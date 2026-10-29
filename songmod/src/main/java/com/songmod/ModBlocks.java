package com.songmod;

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

public final class ModBlocks {

	public static final Block SONG_SELECTOR = registerBlock("song_selector");
	public static final Item SONG_SELECTOR_ITEM = registerItem("song_selector", SONG_SELECTOR);

	private ModBlocks() {
	}

	private static Block registerBlock(String path) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
				Identifier.fromNamespaceAndPath(SongMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.BLOCK, key,
				new SongSelectorBlock(BlockBehaviour.Properties.of().setId(key).strength(1.5F).sound(SoundType.WOOD)));
	}

	private static Item registerItem(String path, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
				Identifier.fromNamespaceAndPath(SongMod.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** クラスを読み込んで登録を走らせる。 */
	public static void init() {
	}
}
