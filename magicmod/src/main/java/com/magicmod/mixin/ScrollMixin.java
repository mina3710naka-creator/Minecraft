package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.magicmod.client.MagicClient;

import net.minecraft.client.MouseHandler;

/** [H] を押しながらホイールで、召喚する使い魔を選ぶ(ホットバーのスクロールは止める)。 */
@Mixin(MouseHandler.class)
public abstract class ScrollMixin {

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
	private void magicmod$familiarScroll(long handle, double xo, double yo, CallbackInfo ci) {
		if (MagicClient.familiarScroll(yo)) {
			ci.cancel();
		}
	}
}
