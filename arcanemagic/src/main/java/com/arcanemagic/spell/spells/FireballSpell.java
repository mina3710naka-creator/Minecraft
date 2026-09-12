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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 視線上の一番近い相手にダメージを与えて燃やす、炎の直撃魔法。 */
public class FireballSpell implements Spell {

	private static final double RANGE = 14.0;

	@Override
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(ArcaneMagic.MOD_ID, "fireball");
	}

	@Override
	public Component displayName() {
		return Component.translatable("spell.arcanemagic.fireball");
	}

	@Override
	public int baseCooldownTicks() {
		return 40;
	}

	@Override
	public void cast(ServerLevel serverLevel, Player caster, int spellLevel) {
		Optional<LivingEntity> target = SpellTargeting.raycastEntity(serverLevel, caster, RANGE);

		Vec3 start = caster.getEyePosition();
		Vec3 end = SpellTargeting.forwardPoint(caster, RANGE);
		Vec3 mid = start.add(end).scale(0.5);
		serverLevel.sendParticles(ParticleTypes.FLAME, mid.x, mid.y, mid.z, 24, 0.3, 0.3, 0.3, 0.02);
		serverLevel.playSound(null, caster.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);

		target.ifPresent(entity -> {
			entity.hurtServer(serverLevel, serverLevel.damageSources().magic(), 4.0F + spellLevel);
			entity.igniteForSeconds(4 + spellLevel);
		});
	}
}
