package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.povreplay.FrameSample;
import com.povreplay.ReplayEngine;
import com.povreplay.ReplayMode;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** リプレイの再生中は、視点の位置・向き・視野角・歩くときの揺れを、記録した値にする。 */
@Mixin(Camera.class)
public abstract class CameraMixin {

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void povreplay$align(float partialTicks, CallbackInfo ci) {
		FrameSample s = ReplayMode.active() ? ReplayEngine.currentSample() : null;
		if (s != null) {
			this.setRotation(s.camYRot, s.camXRot);
			this.setPosition(s.camX, s.camY, s.camZ);
		}
	}

	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateFov(F)F"))
	private float povreplay$fov(Camera camera, float partialTicks, Operation<Float> original) {
		FrameSample s = ReplayMode.active() ? ReplayEngine.currentSample() : null;
		return s != null ? s.fov : original.call(camera, partialTicks);
	}

	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateHudFov(F)F"))
	private float povreplay$hudFov(Camera camera, float partialTicks, Operation<Float> original) {
		FrameSample s = ReplayMode.active() ? ReplayEngine.currentSample() : null;
		return s != null ? s.hudFov : original.call(camera, partialTicks);
	}

	/** カメラの投影(縦横の比)も、リプレイの書き出し中は、設定した大きさで計算する。 */
	@WrapOperation(method = { "update", "createProjectionMatrixForCulling" },
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
	private int povreplay$width(com.mojang.blaze3d.platform.Window window, Operation<Integer> original) {
		return ReplayMode.active() && ReplayEngine.forcesRenderSize() ? com.povreplay.RecConfig.width : original.call(window);
	}

	@WrapOperation(method = { "update", "createProjectionMatrixForCulling" },
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
	private int povreplay$height(com.mojang.blaze3d.platform.Window window, Operation<Integer> original) {
		return ReplayMode.active() && ReplayEngine.forcesRenderSize() ? com.povreplay.RecConfig.height : original.call(window);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void povreplay$bob(CameraRenderState cameraState, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
		FrameSample s = ReplayMode.active() ? ReplayEngine.currentSample() : null;
		if (s != null && cameraState.entityRenderState.isPlayer) {
			cameraState.entityRenderState.bob = s.bob;
			cameraState.entityRenderState.backwardsInterpolatedWalkDistance = s.walkDistBack;
		}
	}
}
