package com.lampvideo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** サーバー→クライアント: 動画を再生中かどうか。クライアントは、再生中だけ、チャンクの同期更新の範囲を広げる。 */
public record LampStatePayload(boolean playing) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<LampStatePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LampStatePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, LampStatePayload::playing,
			LampStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
