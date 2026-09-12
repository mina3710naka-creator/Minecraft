package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 前方の扇形範囲にいる敵を吹き飛ばし、軽いダメージを与える。 */
public class PushSpell implements Spell {

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "push");
	}

	@Override
	public Component displayName() {
		return Component.translatable("spell.arcanemagic.push");
	}

	@Override
	public int baseCooldownTicks() {
		return 50;
	}

	@Override
	public void cast(ServerLevel serverLevel, Player caster, int spellLevel) {
		double radius = 4.0 + spellLevel * 0.5;
		Vec3 origin = caster.getEyePosition();
		Vec3 look = caster.getViewVector(1.0F).normalize();
		AABB area = caster.getBoundingBox().inflate(radius);

		for (Entity entity : serverLevel.getEntities(caster, area, e -> e instanceof LivingEntity && e.isAlive())) {
			Vec3 toEntity = entity.position().subtract(caster.position());
			double distanceSq = toEntity.lengthSqr();
			if (distanceSq < 0.0001 || distanceSq > radius * radius) {
				continue;
			}

			double facing = look.dot(toEntity.normalize());
			if (facing < 0.3) {
				continue;
			}

			Vec3 push = toEntity.normalize().scale(1.2 + spellLevel * 0.15).add(0, 0.35, 0);
			entity.push(push.x, push.y, push.z);
			entity.hurtMarked = true;
			((LivingEntity) entity).hurtServer(serverLevel, serverLevel.damageSources().magic(), 1.0F + spellLevel * 0.5F);
		}

		serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, origin.x + look.x, origin.y + look.y, origin.z + look.z, 3, 0.2, 0.2, 0.2, 0.0);
		serverLevel.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
	}
}
