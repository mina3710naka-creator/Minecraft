package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.magicmod.client.WeaponMotion;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;

/** 物理武器を持っているときの一人称の振りを、武器の種類と連携の段ごとの動きに置き換える。 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class WeaponMotionMixin {

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true, require = 0)
	private void magicmod$swing(float animation, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
		var p = Minecraft.getInstance().player;
		if (p != null && WeaponMotion.firstPerson(poseStack, invert, animation, p.getMainHandItem())) {
			ci.cancel();
		}
	}
}
