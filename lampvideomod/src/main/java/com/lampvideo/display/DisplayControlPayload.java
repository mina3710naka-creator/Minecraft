package com.lampvideo.display;

import com.lampvideo.LampVideoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** クライアント→サーバー: ディスプレイの操作(再生 / 停止 / モード変更)。 */
public record DisplayControlPayload(BlockPos pos, String media, int mode, int action) implements CustomPacketPayload {

	public static final int PLAY = 0;
	public static final int STOP = 1;
	public static final int MODE = 2;

	public static final CustomPacketPayload.Type<DisplayControlPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "display_control"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DisplayControlPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, DisplayControlPayload::pos,
			ByteBufCodecs.STRING_UTF8, DisplayControlPayload::media,
			ByteBufCodecs.VAR_INT, DisplayControlPayload::mode,
			ByteBufCodecs.VAR_INT, DisplayControlPayload::action,
			DisplayControlPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
