package com.arcanemagic.block;

import com.arcanemagic.ArcaneMagic;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {

	public static final Block ARCANE_ALTAR = register("arcane_altar", ArcaneAltarBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_PURPLE)
					.strength(4.0F, 6.0F)
					.sound(SoundType.AMETHYST_CLUSTER)
					.lightLevel(state -> 5)
					.noOcclusion());

	private ModBlocks() {
	}

	@FunctionalInterface
	private interface BlockFactory {
		Block create(BlockBehaviour.Properties properties);
	}

	private static Block register(String path, BlockFactory factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.of(ArcaneMagic.MOD_ID, path));
		Block block = factory.create(properties.setId(key));
		Registry.register(BuiltInRegistries.BLOCK, key, block);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.of(ArcaneMagic.MOD_ID, path));
		Item blockItem = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);

		return block;
	}

	public static void init() {
	}
}
