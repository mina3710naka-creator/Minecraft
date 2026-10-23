package com.lampvideo.display;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * ディスプレイの状態。サーバーが持つのは「ファイル名・モード・再生中か・始めた時刻」だけ。
 * 動画のデコードと音声は、各クライアントが自分でやる(サーバー負荷を増やさない)。
 */
public class DisplayBlockEntity extends BlockEntity {

	/** クライアント側で、毎 tick 呼ばれる。クライアントの初期化で設定する(サーバーにはクライアントのクラスがないので)。 */
	public static volatile Consumer<DisplayBlockEntity> CLIENT_TICK = be -> { };
	/** クライアント側で、ブロックが消えた(壊された・チャンクが解放された)とき。再生を止める。 */
	public static volatile Consumer<DisplayBlockEntity> CLIENT_REMOVED = be -> { };
	/** クライアント側で、操作画面を開く。 */
	public static volatile Consumer<BlockPos> OPEN_GUI = pos -> { };

	/** 再生するファイル名(クライアントの lampvideo/media フォルダの中)。空なら何も再生しない。 */
	public String media = "";
	/** 0=カラー 1=モノクロ(緑) 2=モノクロ(アンバー) 3=モノクロ(グレー) 4=ランプ風 */
	public int mode;
	public boolean playing;
	/** 再生ごとに増える番号。クライアントは、これが変わったら最初から(または途中から)再生し直す。 */
	public int generation;
	/** 再生を始めた時刻(その端末の System.currentTimeMillis 基準)。サーバーでは本当の開始時刻。クライアントでは、受け取った経過時間から逆算した値。 */
	public long startMs;

	// クライアントだけが使う
	public Object clientSession;
	public long lastClientTickMs;

	public DisplayBlockEntity(BlockPos pos, BlockState state) {
		super(ModDisplay.DISPLAY_ENTITY, pos, state);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null && level.isClientSide()) {
			CLIENT_REMOVED.accept(this);
		}
	}

	public void play(String media, int mode) {
		this.media = media;
		this.mode = mode;
		this.playing = true;
		this.generation++;
		this.startMs = System.currentTimeMillis();
		sync();
	}

	public void stop() {
		this.playing = false;
		this.generation++;
		sync();
	}

	public void setMode(int mode) {
		this.mode = mode;
		sync();
	}

	private void sync() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		out.putString("media", media);
		out.putInt("mode", mode);
		out.putBoolean("playing", playing);
		out.putInt("generation", generation);
		out.putLong("elapsed", playing ? Math.max(0, System.currentTimeMillis() - startMs) : 0);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		media = in.getStringOr("media", "");
		mode = in.getIntOr("mode", 0);
		playing = in.getBooleanOr("playing", false);
		generation = in.getIntOr("generation", 0);
		startMs = System.currentTimeMillis() - in.getLongOr("elapsed", 0L);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
