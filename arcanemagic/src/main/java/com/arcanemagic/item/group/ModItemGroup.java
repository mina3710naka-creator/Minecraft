package com.arcanemagic.item.group;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.block.ModBlocks;
import com.arcanemagic.item.ModItems;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModItemGroup {

	public static final RegistryKey<ItemGroup> ARCANE_MAGIC = RegistryKey.of(RegistryKeys.ITEM_GROUP,
			Identifier.of(ArcaneMagic.MOD_ID, "arcanemagic"));

	private ModItemGroup() {
	}

	public static void init() {
		Registry.register(Registries.ITEM_GROUP, ARCANE_MAGIC, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.WAND))
				.displayName(Text.translatable("itemGroup.arcanemagic"))
				.entries((displayContext, entries) -> {
					entries.add(ModItems.WAND);
					entries.add(ModItems.SPELLBOOK);
					entries.add(ModItems.ARCANE_CRYSTAL);
					entries.add(ModItems.FIREBALL_SCROLL);
					entries.add(ModItems.ICE_SHARD_SCROLL);
					entries.add(ModItems.HEAL_SCROLL);
					entries.add(ModItems.LIGHTNING_SCROLL);
					entries.add(ModItems.BLINK_SCROLL);
					entries.add(ModItems.PUSH_SCROLL);
					entries.add(ModBlocks.ARCANE_ALTAR);
				})
				.build());
	}
}
