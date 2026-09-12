package com.arcanemagic;

import com.arcanemagic.item.SpellbookItem;
import com.arcanemagic.network.CycleSpellPayload;
import com.arcanemagic.screen.ArcaneAltarScreen;
import com.arcanemagic.screen.ModMenus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class ArcaneMagicClient implements ClientModInitializer {

	private static KeyMapping cycleForwardKey;
	private static KeyMapping cycleBackwardKey;

	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.ARCANE_ALTAR, ArcaneAltarScreen::new);

		// Ctrl+ホイール(MouseMixin)が使えない環境でも同じ操作ができるよう、
		// 通常のキーバインドでも魔導書の魔法を切り替えられるようにしておく。
		cycleForwardKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.arcanemagic.cycle_spell_forward", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_PERIOD, "key.category.arcanemagic"));
		cycleBackwardKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.arcanemagic.cycle_spell_backward", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_COMMA, "key.category.arcanemagic"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}

			ItemStack main = client.player.getMainHandItem();
			ItemStack off = client.player.getOffhandItem();
			boolean holdingSpellbook = main.getItem() instanceof SpellbookItem || off.getItem() instanceof SpellbookItem;

			while (cycleForwardKey.consumeClick()) {
				if (holdingSpellbook) {
					ClientPlayNetworking.send(new CycleSpellPayload(1));
				}
			}
			while (cycleBackwardKey.consumeClick()) {
				if (holdingSpellbook) {
					ClientPlayNetworking.send(new CycleSpellPayload(-1));
				}
			}
		});
	}
}
