package com.healingblock.block;

import com.healingblock.HealingBlockMod;

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

	public static final Block HEALING_ALTAR = register("healing_altar", HealingAltarBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.QUARTZ)
					.strength(3.0F, 6.0F)
					.sound(SoundType.AMETHYST_CLUSTER)
					.lightLevel(state -> 7)
					.noOcclusion());

	private ModBlocks() {
	}

	@FunctionalInterface
	private interface BlockFactory {
		Block create(BlockBehaviour.Properties properties);
	}

	private static Block register(String path, BlockFactory factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(HealingBlockMod.MOD_ID, path));
		Block block = factory.create(properties.setId(key));
		Registry.register(BuiltInRegistries.BLOCK, key, block);

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(HealingBlockMod.MOD_ID, path));
		Item blockItem = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);

		return block;
	}

	public static void init() {
	}
}
