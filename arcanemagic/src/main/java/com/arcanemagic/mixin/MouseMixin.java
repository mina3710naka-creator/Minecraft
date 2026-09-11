package com.arcanemagic.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.arcanemagic.item.SpellbookItem;
import com.arcanemagic.network.CycleSpellPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;

/**
 * Ctrl を押しながらマウスホイールを回した時だけ、魔導書の選択中の魔法を切り替える。
 * バニラのマウスホイール入力を捕まえる公開APIが無いため Mixin で割り込んでいる。
 * (この Mixin がお使いの環境でビルドできない場合、削除しても
 *  ArcaneMagicClient に登録したキーバインドだけで同じ機能を使えます)
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void arcanemagic$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || vertical == 0 || !Screen.hasControlDown()) {
			return;
		}

		ItemStack main = client.player.getMainHandStack();
		ItemStack off = client.player.getOffHandStack();
		boolean holdingSpellbook = main.getItem() instanceof SpellbookItem || off.getItem() instanceof SpellbookItem;
		if (!holdingSpellbook) {
			return;
		}

		int direction = vertical > 0 ? 1 : -1;
		if (ClientPlayNetworking.canSend(CycleSpellPayload.ID)) {
			ClientPlayNetworking.send(new CycleSpellPayload(direction));
		}
		ci.cancel();
	}
}
