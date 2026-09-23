package com.healingblock.item.group;

import com.healingblock.HealingBlockMod;
import com.healingblock.block.ModBlocks;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModItemGroup {

	public static final ResourceKey<CreativeModeTab> HEALING_BLOCK = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(HealingBlockMod.MOD_ID, "healingblock"));

	private ModItemGroup() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, HEALING_BLOCK, FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(ModBlocks.HEALING_ALTAR))
				.title(Component.translatable("itemGroup.healingblock"))
				.displayItems((context, output) -> {
					output.accept(ModBlocks.HEALING_ALTAR);
				})
				.build());
	}
}
