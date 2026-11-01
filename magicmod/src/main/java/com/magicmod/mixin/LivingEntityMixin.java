package com.magicmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.magicmod.Combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** 被ダメージの補正(ボスの HP スケール・刻印・アクアシールドなど)を Combat.modifyIncoming で一か所にまとめる。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float magicmod$modifyDamage(float damage, ServerLevel level, DamageSource source) {
		return Combat.modifyIncoming((LivingEntity) (Object) this, source, damage);
	}
}
