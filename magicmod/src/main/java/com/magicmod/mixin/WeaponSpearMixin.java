package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.magicmod.client.WeaponMotion;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.world.entity.HumanoidArm;

/** 長柄武器(突きの振り)も、武器の連携の段ごとの動きに置き換える。 */
@Mixin(SpearAnimations.class)
public abstract class WeaponSpearMixin {

	@Inject(method = "firstPersonAttack", at = @At("HEAD"), cancellable = true, require = 0)
	private static void magicmod$stab(float attack, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
		var p = Minecraft.getInstance().player;
		if (p != null && WeaponMotion.firstPerson(poseStack, invert, attack, p.getMainHandItem())) {
			ci.cancel();
		}
	}
}
