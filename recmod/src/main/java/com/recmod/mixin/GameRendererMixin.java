package com.recmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.recmod.Recorder;

import net.minecraft.client.renderer.GameRenderer;

/**
 * 描画の流れ: ワールド(と手)を描き終える → ホットバーなどの画面(GUI)を描く。
 * その境目(GUI を描く直前)で、ワールドだけの絵を録画用に読み出す。
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Shadow
	public abstract RenderTarget mainRenderTarget();

	@Inject(method = "render",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void recmod$captureBeforeGui(CallbackInfo ci) {
		try {
			Recorder.onFrame(mainRenderTarget());
		} catch (Throwable t) {
			Recorder.LOGGER.error("録画のフレーム取得で失敗しました", t);
		}
	}
}
