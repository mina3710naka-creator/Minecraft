package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** 前方の扇形範囲にいる敵を吹き飛ばし、軽いダメージを与える。 */
public class PushSpell implements Spell {

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "push");
	}

	@Override
	public Text displayName() {
		return Text.translatable("spell.arcanemagic.push");
	}

	@Override
	public int baseCooldownTicks() {
		return 50;
	}

	@Override
	public void cast(ServerWorld world, PlayerEntity caster, int level) {
		double radius = 4.0 + level * 0.5;
		Vec3d origin = caster.getEyePos();
		Vec3d look = caster.getRotationVec(1.0F).normalize();
		Box area = caster.getBoundingBox().expand(radius);

		for (Entity entity : world.getOtherEntities(caster, area, e -> e instanceof LivingEntity && e.isAlive())) {
			Vec3d toEntity = entity.getPos().subtract(caster.getPos());
			double distanceSq = toEntity.lengthSquared();
			if (distanceSq < 0.0001 || distanceSq > radius * radius) {
				continue;
			}

			double facing = look.dotProduct(toEntity.normalize());
			if (facing < 0.3) {
				continue;
			}

			Vec3d push = toEntity.normalize().multiply(1.2 + level * 0.15).add(0, 0.35, 0);
			entity.addVelocity(push.x, push.y, push.z);
			entity.velocityModified = true;
			((LivingEntity) entity).damage(world, world.getDamageSources().magic(), 1.0F + level * 0.5F);
		}

		world.spawnParticles(ParticleTypes.SWEEP_ATTACK, origin.x + look.x, origin.y + look.y, origin.z + look.z, 3, 0.2, 0.2, 0.2, 0.0);
		world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.8F);
	}
}
