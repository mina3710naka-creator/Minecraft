package com.arcanemagic.component;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/**
 * 杖(Wand)アイテムに付与される魔法1つと強化レベルを保持するデータコンポーネント。
 */
public record WandData(Optional<Identifier> spellId, int level) {

	public static final int MAX_LEVEL = 5;

	public static final WandData EMPTY = new WandData(Optional.empty(), 1);

	public static final Codec<WandData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.optionalFieldOf("spell_id").forGetter(WandData::spellId),
			Codec.INT.fieldOf("level").forGetter(WandData::level)
	).apply(instance, WandData::new));

	public static final PacketCodec<RegistryByteBuf, WandData> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.optional(Identifier.PACKET_CODEC), WandData::spellId,
			PacketCodecs.VAR_INT, WandData::level,
			WandData::new
	);

	public boolean hasSpell() {
		return spellId.isPresent();
	}

	public WandData withSpell(Identifier newSpellId) {
		return new WandData(Optional.of(newSpellId), this.level);
	}

	public WandData withLevel(int newLevel) {
		return new WandData(this.spellId, Math.min(MAX_LEVEL, Math.max(1, newLevel)));
	}

	public boolean isMaxLevel() {
		return level >= MAX_LEVEL;
	}
}
