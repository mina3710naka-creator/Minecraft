package com.arcanemagic.spell.spells;

import java.util.Optional;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;
import com.arcanemagic.util.SpellTargeting;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 視線上の一番近い敵にダメージ+鈍化+弱体化を与える貫通魔法。 */
public class IceShardSpell implements Spell {

	private static final double RANGE = 12.0;

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "ice_shard");
	}

	@Override
	public Component displayName() {
		return Component.translatable("spell.arcanemagic.ice_shard");
	}

	@Override
	public int baseCooldownTicks() {
		return 30;
	}

	@Override
	public void cast(ServerLevel serverLevel, Player caster, int spellLevel) {
		Optional<LivingEntity> target = SpellTargeting.raycastEntity(serverLevel, caster, RANGE);

		Vec3 start = caster.getEyePosition();
		Vec3 end = SpellTargeting.forwardPoint(caster, RANGE);
		Vec3 mid = start.add(end).scale(0.5);
		serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, mid.x, mid.y, mid.z, 20, 0.3, 0.3, 0.3, 0.01);
		serverLevel.playSound(null, caster.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);

		target.ifPresent(entity -> {
			entity.hurtServer(serverLevel, serverLevel.damageSources().magic(), 3.0F + spellLevel);
			entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60 + spellLevel * 10, 1));
			entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60 + spellLevel * 10, 0));
		});
	}
}
