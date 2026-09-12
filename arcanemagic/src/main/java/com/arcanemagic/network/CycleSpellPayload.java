package com.arcanemagic.network;

import com.arcanemagic.ArcaneMagic;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** クライアント→サーバー: 魔導書の選択中の魔法を +1/-1 だけ進める/戻すリクエスト。 */
public record CycleSpellPayload(int direction) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CycleSpellPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.of(ArcaneMagic.MOD_ID, "cycle_spell"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CycleSpellPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CycleSpellPayload::direction,
			CycleSpellPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
