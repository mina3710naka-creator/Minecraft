package com.songmod;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {

	public static final Item FOLLOWER = register("follower", SongFollowerItem::new);
	public static final Item REST = register("rest", SongRestItem::new);

	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(SongMod.MOD_ID, "songmod"));

	private ModItems() {
	}

	private static Item register(String path, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
				Identifier.fromNamespaceAndPath(SongMod.MOD_ID, path));
		Item item = factory.apply(new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(FOLLOWER))
				.title(Component.translatable("itemGroup.songmod"))
				.displayItems((context, output) -> {
					output.accept(FOLLOWER);
					output.accept(REST);
					output.accept(ModBlocks.SONG_SELECTOR_ITEM);
				})
				.build());
	}
}
