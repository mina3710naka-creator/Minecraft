package com.magicmod;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** 属性ごとの見た目・音。 */
public final class Fx {

	private Fx() {
	}

	public static ParticleOptions of(Element e) {
		if (e == null) {
			return ParticleTypes.CRIT;
		}
		return switch (e) {
			case WIND -> ParticleTypes.CLOUD;
			case THUNDER -> ParticleTypes.ELECTRIC_SPARK;
			case FIRE -> ParticleTypes.FLAME;
			case WATER -> ParticleTypes.SPLASH;
			case GRAVITY -> ParticleTypes.REVERSE_PORTAL;
			case DARK -> ParticleTypes.SQUID_INK;
			case LIGHT -> ParticleTypes.END_ROD;
			case CURSE -> ParticleTypes.WITCH;
		};
	}

	public static ParticleOptions big(Element e) {
		if (e == null) {
			return ParticleTypes.SWEEP_ATTACK;
		}
		return switch (e) {
			case WIND -> ParticleTypes.GUST;
			case THUNDER -> ParticleTypes.ELECTRIC_SPARK;
			case FIRE -> ParticleTypes.LAVA;
			case WATER -> ParticleTypes.BUBBLE_POP;
			case GRAVITY -> ParticleTypes.PORTAL;
			case DARK -> ParticleTypes.LARGE_SMOKE;
			case LIGHT -> ParticleTypes.FIREWORK;
			case CURSE -> ParticleTypes.SCULK_SOUL;
		};
	}

	public static SoundEvent castSound(Element e) {
		if (e == null) {
			return SoundEvents.PLAYER_ATTACK_SWEEP;
		}
		return switch (e) {
			case WIND -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value();
			case THUNDER -> SoundEvents.TRIDENT_THUNDER.value();
			case FIRE -> SoundEvents.FIRECHARGE_USE;
			case WATER -> SoundEvents.PLAYER_SPLASH;
			case GRAVITY -> SoundEvents.ENDERMAN_TELEPORT;
			case DARK -> SoundEvents.WITHER_SHOOT;
			case LIGHT -> SoundEvents.AMETHYST_BLOCK_CHIME;
			case CURSE -> SoundEvents.EVOKER_CAST_SPELL;
		};
	}
}
