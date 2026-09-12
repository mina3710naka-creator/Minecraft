package com.arcanemagic.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.Identifier;

/**
 * 魔導書(Spellbook)アイテムに付与される最大6個の魔法リストと、選択中スロットを保持する。
 * 実際の保存先は ItemStack の CustomData(NBT)コンポーネント(SpellbookDataHelper参照)。
 */
public record SpellbookData(List<Identifier> spells, int selected) {

	public static final int SLOT_COUNT = 6;

	public static final SpellbookData EMPTY = new SpellbookData(List.of(), 0);

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
