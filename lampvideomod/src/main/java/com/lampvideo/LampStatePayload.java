package com.lampvideo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * サーバー→クライアント: 動画を再生中かどうかと、画面を含む範囲(ブロック座標)。
 * クライアントは、再生中だけ、その範囲の区画の同期更新を広げる。
 */
public record LampStatePayload(boolean playing, int minX, int minY, int minZ, int maxX, int maxY, int maxZ)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<LampStatePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LampStatePayload> STREAM_CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.playing);
				buf.writeInt(p.minX);
				buf.writeInt(p.minY);
				buf.writeInt(p.minZ);
				buf.writeInt(p.maxX);
				buf.writeInt(p.maxY);
				buf.writeInt(p.maxZ);
			},
			buf -> new LampStatePayload(buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
					buf.readInt(), buf.readInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
