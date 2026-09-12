package com.arcanemagic.item.group;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.block.ModBlocks;
import com.arcanemagic.item.ModItems;

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

	public static final ResourceKey<CreativeModeTab> ARCANE_MAGIC = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			Identifier.of(ArcaneMagic.MOD_ID, "arcanemagic"));

	private ModItemGroup() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ARCANE_MAGIC, FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(ModItems.WAND))
				.title(Component.translatable("itemGroup.arcanemagic"))
				.displayItems((context, output) -> {
					output.accept(ModItems.WAND);
					output.accept(ModItems.SPELLBOOK);
					output.accept(ModItems.ARCANE_CRYSTAL);
					output.accept(ModItems.FIREBALL_SCROLL);
					output.accept(ModItems.ICE_SHARD_SCROLL);
					output.accept(ModItems.HEAL_SCROLL);
					output.accept(ModItems.LIGHTNING_SCROLL);
					output.accept(ModItems.BLINK_SCROLL);
					output.accept(ModItems.PUSH_SCROLL);
					output.accept(ModBlocks.ARCANE_ALTAR);
				})
				.build());
	}
}
