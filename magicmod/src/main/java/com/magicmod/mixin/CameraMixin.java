package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.magicmod.client.ClientFx;

import net.minecraft.client.Camera;

/** 大魔法・ボスの演出で画面を揺らす。見つからなくても落ちないように require = 0。 */
@Mixin(Camera.class)
public abstract class CameraMixin {

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract float yRot();

	@Shadow
	public abstract float xRot();

	@Inject(method = "alignWithEntity", at = @At("TAIL"), require = 0)
	private void magicmod$shake(float partialTicks, CallbackInfo ci) {
		float[] o = ClientFx.shakeOffset();
		if (o != null) {
			setRotation(yRot() + o[0], xRot() + o[1]);
		}
	}
}
