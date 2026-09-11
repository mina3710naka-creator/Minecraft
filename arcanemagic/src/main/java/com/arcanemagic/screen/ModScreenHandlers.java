package com.arcanemagic.screen;

import com.arcanemagic.ArcaneMagic;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {

	public static final ScreenHandlerType<ArcaneAltarScreenHandler> ARCANE_ALTAR = Registry.register(
			Registries.SCREEN_HANDLER,
			Identifier.of(ArcaneMagic.MOD_ID, "arcane_altar"),
			new ScreenHandlerType<>(ArcaneAltarScreenHandler::new));

	private ModScreenHandlers() {
	}

	public static void init() {
	}
}
