package com.arcanemagic.component;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * SpellbookData を ItemStack の CustomData(NBT)へ読み書きするヘルパー。
 * NBTのリスト型を使わず、区切り文字で連結した1つの文字列として保存することで、
 * バージョンによって変わりやすいリストNBTのAPIへの依存を避けている。
 */
public final class SpellbookDataHelper {

	private static final String SPELLS_KEY = "ArcaneMagicSpells";
	private static final String SELECTED_KEY = "ArcaneMagicSelected";
	private static final String SEPARATOR = ";";

	private SpellbookDataHelper() {
	}

	public static SpellbookData get(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = data.copyTag();

		List<Identifier> spells = new ArrayList<>();
		String joined = tag.getString(SPELLS_KEY);
		if (!joined.isEmpty()) {
			for (String part : joined.split(SEPARATOR)) {
				if (!part.isEmpty()) {
					spells.add(Identifier.parse(part));
				}
			}
		}
		int selected = tag.getInt(SELECTED_KEY);
		return new SpellbookData(List.copyOf(spells), selected);
	}

	public static void set(ItemStack stack, SpellbookData data) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			StringBuilder joined = new StringBuilder();
			for (Identifier id : data.spells()) {
				if (joined.length() > 0) {
					joined.append(SEPARATOR);
				}
				joined.append(id);
			}
			tag.putString(SPELLS_KEY, joined.toString());
			tag.putInt(SELECTED_KEY, data.selected());
		});
	}
}
