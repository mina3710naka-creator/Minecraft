package com.lampvideo.pc;

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

/** スピーカーの設定: 音量(0〜100)と名前。管理パネルから変える。クライアントも、音量を読んで、音の大きさを決める。 */
public class SpeakerBlockEntity extends BlockEntity {

	public int volume = 100;
	public String label = "";

	public SpeakerBlockEntity(BlockPos pos, BlockState state) {
		super(ModPc.SPEAKER_ENTITY, pos, state);
	}

	public void setSettings(String label, int volume) {
		this.label = label.length() > 24 ? label.substring(0, 24) : label;
		this.volume = Math.max(0, Math.min(100, volume));
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		out.putInt("volume", volume);
		out.putString("label", label);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		volume = in.getIntOr("volume", 100);
		label = in.getStringOr("label", "");
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
