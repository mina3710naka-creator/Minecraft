package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;

import com.magicmod.client.WeaponMotion;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;

/** 三人称(自分と他のプレイヤー)の腕の振りも、物理武器ごとの動きにする。 */
@Mixin(HumanoidModel.class)
public abstract class WeaponArmMixin {

	@Shadow
	public ModelPart body;
	@Shadow
	public ModelPart rightArm;
	@Shadow
	public ModelPart leftArm;

	@Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true, require = 0)
	private void magicmod$arm(HumanoidRenderState state, CallbackInfo ci) {
		if (state.currentSwing == null || state.swingAnimation <= 0.0F) {
			return;
		}
		HumanoidArm swingArm = state.currentSwing.hand().asArm(state.mainArm);
		float[] d = WeaponMotion.thirdPerson(state.getMainHandItemStack(), state.swingAnimation, swingArm);
		if (d == null) {
			return;
		}
		ModelPart arm = swingArm == HumanoidArm.LEFT ? this.leftArm : this.rightArm;
		this.body.yRot = d[3];
		arm.xRot += d[0] - 0.3F;
		arm.yRot += d[1] + this.body.yRot;
		arm.zRot += d[2];
		ci.cancel();
	}
}
