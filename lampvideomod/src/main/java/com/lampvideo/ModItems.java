package com.lampvideo;

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

	public static final Item REMOTE = register("remote", LampVideoRemoteItem::new);

	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, "lampvideo"));

	private ModItems() {
	}

	private static Item register(String path, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
				Identifier.fromNamespaceAndPath(LampVideoMod.MOD_ID, path));
		Item item = factory.apply(new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(REMOTE))
				.title(Component.translatable("itemGroup.lampvideo"))
				.displayItems((context, output) -> {
					output.accept(REMOTE);
					output.accept(com.lampvideo.display.ModDisplay.DISPLAY_ITEM);
					output.accept(com.lampvideo.display.ModDisplay.LARGE_DISPLAY_ITEM);
					output.accept(com.lampvideo.display.ModDisplay.STAND_DISPLAY_ITEM);
					output.accept(com.lampvideo.display.ModDisplay.PROJECTOR_ITEM);
					output.accept(com.lampvideo.pc.ModPc.PC_ITEM);
					output.accept(com.lampvideo.pc.ModPc.SPEAKER_ITEM);
					output.accept(com.lampvideo.pc.ModPc.HEADPHONES);
					output.accept(com.lampvideo.pc.ModPc.KEYBOARD_ITEM);
					output.accept(com.lampvideo.pc.ModPc.PANEL_ITEM);
				})
				.build());
	}
}
