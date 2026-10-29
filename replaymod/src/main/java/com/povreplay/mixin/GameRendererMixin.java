package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.povreplay.ReplayEngine;

import net.minecraft.client.renderer.GameRenderer;

/** ワールドを描き終えて、画面の表示(GUI)を描く直前。リプレイの書き出し中は、ここで 1 フレームの絵を取る。 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

	@Shadow
	public abstract RenderTarget mainRenderTarget();

	/** リプレイの書き出し中は、画面(ウィンドウ)の大きさに関係なく、設定した大きさ(標準 1920x1080)で描く。 */
	@Inject(method = "extractWindow", at = @At("TAIL"))
	private void povreplay$renderSize(CallbackInfo ci) {
		if (com.povreplay.ReplayMode.active() && com.povreplay.ReplayEngine.forcesRenderSize()) {
			var state = ((GameRendererAccessor) this).povreplay$gameRenderState().windowRenderState;
			state.width = com.povreplay.RecConfig.width;
			state.height = com.povreplay.RecConfig.height;
		}
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
	private void povreplay$beforeGui(CallbackInfo ci) {
		try {
			ReplayEngine.onFrame(mainRenderTarget());
		} catch (Throwable t) {
			com.povreplay.PovReplay.LOGGER.error("フレームの取得で失敗しました", t);
		}
	}
}
