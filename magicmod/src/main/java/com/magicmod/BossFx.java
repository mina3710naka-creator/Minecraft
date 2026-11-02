package com.magicmod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** 敵のまとう気配と、中ボス・ボスの登場/第2形態/討伐の演出。 */
public final class BossFx {

	private BossFx() {
	}

	static void title(ServerLevel l, Vec3 at, double radius, Component title, Component sub, int stay) {
		for (ServerPlayer p : l.players()) {
			if (p.position().distanceTo(at) < radius) {
				p.connection.send(new ClientboundSetTitlesAnimationPacket(8, stay, 16));
				p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
				p.connection.send(new ClientboundSetTitleTextPacket(title));
			}
		}
	}

	/** 属性のオーラ(毎 tick 呼ばれ、中で間引く)。 */
	static void aura(Mob m, ServerLevel l, Enemies.Def d, int phase, long t) {
		Element e = d.element();
		int[] pal = Vfx.palette(e);
		double h = m.getBbHeight(), w = m.getBbWidth();
		Vec3 p = m.position();
		switch (d.tier()) {
			case MINION -> {
				if (t % 6 == 0) {
					Vfx.near(l, Fx.of(e), p.x, p.y + h * 0.5, p.z, 1, w * 0.5, h * 0.3, w * 0.5, 0.01);
				}
			}
			case MIDBOSS -> {
				if (t % 3 == 0) {
					for (int i = 0; i < 3; i++) {
						double a = t * 0.12 + i * Math.PI * 2 / 3;
						Vfx.near(l, Vfx.dust(pal[0], 1.1f), p.x + Math.cos(a) * (w * 0.7 + 0.4), p.y + h * 0.5, p.z + Math.sin(a) * (w * 0.7 + 0.4), 1, 0,
								0, 0, 0);
					}
					Vfx.near(l, Fx.of(e), p.x, p.y + h * 0.5, p.z, 1, w * 0.4, h * 0.3, w * 0.4, 0.01);
				}
				if (t % 20 == 0) {
					Vfx.ring(l, p.add(0, 0.1, 0), w + 0.8, Vfx.dust(pal[1], 0.8f), 18);
				}
			}
			case BOSS -> {
				if (t % 2 == 0) {
					for (int i = 0; i < 4; i++) {
						double a = t * 0.1 + i * Math.PI / 2;
						double r = w * 0.6 + 0.8;
						Vfx.near(l, Vfx.dust(pal[0], 1.4f), p.x + Math.cos(a) * r, p.y + h * 0.35, p.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
						Vfx.near(l, Vfx.dust(pal[1], 1.0f), p.x + Math.cos(-a) * r * 0.8, p.y + h * 0.75, p.z + Math.sin(-a) * r * 0.8, 1, 0, 0, 0, 0);
					}
					Vfx.near(l, Fx.of(e), p.x, p.y + h * 0.5, p.z, 2, w * 0.4, h * 0.35, w * 0.4, 0.02);
				}
				if (phase == 2) {
					if (t % 10 == 0) {
						Vfx.magicCircle(l, p, w + 2.5, t * 0.05, pal[0], pal[2]);
						Vfx.near(l, Vfx.secondary(e), p.x, p.y + h, p.z, 6, w * 0.6, 0.6, w * 0.6, 0.05);
					}
					if (t % 40 == 0) {
						l.playSound(null, m.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, m.getSoundSource(), 1.5f, 0.6f);
					}
				}
			}
		}
	}

	private static Component sub(Enemies.Def d) {
		String tier = d.tier() == Enemies.Tier.BOSS ? "属性超越ボス" : "中ボス";
		return Component.literal(tier + "  ―  " + d.element().ja + "属性 / 弱点: " + (d.weak() == null ? "-" : d.weak().ja) + " / 同属性は半減")
				.withStyle(ChatFormatting.GRAY);
	}

	static void intro(Mob m, ServerLevel l, Enemies.Def d) {
		Vec3 p = m.position();
		int[] pal = Vfx.palette(d.element());
		boolean boss = d.tier() == Enemies.Tier.BOSS;
		title(l, p, 64, Component.literal(d.name()).withStyle(boss ? ChatFormatting.GOLD : ChatFormatting.YELLOW, ChatFormatting.BOLD), sub(d),
				boss ? 60 : 40);
		Vfx.pillar(l, p, boss ? 14 : 7, m.getBbWidth() + 0.5, Vfx.fade(pal[2], pal[0], 1.8f), boss ? 6 : 3);
		Vfx.shockwave(l, p, boss ? 10 : 6, d.element());
		Vfx.flash(l, p, 48, pal[0], boss ? 10 : 6);
		Vfx.shake(l, p, 48, boss ? 1.3f : 0.8f, boss ? 20 : 10);
		if (boss) {
			RandomSource r = l.getRandom();
			for (int i = 0; i < 4; i++) {
				Vec3 at = p.add(r.nextGaussian() * 5, 0, r.nextGaussian() * 5);
				Vfx.lightningLine(l, at.add(0, 20, 0), at, pal[0], 2);
			}
			l.playSound(null, m.blockPosition(), SoundEvents.WITHER_SPAWN, m.getSoundSource(), 2f, 0.8f);
			l.playSound(null, m.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, m.getSoundSource(), 1.5f, 0.7f);
		} else {
			l.playSound(null, m.blockPosition(), SoundEvents.RAVAGER_ROAR, m.getSoundSource(), 1.5f, 0.9f);
		}
	}

	static void phaseTwo(Mob m, ServerLevel l, Enemies.Def d) {
		Vec3 p = m.position();
		int[] pal = Vfx.palette(d.element());
		title(l, p, 48, Component.literal("第2形態").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.literal(d.name() + " が本気を出した").withStyle(ChatFormatting.GRAY), 30);
		m.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 4));
		m.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 30, 0));
		Vfx.shockwave(l, p, 8, d.element());
		Vfx.burstOut(l, p.add(0, m.getBbHeight() * 0.5, 0), Vfx.fade(0xFFFFFF, pal[0], 2.5f), 60, 0.8);
		Vfx.far(l, ParticleTypes.SONIC_BOOM, p.x, p.y + m.getBbHeight() * 0.5, p.z, 1, 0, 0, 0, 0);
		Vfx.flash(l, p, 48, pal[0], 8);
		Vfx.shake(l, p, 48, 1.4f, 20);
		l.playSound(null, m.blockPosition(), SoundEvents.RAVAGER_ROAR, m.getSoundSource(), 2.5f, 0.6f);
		l.playSound(null, m.blockPosition(), SoundEvents.WARDEN_ROAR, m.getSoundSource(), 1.5f, 0.8f);
		for (ServerPlayer pl : l.players()) {
			if (pl.distanceToSqr(m) < 64 && !pl.isCreative()) {
				Ctx.push(pl, p, 1.4, 0.4);
			}
		}
	}

	static void death(LivingEntity t, ServerLevel l, Enemies.Def d) {
		Vec3 p = t.position().add(0, t.getBbHeight() * 0.5, 0);
		int[] pal = Vfx.palette(d.element());
		boolean boss = d.tier() == Enemies.Tier.BOSS;
		RandomSource r = l.getRandom();
		l.playSound(null, t.blockPosition(), SoundEvents.WITHER_DEATH, t.getSoundSource(), boss ? 1.5f : 0.8f, 1.1f);
		Tasks.repeat(0, 4, boss ? 8 : 4, i -> {
			Vec3 at = p.add(r.nextGaussian() * t.getBbWidth(), r.nextGaussian() * 0.6, r.nextGaussian() * t.getBbWidth());
			Vfx.far(l, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			Vfx.impact(l, at, d.element(), 1.8f);
			l.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), t.getSoundSource(), 1f, 0.8f + r.nextFloat() * 0.4f);
		});
		Tasks.later(boss ? 34 : 18, () -> {
			Vfx.pillar(l, p.subtract(0, t.getBbHeight() * 0.5, 0), boss ? 30 : 12, 1.2, ParticleTypes.END_ROD, 4);
			Vfx.rays(l, p, pal[2], boss ? 50 : 24, boss ? 10 : 6, 16);
			Vfx.wave(l, p, boss ? 12 : 7, 10, Vfx.fade(0xFFFFFF, pal[0], 2f));
			Vfx.flash(l, p, 64, 0xFFFFFF, boss ? 14 : 8);
			Vfx.shake(l, p, 64, boss ? 1.6f : 0.9f, boss ? 20 : 10);
			l.playSound(null, p.x, p.y, p.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, t.getSoundSource(), 1f, 1f);
			title(l, p, 64, Component.literal(boss ? "討伐!" : "撃破!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.literal(d.name()).withStyle(ChatFormatting.GRAY), 40);
		});
	}
}
