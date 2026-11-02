package com.magicmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** 大魔法(⑧〜⑩)の演出。時間をかけて組み立てる「見せ場」。 */
public final class Cinema {

	private Cinema() {
	}

	/** 風⑩ テンペストブレイカー: 暴風の爆発 → 自分を中心に巨大な竜巻が残る。 */
	static void tempest(Ctx c) {
		ServerLevel l = c.level;
		Vec3 o = c.caster.position();
		int[] pal = Vfx.palette(Element.WIND);
		Vfx.wave(l, o.add(0, 0.3, 0), 10, 8, Vfx.fade(0xFFFFFF, pal[0], 2f));
		Vfx.wave(l, o.add(0, 1.4, 0), 9, 10, ParticleTypes.CLOUD);
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * i / 6;
			Vfx.far(l, ParticleTypes.GUST_EMITTER_LARGE, o.x + Math.cos(a) * 5, o.y + 1, o.z + Math.sin(a) * 5, 1, 0, 0, 0, 0);
		}
		Vfx.rays(l, o.add(0, 1, 0), 0xFFFFFF, 24, 9, 10);
		Vfx.flash(l, o, 32, 0xD8FFE8, 6);
		Vfx.shake(l, o, 40, 1.1f, 14);
		Vfx.sound(l, o, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2.5f, 0.5f);
		Vfx.sound(l, o, SoundEvents.ELYTRA_FLYING, 1.5f, 0.6f);
		Tasks.repeat(2, 2, 18, i -> Vfx.tornado(l, c.caster.position(), 9, 1.5 + i * 0.25, l.getGameTime(), ParticleTypes.CLOUD,
				Vfx.dust(pal[0], 1.4f)));
	}

	/** 電気⑩ ジャッジメントボルト: 雷が次々に落ち、最後に巨大な一撃。onFinal で本命のダメージ。 */
	static void judgement(Ctx c, Vec3 p, Runnable onFinal) {
		ServerLevel l = c.level;
		RandomSource r = l.getRandom();
		int[] pal = Vfx.palette(Element.THUNDER);
		Vfx.sound(l, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 3f, 0.6f);
		Tasks.repeat(0, 3, 7, i -> {
			Vec3 at = p.add(r.nextGaussian() * 4, 0, r.nextGaussian() * 4);
			Vfx.lightningLine(l, at.add(r.nextGaussian() * 2, 22, r.nextGaussian() * 2), at, i % 2 == 0 ? pal[0] : pal[1], 2);
			Vfx.impact(l, at.add(0, 0.3, 0), Element.THUNDER, 1.2f);
			Vfx.flash(l, at, 48, 0xF0F8FF, 3);
			if (i % 3 == 0) {
				c.lightning(at);
			}
		});
		Tasks.later(22, () -> {
			for (int k = 0; k < 4; k++) {
				Vfx.lightningLine(l, p.add(r.nextGaussian() * 0.6, 30, r.nextGaussian() * 0.6), p, k % 2 == 0 ? 0xFFFFFF : pal[0], 3);
			}
			c.lightning(p);
			Vfx.pillar(l, p, 8, 1.2, ParticleTypes.ELECTRIC_SPARK, 6);
			Vfx.far(l, ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
			Vfx.flash(l, p, 64, 0xFFFFFF, 10);
			Vfx.shake(l, p, 48, 1.5f, 16);
			Vfx.sound(l, p, SoundEvents.LIGHTNING_BOLT_IMPACT, 3f, 0.5f);
			onFinal.run();
		});
	}

	/** 炎⑩ インフェルノブレス: 竜の息のような炎の奔流(tick ごと)。 */
	static void breathTick(Ctx c, int i) {
		ServerLevel l = c.level;
		RandomSource r = l.getRandom();
		Vec3 from = Vfx.hand(c.caster);
		Vec3 d = c.caster.getLookAngle();
		for (int k = 0; k < 10; k++) {
			Vec3 v = d.scale(0.8 + r.nextDouble() * 0.5).add(r.nextGaussian() * 0.12, r.nextGaussian() * 0.08, r.nextGaussian() * 0.12);
			Vfx.move(l, k % 4 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, from, v);
		}
		for (int k = 0; k < 3; k++) {
			Vfx.move(l, ParticleTypes.LARGE_SMOKE, from.add(d.scale(3)), d.scale(0.35).add(r.nextGaussian() * 0.05, 0.05, r.nextGaussian() * 0.05));
		}
		Vfx.dragonBreath(l, from.add(d.scale(5)), 4);
		if (i % 2 == 0) {
			Vfx.near(l, ParticleTypes.LAVA, from.x + d.x * 7, from.y + d.y * 7, from.z + d.z * 7, 3, 0.8, 0.3, 0.8, 0);
			Vfx.shake(l, c.caster.position(), 8, 0.3f, 3);
		}
	}

	/** 隕石の尾(毎 tick)。 */
	static void meteorTail(ServerLevel l, Vec3 p) {
		Vfx.near(l, ParticleTypes.FLAME, p.x, p.y, p.z, 10, 0.35, 0.35, 0.35, 0.03);
		Vfx.near(l, ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.3, p.z, 4, 0.3, 0.3, 0.3, 0.02);
		Vfx.near(l, ParticleTypes.LAVA, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0);
		Vfx.near(l, Vfx.fade(0xFFF0B0, 0xFF3000, 3f), p.x, p.y, p.z, 4, 0.2, 0.2, 0.2, 0);
	}

	static void meteorImpact(Ctx c, Vec3 p, float mult) {
		ServerLevel l = c.level;
		Vfx.far(l, ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		Vfx.debris(l, p, 3, 40);
		Vfx.burstOut(l, p.add(0, 0.5, 0), ParticleTypes.LAVA, 14, 0.6);
		Vfx.near(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + 0.5, p.z, 12, 1.5, 0.5, 1.5, 0.03);
		Vfx.flash(l, p, 40 * mult, 0xFFB060, 5);
		Vfx.shake(l, p, 32 * mult, 1.2f * mult, 12);
		Vfx.sound(l, p, SoundEvents.GENERIC_EXPLODE.value(), 2f * mult, 0.7f);
		Terrain.melt(l, p, 3, mult >= 0.99f);
	}

	/** 水⑩ タイフーンディザスター: 押し寄せる大波と豪雨。 */
	static void tsunami(Ctx c, Vec3 p) {
		ServerLevel l = c.level;
		RandomSource r = l.getRandom();
		Vfx.sound(l, p, SoundEvents.GENERIC_SPLASH, 3f, 0.4f);
		Vfx.sound(l, p, SoundEvents.WEATHER_RAIN_ABOVE, 2f, 0.6f);
		Vfx.flash(l, p, 40, 0x80C0FF, 6);
		Vfx.shake(l, p, 40, 1.0f, 16);
		Tasks.repeat(0, 1, 16, i -> {
			double rad = 1 + i * 0.5;
			int n = (int) (rad * 7);
			for (int k = 0; k < n; k++) {
				double a = Math.PI * 2 * k / n;
				double x = p.x + Math.cos(a) * rad, z = p.z + Math.sin(a) * rad;
				double h = 2.5 * Math.sin(Math.PI * Math.min(1, i / 15.0)) + 0.5;
				Vfx.near(l, ParticleTypes.SPLASH, x, p.y + h, z, 2, 0.1, 0.4, 0.1, 0.1);
				Vfx.near(l, ParticleTypes.FALLING_WATER, x, p.y + h + 0.5, z, 1, 0.1, 0.2, 0.1, 0);
				if (k % 3 == 0) {
					Vfx.near(l, Vfx.dust(0x2F7BFF, 1.6f), x, p.y + h * r.nextDouble(), z, 1, 0.1, 0.3, 0.1, 0);
				}
			}
		});
		Tasks.repeat(0, 2, 60, i -> Vfx.near(l, ParticleTypes.FALLING_WATER, p.x, p.y + 7, p.z, 25, 7, 0.5, 7, 0));
		Terrain.douse(l, p, 8);
	}

	/** 重力⑩ グラビティコラプス: 特異点が育って吸い込み、最後に潰れて弾ける。 */
	static void singularity(Ctx c, Vec3 p, int ticks) {
		ServerLevel l = c.level;
		Vec3 core = p.add(0, 1.2, 0);
		Vfx.sound(l, p, SoundEvents.PORTAL_TRIGGER, 1.5f, 0.5f);
		Tasks.repeat(0, 1, ticks, i -> {
			float g = (float) i / ticks;
			Vfx.near(l, Vfx.dust(0x000000, 1.5f + g * 2.5f), core.x, core.y, core.z, 6, 0.1 + g * 0.3, 0.1 + g * 0.3, 0.1 + g * 0.3, 0);
			Vfx.ring(l, core, 0.6 + g * 1.2, Vfx.dust(0xB080FF, 0.8f), 20);
			Vfx.vortex(l, core, 8, ParticleTypes.REVERSE_PORTAL, 10);
			if (i % 2 == 0) {
				Vfx.vortex(l, p.add(0, 0.2, 0), 7, Vfx.dust(0x6020C0, 1.4f), 6);
				Vfx.debris(l, p.add(l.getRandom().nextGaussian() * 4, 0, l.getRandom().nextGaussian() * 4), 0.8, 4);
			}
			if (i % 12 == 0) {
				Vfx.sound(l, p, SoundEvents.WARDEN_HEARTBEAT, 2f, 0.6f + g);
				Vfx.shake(l, p, 24, 0.3f + g * 0.4f, 8);
			}
		});
		Tasks.later(ticks, () -> {
			Vfx.far(l, ParticleTypes.SONIC_BOOM, core.x, core.y, core.z, 1, 0, 0, 0, 0);
			Vfx.far(l, ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 2, 0.5, 0.5, 0.5, 0);
			Vfx.burstOut(l, core, Vfx.fade(0xFFFFFF, 0x7A3CE0, 2.5f), 60, 0.9);
			Vfx.flash(l, p, 48, 0xD0A0FF, 8);
			Vfx.shake(l, p, 48, 1.6f, 18);
		});
	}

	/** 闇⑩ アビスゲート: 地面に深淵の門が開き、影の柱が立ちのぼる(継続)。 */
	static void abyss(Ctx c, Vec3 p, int seconds) {
		ServerLevel l = c.level;
		Vfx.sound(l, p, SoundEvents.WARDEN_EMERGE, 1.5f, 0.6f);
		Vfx.flash(l, p, 24, 0x20002A, 10);
		Tasks.repeat(0, 2, seconds * 10, i -> {
			long t = l.getGameTime();
			Vfx.magicCircle(l, p, 6, -t * 0.04, 0x6A3A90, 0x1A0A28);
			Vfx.magicCircle(l, p, 3.5, t * 0.07, 0xB070FF, 0x2A1638);
			for (int k = 0; k < 6; k++) {
				double a = Math.PI * 2 * k / 6 + t * 0.03;
				Vec3 b = p.add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
				Vfx.helix(l, b, b.add(0, 3 + Math.sin(t * 0.2 + k) * 1.5, 0), 0.3, 2, ParticleTypes.SQUID_INK, Vfx.dust(0x2A1638, 1.5f));
			}
			Vfx.near(l, ParticleTypes.SCULK_SOUL, p.x, p.y + 0.3, p.z, 3, 3, 0.2, 3, 0.04);
			if (i % 15 == 0) {
				Vfx.far(l, new ShriekParticleOption(0), p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
				Vfx.sound(l, p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1f, 0.5f);
			}
		});
	}

	/** 光⑩ ディヴァインジャッジメント: 天から光の柱が降り、少し遅れて裁きが下る。 */
	static void divine(Ctx c, Vec3 p, Runnable onHit) {
		ServerLevel l = c.level;
		Vfx.sound(l, p, SoundEvents.BEACON_ACTIVATE, 3f, 0.6f);
		Tasks.repeat(0, 1, 14, i -> {
			double r = 3 - i * 0.12;
			Vfx.ring(l, p.add(0, 0.2, 0), r, Vfx.dust(0xFFF2A0, 1.2f), 30);
			Vfx.pillar(l, p.add(0, 30 - i * 2, 0), 4, r * 0.7, ParticleTypes.END_ROD, 2);
		});
		Tasks.later(12, () -> {
			Vfx.pillar(l, p, 30, 1.8, ParticleTypes.END_ROD, 5);
			Vfx.pillar(l, p, 30, 1.0, Vfx.dust(0xFFFFFF, 2.5f), 4);
			Vfx.rays(l, p.add(0, 1, 0), 0xFFF6C8, 40, 8, 14);
			Vfx.wave(l, p.add(0, 0.2, 0), 8, 8, Vfx.fade(0xFFFFFF, 0xFFE070, 2f));
			Vfx.flash(l, p, 64, 0xFFFFFF, 12);
			Vfx.shake(l, p, 40, 1.2f, 12);
			Vfx.sound(l, p, SoundEvents.BEACON_DEACTIVATE, 3f, 0.5f);
			Vfx.sound(l, p, SoundEvents.TOTEM_USE, 1f, 1.5f);
			onHit.run();
		});
	}

	/** 呪い⑩ ドゥームカース: 呪印が刻まれ、魂が噴き出す。 */
	static void doom(Ctx c, Vec3 p) {
		ServerLevel l = c.level;
		Vfx.sound(l, p, SoundEvents.ELDER_GUARDIAN_CURSE, 1.5f, 0.5f);
		Vfx.sound(l, p, SoundEvents.WITHER_AMBIENT, 1f, 0.6f);
		Vfx.flash(l, p, 24, 0x400020, 8);
		Tasks.repeat(0, 2, 20, i -> {
			long t = l.getGameTime();
			Vfx.magicCircle(l, p, 4, t * 0.15, 0x8A1E6A, 0xFF60C0);
			Vfx.burstOut(l, p.add(0, 0.3, 0), ParticleTypes.SCULK_SOUL, 4, 0.25);
			if (i % 5 == 0) {
				Vfx.far(l, new ShriekParticleOption(0), p.x, p.y + 0.3, p.z, 1, 0, 0, 0, 0);
			}
		});
	}
}
