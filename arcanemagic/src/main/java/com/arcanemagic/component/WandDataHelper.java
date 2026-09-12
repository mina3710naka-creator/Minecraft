package com.arcanemagic.component;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * WandData を ItemStack の CustomData(汎用NBT)コンポーネントへ読み書きするヘルパー。
 * 独自の DataComponentType を新規登録せず、バニラの custom_data に間借りすることで
 * Codec/StreamCodec を自作する必要をなくしている。
 */
public final class WandDataHelper {

	private static final String SPELL_KEY = "ArcaneMagicSpell";
	private static final String LEVEL_KEY = "ArcaneMagicLevel";

	private WandDataHelper() {
	}

	public static WandData get(ItemStack stack) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = data.copyTag();

		int level = tag.contains(LEVEL_KEY) ? tag.getInt(LEVEL_KEY) : 1;
		if (!tag.contains(SPELL_KEY)) {
			return new WandData(Optional.empty(), Math.max(1, level));
		}
		Identifier spellId = Identifier.parse(tag.getString(SPELL_KEY));
		return new WandData(Optional.of(spellId), Math.max(1, level));
	}

	public static void set(ItemStack stack, WandData data) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.remove(SPELL_KEY);
			data.spellId().ifPresent(id -> tag.putString(SPELL_KEY, id.toString()));
			tag.putInt(LEVEL_KEY, data.level());
		});
	}
}
