package com.arcanemagic.spell.spells;

import com.arcanemagic.ArcaneMagic;
import com.arcanemagic.spell.Spell;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 自分を回復し、再生効果を付与する。 */
public class HealSpell implements Spell {

	@Override
	public Identifier id() {
		return Identifier.of(ArcaneMagic.MOD_ID, "heal");
	}

	@Override
	public Text displayName() {
		return Text.translatable("spell.arcanemagic.heal");
	}

	@Override
	public int baseCooldownTicks() {
		return 100;
	}

	@Override
	public void cast(ServerWorld world, PlayerEntity caster, int level) {
		caster.heal(2.0F + level * 1.5F);
		caster.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40 + level * 10, Math.min(1, level / 3)));

		world.spawnParticles(ParticleTypes.HEART, caster.getX(), caster.getBodyY(0.7), caster.getZ(), 6, 0.4, 0.4, 0.4, 0.0);
		world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6F, 1.6F);
	}
}
