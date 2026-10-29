package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.povreplay.ReplayMode;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

/** リプレイの再生中は、プレイヤーの動き(物理・入力)を計算しない。位置と向きは、記録した値を使う。 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V"))
	private void povreplay$noPhysics(LocalPlayer player, Operation<Void> original) {
		if (!ReplayMode.active()) {
			original.call(player);
		}
	}
}
