package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import com.povreplay.RecConfig;
import com.povreplay.ReplayEngine;
import com.povreplay.ReplayMode;

import net.minecraft.client.Minecraft;

/**
 * ゲームは、描いた絵を、ウィンドウへ「そのままの大きさで」写す(大きい絵なら、左下の一部だけが見える)。
 * リプレイの書き出し中は、絵がウィンドウより大きいので、ウィンドウいっぱいに引き伸ばして写す
 * (見た目と、画面の表示(GUI)・マウスの位置が、ずれないように)。動画にする絵そのものは、変わらない。
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public abstract class PresentMixin {

	@WrapOperation(method = "presentTexture",
			at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/DirectStateAccess;blitFrameBuffers(IIIIIIIIIIII)V"))
	private void povreplay$stretch(DirectStateAccess access, int source, int dest, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0,
			int dstY0, int dstX1, int dstY1, int mask, int filter, Operation<Void> original) {
		if (ReplayMode.active() && ReplayEngine.forcesRenderSize()) {
			var window = Minecraft.getInstance().getWindow();
			original.call(access, source, dest, 0, 0, RecConfig.width, RecConfig.height, 0, 0, window.getWidth(), window.getHeight(), mask, 9729);
		} else {
			original.call(access, source, dest, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
		}
	}
}
