package com.arcanemagic.screen;

import com.arcanemagic.ArcaneMagic;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class ModMenus {

	public static final MenuType<ArcaneAltarMenu> ARCANE_ALTAR = Registry.register(
			BuiltInRegistries.MENU,
			Identifier.of(ArcaneMagic.MOD_ID, "arcane_altar"),
			new MenuType<>(ArcaneAltarMenu::new, FeatureFlags.VANILLA_SET));

	private ModMenus() {
	}

	public static void init() {
	}
}
