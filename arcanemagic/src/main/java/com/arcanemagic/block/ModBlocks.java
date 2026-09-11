package com.arcanemagic.block;

import com.arcanemagic.ArcaneMagic;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {

	public static final Block ARCANE_ALTAR = register("arcane_altar", ArcaneAltarBlock::new,
			AbstractBlock.Settings.create()
					.mapColor(MapColor.PURPLE)
					.strength(4.0F, 6.0F)
					.sounds(BlockSoundGroup.AMETHYST_CLUSTER)
					.luminance(state -> 5)
					.nonOpaque());

	private ModBlocks() {
	}

	@FunctionalInterface
	private interface BlockFactory {
		Block create(AbstractBlock.Settings settings);
	}

	private static Block register(String path, BlockFactory factory, AbstractBlock.Settings settings) {
		RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(ArcaneMagic.MOD_ID, path));
		Block block = factory.create(settings.registryKey(key));
		Registry.register(Registries.BLOCK, key, block);

		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(ArcaneMagic.MOD_ID, path));
		Item blockItem = new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey());
		Registry.register(Registries.ITEM, itemKey, blockItem);

		return block;
	}

	public static void init() {
	}
}
