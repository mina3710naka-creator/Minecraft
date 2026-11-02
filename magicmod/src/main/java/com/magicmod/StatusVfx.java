package com.magicmod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 状態異常・補助効果が「見て分かる」ようにする(3 tick ごと)。 */
public final class StatusVfx {

	private StatusVfx() {
	}

	static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			long t = level.getGameTime();
			if (t % 3 != 0) {
				return;
			}
			for (Entity e : level.getAllEntities()) {
				if (e instanceof LivingEntity le && le.isAlive() && !le.getActiveEffects().isEmpty() && !le.isInvisible()) {
					draw(level, le, t);
				}
			}
		});
	}

	private static boolean has(LivingEntity e, Holder<MobEffect> h) {
		return e.hasEffect(h);
	}

	private static void draw(ServerLevel l, LivingEntity e, long t) {
		double h = e.getBbHeight(), w = Math.max(0.4, e.getBbWidth() * 0.6);
		Vec3 p = e.position();
		Vec3 head = p.add(0, h + 0.35, 0);
		if (has(e, ModEffects.BLEED)) {
			Vfx.near(l, Vfx.dust(0x9A0010, 0.9f), p.x, p.y + h * 0.6, p.z, 2, w * 0.4, h * 0.25, w * 0.4, 0);
			Vfx.near(l, ParticleTypes.DRIPPING_LAVA, p.x, p.y + h * 0.5, p.z, 1, w * 0.3, 0.1, w * 0.3, 0);
		}
		if (has(e, ModEffects.BURN)) {
			Vfx.near(l, ParticleTypes.FLAME, p.x, p.y + h * 0.4, p.z, 2, w * 0.4, h * 0.3, w * 0.4, 0.01);
			Vfx.near(l, ParticleTypes.SMOKE, p.x, p.y + h, p.z, 1, w * 0.3, 0.1, w * 0.3, 0.01);
		}
		if (has(e, ModEffects.SHOCK) || has(e, ModEffects.OVERCHARGE)) {
			MobEffectInstance oc = e.getEffect(ModEffects.OVERCHARGE);
			int arcs = 1 + (oc == null ? 0 : oc.getAmplifier());
			for (int i = 0; i < arcs; i++) {
				Vec3 a = p.add((l.getRandom().nextFloat() - 0.5) * w * 2, l.getRandom().nextFloat() * h, (l.getRandom().nextFloat() - 0.5) * w * 2);
				Vec3 b = a.add(l.getRandom().nextGaussian() * 0.4, l.getRandom().nextGaussian() * 0.4, l.getRandom().nextGaussian() * 0.4);
				Vfx.line(l, a, b, Vfx.dust(0xFFE84A, 0.5f), 0.12);
			}
			Vfx.near(l, ParticleTypes.ELECTRIC_SPARK, p.x, p.y + h * 0.5, p.z, 2, w * 0.5, h * 0.3, w * 0.5, 0.05);
		}
		if (has(e, ModEffects.PARALYSIS)) {
			Vfx.ring(l, p.add(0, h * 0.5, 0), w + 0.2, Vfx.dust(0xFFF080, 0.6f), 10);
		}
		if (has(e, ModEffects.STUN) || has(e, ModEffects.KNOCKDOWN)) {
			for (int i = 0; i < 3; i++) {
				double a = t * 0.35 + i * Math.PI * 2 / 3;
				Vfx.near(l, ParticleTypes.WAX_ON, head.x + Math.cos(a) * 0.45, head.y, head.z + Math.sin(a) * 0.45, 1, 0, 0, 0, 0);
			}
		}
		if (has(e, ModEffects.BIND)) {
			for (int k = 0; k < 2; k++) {
				double y = p.y + h * (0.3 + 0.4 * k);
				for (int i = 0; i < 10; i++) {
					double a = Math.PI * 2 * i / 10 + t * 0.2 * (k == 0 ? 1 : -1);
					Vfx.near(l, Vfx.dust(0x6A4CC0, 0.7f), p.x + Math.cos(a) * (w + 0.15), y, p.z + Math.sin(a) * (w + 0.15), 1, 0, 0, 0, 0);
				}
			}
		}
		if (has(e, ModEffects.DROWN)) {
			Vfx.near(l, ParticleTypes.BUBBLE, p.x, e.getEyeY(), p.z, 4, 0.3, 0.3, 0.3, 0.05);
			Vfx.near(l, ParticleTypes.BUBBLE_POP, p.x, e.getEyeY() + 0.3, p.z, 2, 0.2, 0.1, 0.2, 0.02);
		}
		if (has(e, ModEffects.WET)) {
			Vfx.near(l, ParticleTypes.DRIPPING_WATER, p.x, p.y + h * 0.8, p.z, 1, w * 0.4, 0.1, w * 0.4, 0);
		}
		if (has(e, ModEffects.FEAR)) {
			Vfx.near(l, Vfx.dust(0x3A1050, 1f), head.x, head.y, head.z, 2, 0.2, 0.1, 0.2, 0);
			Vfx.near(l, ParticleTypes.SMOKE, head.x, head.y, head.z, 1, 0.15, 0.05, 0.15, 0.01);
		}
		if (has(e, ModEffects.CONFUSION)) {
			double a = t * 0.5;
			Vfx.near(l, ParticleTypes.WITCH, head.x + Math.cos(a) * 0.4, head.y, head.z + Math.sin(a) * 0.4, 1, 0, 0, 0, 0);
			Vfx.near(l, ParticleTypes.WITCH, head.x - Math.cos(a) * 0.4, head.y, head.z - Math.sin(a) * 0.4, 1, 0, 0, 0, 0);
		}
		if (has(e, ModEffects.SILENCE)) {
			Vfx.line(l, head.add(-0.25, -0.25, 0), head.add(0.25, 0.25, 0), Vfx.dust(0x6060A0, 0.6f), 0.1);
			Vfx.line(l, head.add(-0.25, 0.25, 0), head.add(0.25, -0.25, 0), Vfx.dust(0x6060A0, 0.6f), 0.1);
		}
		if (has(e, ModEffects.MANA_SEAL)) {
			Vfx.ring(l, head, 0.3, Vfx.dust(0x2040A0, 0.6f), 8);
		}
		if (has(e, ModEffects.ARMOR_BREAK)) {
			Vfx.near(l, Vfx.dust(0x808088, 0.7f), p.x, p.y + h * 0.6, p.z, 2, w * 0.5, h * 0.2, w * 0.5, 0);
		}
		if (has(e, ModEffects.CLONE)) {
			Vfx.near(l, ParticleTypes.SQUID_INK, p.x - e.getLookAngle().x, p.y + h * 0.5, p.z - e.getLookAngle().z, 3, 0.2, h * 0.3, 0.2, 0);
		}
		if (has(e, ModEffects.REBIRTH) && t % 9 == 0) {
			Vfx.near(l, Vfx.dust(0xFFD040, 0.6f), p.x, p.y + h * 0.5, p.z, 1, w, h * 0.4, w, 0);
		}
		if (has(e, ModEffects.WIND_SHIELD)) {
			double a = t * 0.4;
			Vfx.near(l, ParticleTypes.CLOUD, p.x + Math.cos(a) * (w + 0.5), p.y + h * 0.5, p.z + Math.sin(a) * (w + 0.5), 1, 0, 0.1, 0, 0);
		}
		if (has(e, ModEffects.AQUA_SHIELD)) {
			Vfx.near(l, ParticleTypes.SPLASH, p.x, p.y + h * 0.5, p.z, 2, w + 0.2, h * 0.4, w + 0.2, 0);
		}
		if (has(e, ModEffects.HEAT_HAZE)) {
			Vfx.near(l, ParticleTypes.WHITE_SMOKE, p.x, p.y + 0.2, p.z, 1, w, 0.1, w, 0.01);
		}
		if (has(e, ModEffects.COUNTER)) {
			Vfx.ring(l, p.add(0, 0.1, 0), w + 0.6, Vfx.dust(0xD0D0FF, 0.7f), 14);
		}
		if (has(e, ModEffects.JET_WING)) {
			Vfx.near(l, ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.2, 0.05, 0.2, 0.01);
		}
		if (has(e, ModEffects.DOUBLE_JUMP) && t % 6 == 0) {
			Vfx.near(l, ParticleTypes.SMALL_GUST, p.x, p.y + 0.1, p.z, 1, 0.3, 0, 0.3, 0);
		}
		MobEffectInstance doom = e.getEffect(ModEffects.DOOM);
		if (doom != null) {
			int sec = (doom.getDuration() + 19) / 20;
			double rot = t * 0.2;
			Vfx.magicCircle(l, head.add(0, 0.25, 0), 0.5, rot, 0x8A1E6A, 0xFF60C0);
			if (doom.getDuration() % 20 < 3) {
				l.playSound(null, e.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), e.getSoundSource(), 1f, 0.5f + 0.1f * Math.max(0, 6 - sec));
				if (e instanceof ServerPlayer sp && sec <= 5) {
					sp.sendOverlayMessage(Component.literal("ドゥームカース 残り " + sec + " 秒! 解呪の聖水・浄化で解ける").withStyle(ChatFormatting.DARK_PURPLE));
				}
			}
		}
	}
}
