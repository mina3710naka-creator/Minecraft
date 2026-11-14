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

/**
 * 変身中は、プレイヤー本体のモデルの代わりに、変身した姿のモデルを「プレイヤーの描画として」描く(位置・影・名前表示・視点はプレイヤーのまま)。
 * 姿のデータは、サーバーが持つ「変身体」エンティティのもの(画面側で毎 tick プレイヤーに重ねてあり、自分では描かれない)。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class FormHideMixin {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
	private void magicmod$hide(LivingEntityRenderState state, PoseStack ps, SubmitNodeCollector nodes, CameraRenderState cam, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (!(state instanceof AvatarRenderState a) || mc.level == null
				|| !(mc.level.getEntity(a.id) instanceof net.minecraft.world.entity.LivingEntity le) || !Forms.active(le)) {
			return;
		}
		ci.cancel();
		String want = "avatar:" + le.getUUID();
		for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.Mob m && m.getCustomName() != null && m.getCustomName().getString().equals(want)) {
				var r = (net.minecraft.client.renderer.entity.EntityRenderer) mc.getEntityRenderDispatcher().getRenderer(m);
				var st = r.createRenderState();
				r.extractRenderState(m, st, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
				if (st instanceof LivingEntityRenderState ls) {
					ls.isInvisible = false;
					ls.nameTag = state.nameTag;
					ls.nameTagAttachment = state.nameTagAttachment;
					ls.shadowRadius = 0.5f;
				}
				r.submit(st, ps, nodes, cam);
				return;
			}
		}
	}
}
