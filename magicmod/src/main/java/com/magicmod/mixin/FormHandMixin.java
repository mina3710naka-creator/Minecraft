package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.magicmod.Forms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;

/** 変身中は、一人称の手も描かない(変身体の腕は別にあるため)。 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FormHandMixin {

	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true, require = 0)
	private void magicmod$hands(CallbackInfo ci) {
		if (Forms.active(Minecraft.getInstance().player)) {
			ci.cancel();
		}
	}
}
