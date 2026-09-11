package com.arcanemagic.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;

/**
 * 魔導書(Spellbook)アイテムに付与される最大6個の魔法リストと、選択中スロットを保持する。
 */
public record SpellbookData(List<Identifier> spells, int selected) {

	public static final int SLOT_COUNT = 6;

	public static final SpellbookData EMPTY = new SpellbookData(List.of(), 0);

	public static final Codec<SpellbookData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Identifier.CODEC.listOf(0, SLOT_COUNT).fieldOf("spells").forGetter(SpellbookData::spells),
			Codec.INT.fieldOf("selected").forGetter(SpellbookData::selected)
	).apply(instance, SpellbookData::new));

	public static final PacketCodec<RegistryByteBuf, SpellbookData> PACKET_CODEC = PacketCodec.tuple(
			Identifier.PACKET_CODEC.collect(PacketCodecs.toList()), SpellbookData::spells,
			PacketCodecs.VAR_INT, SpellbookData::selected,
			SpellbookData::new
	);

	public boolean isFull() {
		return spells.size() >= SLOT_COUNT;
	}

	public boolean isEmpty() {
		return spells.isEmpty();
	}

	public SpellbookData withAdded(Identifier spellId) {
		if (isFull()) {
			return this;
		}
		List<Identifier> copy = new ArrayList<>(spells);
		copy.add(spellId);
		return new SpellbookData(List.copyOf(copy), selected);
	}

	public SpellbookData withSelected(int newSelected) {
		if (spells.isEmpty()) {
			return new SpellbookData(spells, 0);
		}
		int size = spells.size();
		int normalized = ((newSelected % size) + size) % size;
		return new SpellbookData(spells, normalized);
	}

	public SpellbookData cycled(int direction) {
		return withSelected(selected + direction);
	}

	public Optional<Identifier> selectedSpell() {
		if (spells.isEmpty() || selected < 0 || selected >= spells.size()) {
			return Optional.empty();
		}
		return Optional.of(spells.get(selected));
	}
}
