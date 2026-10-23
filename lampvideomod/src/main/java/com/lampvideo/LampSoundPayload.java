package com.lampvideo;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * サーバー→クライアント: 動画の音声を、プレイヤーに付いてくる音として鳴らす/止める。
 * sound は "lampvideo:sample_t3" のような音声のID。play=false なら、鳴らしている音を止める。
 */
public record LampSoundPayload(String sound, boolean play) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<LampSoundPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "sound"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LampSoundPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, LampSoundPayload::sound,
			ByteBufCodecs.BOOL, LampSoundPayload::play,
			LampSoundPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
