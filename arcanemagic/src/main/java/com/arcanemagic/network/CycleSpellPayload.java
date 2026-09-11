package com.arcanemagic.network;

import com.arcanemagic.ArcaneMagic;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** クライアント→サーバー: 魔導書の選択中の魔法を +1/-1 だけ進める/戻すリクエスト。 */
public record CycleSpellPayload(int direction) implements CustomPayload {

	public static final CustomPayload.Id<CycleSpellPayload> ID =
			new CustomPayload.Id<>(Identifier.of(ArcaneMagic.MOD_ID, "cycle_spell"));

	public static final PacketCodec<RegistryByteBuf, CycleSpellPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, CycleSpellPayload::direction,
			CycleSpellPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
