package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.magicmod.Forms;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** 変身中の移動能力: 壁をのぼれる(はしごと同じ扱い)・すり抜け(ブロックの当たり判定を無視)。両側(サーバーとクライアント)で動く。 */
@Mixin(LivingEntity.class)
public abstract class FormMovementMixin {

	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true, require = 0)
	private void magicmod$climb(CallbackInfoReturnable<Boolean> cir) {
		if (Forms.climbs((LivingEntity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}
}
