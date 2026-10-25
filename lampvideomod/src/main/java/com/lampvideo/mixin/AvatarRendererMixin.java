package com.lampvideo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lampvideo.client.HeadphoneVisual;
import com.lampvideo.pc.ModPc;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * プレイヤーを描くときの「頭の装備」に、ヘッドホンの絵を差し込む(専用スロットのヘッドホン。兜を着けていれば、兜を優先)。
 * 標準の兜の描画をそのまま使うので、頭に重なって見える。
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("RETURN"), require = 0)
	private void lampvideo$headphones(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		try {
			if (state.headEquipment.isEmpty() && entity instanceof Player player && ModPc.isWearingHeadphones(player)) {
				state.headEquipment = HeadphoneVisual.stack();
			}
		} catch (Throwable t) {
			// 見た目だけなので、失敗しても、ゲームは続ける
		}
	}
}
