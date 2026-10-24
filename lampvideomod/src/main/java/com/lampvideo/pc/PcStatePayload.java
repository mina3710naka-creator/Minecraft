package com.lampvideo.pc;

import com.lampvideo.LampVideoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** サーバー→クライアント: PC の操作画面を開く/更新する。 */
public record PcStatePayload(BlockPos pc, boolean hasPassword, boolean authed, boolean owner, int linked, int state,
		String message) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<PcStatePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "pc_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PcStatePayload> STREAM_CODEC = StreamCodec.of(
			(buf, p) -> {
				BlockPos.STREAM_CODEC.encode(buf, p.pc);
				buf.writeBoolean(p.hasPassword);
				buf.writeBoolean(p.authed);
				buf.writeBoolean(p.owner);
				buf.writeVarInt(p.linked);
				buf.writeVarInt(p.state);
				ByteBufCodecs.STRING_UTF8.encode(buf, p.message);
			},
			buf -> new PcStatePayload(BlockPos.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readBoolean(),
					buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), ByteBufCodecs.STRING_UTF8.decode(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
