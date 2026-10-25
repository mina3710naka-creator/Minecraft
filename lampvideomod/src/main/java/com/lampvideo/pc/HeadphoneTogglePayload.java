package com.lampvideo.pc;

import com.lampvideo.LampVideoMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** クライアント→サーバー: クリエイティブの持ち物画面から、ヘッドホンを着ける/外す(クリエイティブのプレイヤーだけ)。 */
public record HeadphoneTogglePayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<HeadphoneTogglePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "headphone_toggle"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HeadphoneTogglePayload> STREAM_CODEC =
			StreamCodec.unit(new HeadphoneTogglePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
