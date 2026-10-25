package com.lampvideo.client;

import com.lampvideo.LampVideoMod;
import com.lampvideo.pc.ModPc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/** 頭に着けたヘッドホンの見た目。標準の兜の描画に渡す、描画専用のアイテム(兜の欄には入らない本物とは別)。 */
public final class HeadphoneVisual {

	private static final ResourceKey<EquipmentAsset> ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "headphones"));
	private static ItemStack cached;

	private HeadphoneVisual() {
	}

	public static ItemStack stack() {
		if (cached == null) {
			ItemStack s = new ItemStack(ModPc.HEADPHONES);
			s.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setAsset(ASSET).build());
			cached = s;
		}
		return cached;
	}
}
