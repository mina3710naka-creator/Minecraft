package com.arcanemagic;

import com.arcanemagic.item.SpellbookItem;
import com.arcanemagic.network.CycleSpellPayload;
import com.arcanemagic.screen.ArcaneAltarScreen;
import com.arcanemagic.screen.ModScreenHandlers;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;

public class ArcaneMagicClient implements ClientModInitializer {

	private static KeyBinding cycleForwardKey;
	private static KeyBinding cycleBackwardKey;

	@Override
	public void onInitializeClient() {
		HandledScreens.register(ModScreenHandlers.ARCANE_ALTAR, ArcaneAltarScreen::new);

		// Ctrl+ホイール(MouseMixin)が使えない環境でも同じ操作ができるよう、
		// 通常のキーバインドでも魔導書の魔法を切り替えられるようにしておく。
		cycleForwardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.arcanemagic.cycle_spell_forward", InputUtil.Type.KEYSYM,
				InputUtil.GLFW_KEY_PERIOD, "key.category.arcanemagic"));
		cycleBackwardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.arcanemagic.cycle_spell_backward", InputUtil.Type.KEYSYM,
				InputUtil.GLFW_KEY_COMMA, "key.category.arcanemagic"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}

			ItemStack main = client.player.getMainHandStack();
			ItemStack off = client.player.getOffHandStack();
			boolean holdingSpellbook = main.getItem() instanceof SpellbookItem || off.getItem() instanceof SpellbookItem;

			while (cycleForwardKey.wasPressed()) {
				if (holdingSpellbook) {
					ClientPlayNetworking.send(new CycleSpellPayload(1));
				}
			}
			while (cycleBackwardKey.wasPressed()) {
				if (holdingSpellbook) {
					ClientPlayNetworking.send(new CycleSpellPayload(-1));
				}
			}
		});
	}
}
