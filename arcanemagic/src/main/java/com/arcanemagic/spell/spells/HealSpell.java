package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** 自分を回復し、再生効果を付与する。 */
public class HealSpell implements Spell {

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "heal");
	}

	@Override
	public Component displayName() {
		return Component.translatable("spell.arcanemagic.heal");
	}

	@Override
	public int baseCooldownTicks() {
		return 100;
	}

	@Override
	public void cast(ServerLevel serverLevel, Player caster, int spellLevel) {
		caster.heal(2.0F + spellLevel * 1.5F);
		caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40 + spellLevel * 10, Math.min(1, spellLevel / 3)));

		double particleY = caster.getY() + caster.getBbHeight() * 0.7;
		serverLevel.sendParticles(ParticleTypes.HEART, caster.getX(), particleY, caster.getZ(), 6, 0.4, 0.4, 0.4, 0.0);
		serverLevel.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
	}
}
