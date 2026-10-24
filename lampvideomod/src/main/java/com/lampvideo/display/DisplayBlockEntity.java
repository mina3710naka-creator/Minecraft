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

	public static final int STOPPED = 0;
	public static final int PLAYING = 1;
	public static final int PAUSED = 2;

	/** 再生するファイル名(クライアントの lampvideo/media フォルダの中)。空なら何も再生しない。 */
	public String media = "";
	/** 0=カラー 1=モノクロ(緑) 2=モノクロ(アンバー) 3=モノクロ(グレー) 4=ランプ風 */
	public int mode;
	public int state = STOPPED;
	/** 再生・一時停止・シークのたびに増える番号。クライアントは、これが変わったら、新しい位置から再生し直す。 */
	public int generation;
	/** 再生中: 再生を始めた(とみなす)時刻。経過時間 = いま - startMs。その端末の System.currentTimeMillis 基準。 */
	public long startMs;
	/** 一時停止中: 止めた位置(ミリ秒)。 */
	public long posMs;
	/** PC の管理下にあるか(その場合、直接の操作は受け付けず、PC・キーボードから操作する)。 */
	public boolean managed;
	private boolean loadedOnce;

	// クライアントだけが使う
	public Object clientState;
	/** グループ(連結した大画面)での、このパネルの位置: 列・行、全体の列数・行数、親(左上)の位置、{左,右,上,下}に続く枚数。 */
	public int col;
	public int row;
	public int cols = 1;
	public int rows = 1;
	public BlockPos primary;
	public int[] edges = new int[4];

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

	/** いまの再生位置(ミリ秒)。止まっているなら 0。 */
	public long positionMs() {
		return switch (state) {
			case PLAYING -> Math.max(0, System.currentTimeMillis() - startMs);
			case PAUSED -> posMs;
			default -> 0;
		};
	}

	public void play(String media, int mode) {
		this.media = media;
		this.mode = mode;
		this.state = PLAYING;
		this.startMs = System.currentTimeMillis();
		this.posMs = 0;
		changed(true);
	}

	public void stop() {
		this.state = STOPPED;
		changed(true);
	}

	/** 一時停止 ⇔ 再開。 */
	public void togglePause() {
		if (state == PLAYING) {
			posMs = positionMs();
			state = PAUSED;
		} else if (state == PAUSED) {
			startMs = System.currentTimeMillis() - posMs;
			state = PLAYING;
		} else {
			return;
		}
		changed(true);
	}

	/** 再生位置を deltaMs だけ動かす(進める/戻す)。 */
	public void seek(long deltaMs) {
		if (state == PLAYING) {
			startMs = System.currentTimeMillis() - Math.max(0, positionMs() + deltaMs);
		} else if (state == PAUSED) {
			posMs = Math.max(0, posMs + deltaMs);
		} else {
			return;
		}
		changed(true);
	}

	public void setMode(int mode) {
		this.mode = mode;
		changed(false);
	}

	private void changed(boolean newGeneration) {
		if (newGeneration) {
			generation++;
		}
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		out.putString("media", media);
		out.putInt("mode", mode);
		out.putInt("state", state);
		out.putBoolean("managed", managed);
		out.putInt("generation", generation);
		out.putLong("pos", state == PAUSED ? posMs : 0);
		out.putLong("elapsed", state == PLAYING ? positionMs() : 0);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		media = in.getStringOr("media", "");
		mode = in.getIntOr("mode", 0);
		state = in.getIntOr("state", STOPPED);
		managed = in.getBooleanOr("managed", false);
		int gen = in.getIntOr("generation", 0);
		// 位置は、番号が変わったとき(と、最初の読み込み)だけ受け取る。モード変更などの更新で、位置がずれないように
		if (!loadedOnce || gen != generation) {
			startMs = System.currentTimeMillis() - in.getLongOr("elapsed", 0L);
			posMs = in.getLongOr("pos", 0L);
		}
		generation = gen;
		loadedOnce = true;
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
