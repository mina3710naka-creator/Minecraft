package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.magicmod.Forms;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** 変身中は、プレイヤー本体(防具・持ち物ごと)を描かない。代わりに重なっている「変身体」が見える。 */
@Mixin(LivingEntityRenderer.class)
public abstract class FormHideMixin {

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
	private void magicmod$hide(LivingEntityRenderState state, PoseStack ps, SubmitNodeCollector nodes, CameraRenderState cam, CallbackInfo ci) {
		if (state instanceof AvatarRenderState a && Minecraft.getInstance().level != null
				&& Minecraft.getInstance().level.getEntity(a.id) instanceof net.minecraft.world.entity.LivingEntity le && Forms.active(le)) {
			ci.cancel();
		}
	}
}
