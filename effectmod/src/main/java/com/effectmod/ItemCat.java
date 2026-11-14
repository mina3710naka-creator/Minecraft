package com.effectmod;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 専用効果のためのアイテムの種類。 */
public enum ItemCat {
	SWORD("剣"), AXE("斧"), PICKAXE("ツルハシ"), SHOVEL("シャベル"), HOE("クワ"), BOW("弓"), CROSSBOW("クロスボウ"),
	TRIDENT("トライデント"), MACE("メイス"), SHIELD("盾"), HELMET("兜"), CHESTPLATE("胸当て"), LEGGINGS("レギンス"),
	BOOTS("ブーツ"), ELYTRA("エリトラ"), FISHING_ROD("釣り竿"), SHEARS("ハサミ");

	public final String label;

	ItemCat(String label) {
		this.label = label;
	}

	public boolean matches(ItemStack s) {
		return switch (this) {
			case SWORD -> s.is(ItemTags.SWORDS);
			case AXE -> s.is(ItemTags.AXES);
			case PICKAXE -> s.is(ItemTags.PICKAXES);
			case SHOVEL -> s.is(ItemTags.SHOVELS);
			case HOE -> s.is(ItemTags.HOES);
			case BOW -> s.is(Items.BOW);
			case CROSSBOW -> s.is(Items.CROSSBOW);
			case TRIDENT -> s.is(Items.TRIDENT);
			case MACE -> s.is(Items.MACE);
			case SHIELD -> s.is(Items.SHIELD);
			case HELMET -> s.is(ItemTags.HEAD_ARMOR);
			case CHESTPLATE -> s.is(ItemTags.CHEST_ARMOR);
			case LEGGINGS -> s.is(ItemTags.LEG_ARMOR);
			case BOOTS -> s.is(ItemTags.FOOT_ARMOR);
			case ELYTRA -> s.is(Items.ELYTRA);
			case FISHING_ROD -> s.is(Items.FISHING_ROD);
			case SHEARS -> s.is(Items.SHEARS);
		};
	}
}
