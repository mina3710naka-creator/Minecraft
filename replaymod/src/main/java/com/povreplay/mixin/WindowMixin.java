package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.Window;
import com.povreplay.RecConfig;
import com.povreplay.ReplayEngine;
import com.povreplay.ReplayMode;

/**
 * リプレイの書き出し中は、ワールドを、ウィンドウの大きさと関係なく、設定した大きさ(標準 1920x1080)で描く。
 * 画面の表示(GUI)も、同じ大きさの絵の上に描かれるので、GUI の大きさ(GUI スケール)も、その大きさに合わせる。
 * 描いた絵は、ウィンドウいっぱいに引き伸ばして見せるので、マウスの位置も、そのまま対応する。
 */
@Mixin(Window.class)
public abstract class WindowMixin {

	private static boolean povreplay$forced() {
		return ReplayMode.active() && ReplayEngine.forcesRenderSize();
	}

	/** 1080 のとき 2、4K のとき 4(GUI の高さが、おおよそ 540 になるように)。 */
	private static int povreplay$scale() {
		return Math.max(1, Math.round(RecConfig.height / 540F));
	}

	@Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
	private void povreplay$guiScale(CallbackInfoReturnable<Integer> cir) {
		if (povreplay$forced()) {
			cir.setReturnValue(povreplay$scale());
		}
	}

	@Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
	private void povreplay$guiWidth(CallbackInfoReturnable<Integer> cir) {
		if (povreplay$forced()) {
			cir.setReturnValue((int) Math.ceil(RecConfig.width / (double) povreplay$scale()));
		}
	}

	@Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
	private void povreplay$guiHeight(CallbackInfoReturnable<Integer> cir) {
		if (povreplay$forced()) {
			cir.setReturnValue((int) Math.ceil(RecConfig.height / (double) povreplay$scale()));
		}
	}
}
