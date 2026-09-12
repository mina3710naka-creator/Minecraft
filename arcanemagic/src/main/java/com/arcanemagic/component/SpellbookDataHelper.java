package com.arcanemagic.component;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class SpellbookDataHelper {

	private static final String SPELLS_KEY = "ArcaneMagicSpells";
	private static final String SELECTED_KEY = "ArcaneMagicSelected";

	private SpellbookDataHelper() {
	}

	public static SpellbookData get(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = data.copyTag();

		List<Identifier> spells = new ArrayList<>();
		ListTag list = tag.getList(SPELLS_KEY, Tag.TAG_STRING);
		for (int i = 0; i < list.size(); i++) {
			spells.add(Identifier.parse(list.getString(i)));
		}
		int selected = tag.getInt(SELECTED_KEY);
		return new SpellbookData(List.copyOf(spells), selected);
	}

	public static void set(ItemStack stack, SpellbookData data) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			ListTag list = new ListTag();
			for (Identifier id : data.spells()) {
				list.add(StringTag.valueOf(id.toString()));
			}
			tag.put(SPELLS_KEY, list);
			tag.putInt(SELECTED_KEY, data.selected());
		});
	}
}
