package com.arcanemagic.spell.spells;

import java.util.Optional;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;
import com.arcanemagic.util.SpellTargeting;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** 視線上の一番近い敵にダメージ+鈍化+弱体化を与える貫通魔法。 */
public class IceShardSpell implements Spell {

	private static final double RANGE = 12.0;

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "ice_shard");
	}

	@Override
	public Text displayName() {
		return Text.translatable("spell.arcanemagic.ice_shard");
	}

	@Override
	public int baseCooldownTicks() {
		return 30;
	}

	@Override
	public void cast(ServerWorld world, PlayerEntity caster, int level) {
		Optional<LivingEntity> target = SpellTargeting.raycastEntity(world, caster, RANGE);

		Vec3d start = caster.getEyePos();
		Vec3d end = SpellTargeting.forwardPoint(caster, RANGE);
		Vec3d mid = start.add(end).multiply(0.5);
		world.spawnParticles(ParticleTypes.SNOWFLAKE, mid.x, mid.y, mid.z, 20, 0.3, 0.3, 0.3, 0.01);
		world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8F, 1.4F);

		target.ifPresent(entity -> {
			entity.damage(world, world.getDamageSources().magic(), 3.0F + level);
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60 + level * 10, 1));
			entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60 + level * 10, 0));
		});
	}
}
