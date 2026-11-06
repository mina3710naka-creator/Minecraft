package com.magicmod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 最大魔法 × 最大魔法の「融合魔法」。
 * 通常の合成(ベースの形+サブの状態異常)とは別物で、2 つの属性から生まれる 28 種の専用魔法。
 * 名前も演出も、どれも組み合わせごとに固有。
 */
public final class Fusions {

	public record Fusion(String name, String desc, Spell.Form form) {
	}

	private static final Map<String, Fusion> ALL = new LinkedHashMap<>();

	private Fusions() {
	}

	public static String key(Element a, Element b) {
		return a.ordinal() < b.ordinal() ? a.id + "+" + b.id : b.id + "+" + a.id;
	}

	/** ベースとサブがすべて最大魔法で属性がそれぞれ違うなら、その融合魔法(サブ 1 つ = 二重、サブ 2 つ = 三重)。 */
	public static @Nullable Fusion of(Spell base, List<Spell> subs) {
		if (subs.isEmpty() || subs.size() > 2 || !"max".equals(base.rank)) {
			return null;
		}
		for (Spell s : subs) {
			if (!"max".equals(s.rank) || s.element == base.element) {
				return null;
			}
		}
		if (subs.size() == 1) {
			return ALL.get(key(base.element, subs.get(0).element));
		}
		if (subs.get(0).element == subs.get(1).element) {
			return null;
		}
		return TRIPLE.get(key3(base.element, subs.get(0).element, subs.get(1).element));
	}

	private static final Map<String, Fusion> TRIPLE = new LinkedHashMap<>();

	public static Map<String, Fusion> triples() {
		return TRIPLE;
	}

	static String key3(Element a, Element b, Element c) {
		Element[] e = { a, b, c };
		java.util.Arrays.sort(e);
		return e[0].id + "+" + e[1].id + "+" + e[2].id;
	}

	/** 三重融合の途中で、二重融合の「魔法名の表示」を黙らせる。 */
	private static boolean quiet;

	public static Map<String, Fusion> all() {
		return ALL;
	}

	private static void reg(Element a, Element b, String name, String desc, Spell.Form f) {
		ALL.put(key(a, b), new Fusion(name, desc, f));
	}

	// ---------------------------------------------------------------------------------------------
	// 共通の部品

	private static void eff(LivingEntity t, Holder<MobEffect> e, float sec, int amp) {
		ModEffects.apply(t, e, Math.round(sec * 20), amp);
	}

	private static Vec3 flat(Ctx c) {
		Vec3 d = new Vec3(c.dir.x, 0, c.dir.z);
		return d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : d.normalize();
	}

	/** 範囲内のランダムな地点に連続で落とす。 */
	private static void barrage(Ctx c, Vec3 center, double radius, int count, int interval, float mult, double hitR, java.util.function.Consumer<Vec3> fx) {
		Tasks.repeat(0, interval, count, i -> {
			if (!c.caster.isAlive()) {
				return;
			}
			double a = c.level.getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(c.level.getRandom().nextDouble()) * radius;
			Vec3 p = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
			c.areaAt(p, hitR, mult);
			fx.accept(p);
		});
	}

	/** 前へ進んでいく範囲攻撃。 */
	private static void sweep(Ctx c, int steps, int interval, double stride, double hitR, float mult, java.util.function.Consumer<Vec3> fx) {
		Vec3 d = flat(c);
		Vec3 o = c.caster.position();
		Tasks.repeat(0, interval, steps, i -> {
			Vec3 p = o.add(d.scale(2 + i * stride));
			c.areaAt(p, hitR, mult);
			fx.accept(p);
		});
	}

	private static void sound(Ctx c, Vec3 p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		Vfx.sound(c.level, p, s, vol, pitch);
	}

	private static void big(Ctx c, Vec3 p, Element e, double r, int rgb) {
		Vfx.sound(c.level, p, ModSounds.MAX_RELEASE, 1.4f, 1f);
		FxDisplay.ring(c.level, p, e, r, 10);
		Vfx.flash(c.level, p, 36, rgb, 6);
		Vfx.shake(c.level, p, 32, 1.0f, 12);
	}

	private static void announce(Ctx c, String name, Element a, Element b) {
		if (quiet) {
			return;
		}
		if (c.caster instanceof net.minecraft.server.level.ServerPlayer sp) {
			sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("◆ 融合魔法『" + name + "』").withStyle(a.fmt));
		}
		FxDisplay.circle(c.level, c.caster.position(), a, 3, 24, 4f);
		FxDisplay.circle(c.level, c.caster.position().add(0, 0.05, 0), b, 2, 24, -4f);
		Vfx.sound(c.level, c.caster.position(), SoundEvents.BEACON_POWER_SELECT, 1f, 1.5f);
	}

	static {
		Element W = Element.WIND, T = Element.THUNDER, F = Element.FIRE, Wa = Element.WATER, G = Element.GRAVITY, D = Element.DARK,
				L = Element.LIGHT, C = Element.CURSE;

		// 風 × 雷: 空に雷雲が立ち込め、雷が降り続ける。
		reg(W, T, "ストームクラウン", "頭上に雷雲を呼び、6 秒間、暴風と落雷が降り注ぐ。", c -> {
			announce(c, "ストームクラウン", W, T);
			Vec3 p = c.aimPoint(14);
			Vfx.tornado(c.level, p, 9, 4, c.level.getGameTime(), ParticleTypes.CLOUD, ParticleTypes.ELECTRIC_SPARK);
			Tasks.repeat(0, 2, 60, i -> Vfx.near(c.level, ParticleTypes.CLOUD, p.x, p.y + 9, p.z, 14, 4.5, 0.4, 4.5, 0.01));
			barrage(c, p, 6, 14, 8, 0.2f, 2.2, q -> c.lightning(q));
			c.field(p, 6, 6, 10, (cc, l) -> l.forEach(t -> {
				Ctx.push(t, p, 0.35, 0.15);
				eff(t, ModEffects.SHOCK, 3, 0);
			}));
		});

		// 風 × 炎: 炎の竜巻が前へ進みながら焼き払う。
		reg(W, F, "フレアサイクロン", "炎の竜巻が前方へ走り、巻き込んだ敵を焼きながら浮かせる。", c -> {
			announce(c, "フレアサイクロン", W, F);
			sweep(c, 12, 3, 1.6, 3.2, 0.14f, p -> {
				Vfx.tornado(c.level, p, 6, 2.6, c.level.getGameTime(), ParticleTypes.FLAME, ParticleTypes.LAVA);
				c.level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(p, p).inflate(3.2)).forEach(t -> {
					if (c.isEnemy(t)) {
						Ctx.pull(t, p, 0.4);
						eff(t, ModEffects.BURN, 4, 1);
					}
				});
				sound(c, p, SoundEvents.FIRECHARGE_USE, 0.8f, 0.6f);
			});
		});

		// 風 × 水: 大波が前方を押し流す。
		reg(W, Wa, "ストームサージ", "嵐が海を持ち上げ、高波となって前方を押し流す。", c -> {
			announce(c, "ストームサージ", W, Wa);
			sweep(c, 10, 3, 2.0, 3.6, 0.15f, p -> {
				Vfx.wave(c.level, p, 3.5, 6, ParticleTypes.SPLASH);
				Vfx.near(c.level, ParticleTypes.CLOUD, p.x, p.y + 1.5, p.z, 10, 1.6, 1, 1.6, 0.05);
				c.enemiesNear(p, 3.6).forEach(t -> {
					Ctx.push(t, c.caster.position(), 1.0, 0.25);
					eff(t, ModEffects.WET, 8, 0);
				});
				Terrain.douse(c.level, p, 3);
			});
		});

		// 風 × 重力: 敵を空へ打ち上げ、無重力のあと地面へ叩きつける。
		reg(W, G, "ゼロ・アセンション", "範囲の敵を高く打ち上げて無重力に漂わせ、一斉に地面へ叩き落とす。", c -> {
			announce(c, "ゼロ・アセンション", W, G);
			Vec3 p = c.aimPoint(14);
			List<LivingEntity> hit = c.areaAt(p, 6, 0.3f);
			Vfx.pillar(c.level, p, 12, 2.5, ParticleTypes.END_ROD, 6);
			for (LivingEntity t : hit) {
				t.setDeltaMovement(0, 1.1, 0);
				t.syncVelocity = true;
				t.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.LEVITATION, 30, 0));
			}
			c.later(34, () -> {
				for (LivingEntity t : hit) {
					if (t.isAlive()) {
						t.removeEffect(MobEffects.LEVITATION);
						t.setDeltaMovement(0, -2.5, 0);
						t.syncVelocity = true;
						c.hit(t, 0.9f);
						eff(t, ModEffects.KNOCKDOWN, 2, 0);
					}
				}
				big(c, p, G, 6, 0xB090FF);
				c.areaAt(p, 6, 0.2f);
			});
		});

		// 風 × 闇: 影の刃が全方位から斬り刻み、視界を奪う。
		reg(W, D, "シェイドハリケーン", "漆黒の風の刃が全方位から斬り刻み、視界を奪う。", c -> {
			announce(c, "シェイドハリケーン", W, D);
			Vec3 p = c.caster.position();
			Tasks.repeat(0, 2, 12, i -> {
				double a = i * 0.9;
				Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
				Vfx.line(c.level, p.add(0, 1, 0), p.add(d.scale(8)).add(0, 1, 0), ParticleTypes.SQUID_INK, 0.5);
				Vfx.line(c.level, p.add(0, 1.2, 0), p.add(d.scale(8)).add(0, 1.2, 0), ParticleTypes.CLOUD, 0.8);
				for (LivingEntity t : c.enemiesNear(p, 8)) {
					Vec3 to = t.position().subtract(p).normalize();
					if (to.dot(d) > 0.8) {
						c.hit(t, 0.13f);
						eff(t, MobEffects.BLINDNESS, 4, 0);
					}
				}
				sound(c, p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.6f + i * 0.04f);
			});
			Vfx.tornado(c.level, p, 5, 3, c.level.getGameTime(), ParticleTypes.SQUID_INK, ParticleTypes.CLOUD);
		});

		// 風 × 光: 聖なる風で味方を癒やし、敵を吹き飛ばす。
		reg(W, L, "セラフィムブリーズ", "光の風が広がり、味方を癒やして風の加護を与え、敵を吹き飛ばす。", c -> {
			announce(c, "セラフィムブリーズ", W, L);
			Vec3 p = c.caster.position();
			Vfx.wave(c.level, p, 9, 14, ParticleTypes.END_ROD);
			Vfx.rays(c.level, p.add(0, 1, 0), 0xFFF6C8, 16, 5, 14);
			big(c, p, L, 9, 0xFFF6C8);
			for (LivingEntity a : c.alliesNear(p, 9)) {
				c.heal(a, c.healAmount(0.5f));
				a.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModEffects.WIND_SHIELD, 200, 0));
				a.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.SPEED, 200, 1));
			}
			for (LivingEntity t : c.areaAt(p, 9, 0.7f)) {
				Ctx.push(t, p, 1.3, 0.4);
			}
		});

		// 風 × 呪い: 疫病を運ぶ風が輪になって広がる。
		reg(W, C, "プレイグウィンド", "毒と呪いを含んだ風が広がり続け、浴びた者に呪いを重ねる。", c -> {
			announce(c, "プレイグウィンド", W, C);
			Vec3 p = c.caster.position();
			Tasks.repeat(0, 3, 14, i -> {
				double r = 2 + i * 0.8;
				c.ring(p, r, ParticleTypes.SPORE_BLOSSOM_AIR);
				c.ring(p.add(0, 0.8, 0), r, Vfx.dust(0x7A1E5A, 1.4f));
				for (LivingEntity t : c.enemiesNear(p, r + 1)) {
					if (t.position().distanceTo(p) > r - 1.5) {
						c.hit(t, 0.1f);
						eff(t, MobEffects.POISON, 5, 1);
						eff(t, MobEffects.WEAKNESS, 6, 0);
						eff(t, ModEffects.ARMOR_BREAK, 6, 0);
					}
				}
			});
		});

		// 雷 × 炎: 超高温のプラズマ光線。
		reg(T, F, "プロミネンスレイ", "プラズマの極太光線が一直線に焼き貫き、終点で大爆発を起こす。", c -> {
			announce(c, "プロミネンスレイ", T, F);
			Vec3 o = c.origin, end = o.add(c.dir.scale(24));
			c.later(10, () -> {
				Vfx.beam(c.level, o, end, F);
				Vfx.beam(c.level, o.add(0, 0.1, 0), end.add(0, 0.1, 0), T);
				sound(c, o, SoundEvents.ENDER_DRAGON_SHOOT, 1.5f, 0.6f);
				for (LivingEntity t : c.beam(24, 2.2, 1.1f)) {
					eff(t, ModEffects.BURN, 6, 2);
					eff(t, ModEffects.SHOCK, 3, 0);
				}
				Vec3 g = c.aimPoint(24);
				c.later(6, () -> {
					c.areaAt(g, 5, 0.6f);
					Cinema.meteorImpact(c, g, 0.7f);
				});
			});
		});

		// 雷 × 水: 水を伝って雷が敵から敵へ連鎖する。
		reg(T, Wa, "ボルテックスタイド", "敵を濡らして雷を連鎖させる。濡れた敵ほど雷が深く走る。", c -> {
			announce(c, "ボルテックスタイド", T, Wa);
			Vec3 p = c.aimPoint(14);
			List<LivingEntity> list = c.enemiesNear(p, 10);
			if (list.isEmpty()) {
				c.areaAt(p, 4, 0.8f);
				return;
			}
			list.forEach(t -> eff(t, ModEffects.WET, 8, 0));
			Vfx.wave(c.level, p, 10, 10, ParticleTypes.SPLASH);
			Vec3[] prev = { c.caster.position().add(0, 1.2, 0) };
			for (int i = 0; i < Math.min(8, list.size()); i++) {
				LivingEntity t = list.get(i);
				c.later(i * 4 + 4, () -> {
					if (!t.isAlive()) {
						return;
					}
					Vec3 q = t.position().add(0, t.getBbHeight() / 2, 0);
					Vfx.lightningLine(c.level, prev[0], q, Vfx.palette(Element.THUNDER)[1], 2);
					prev[0] = q;
					c.hit(t, t.hasEffect(ModEffects.WET) ? 0.9f : 0.6f);
					eff(t, ModEffects.PARALYSIS, 2, 0);
					sound(c, q, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
				});
			}
		});

		// 雷 × 重力: 磁場で全員を引き寄せて一撃。
		reg(T, G, "マグネティックコラプス", "強烈な磁場で敵を一点に引き寄せ、まとめて雷撃で粉砕する。", c -> {
			announce(c, "マグネティックコラプス", T, G);
			Vec3 p = c.aimPoint(14);
			Vfx.vortex(c.level, p.add(0, 1, 0), 7, ParticleTypes.ELECTRIC_SPARK, 40);
			Tasks.repeat(0, 2, 15, i -> {
				for (LivingEntity t : c.enemiesNear(p, 9)) {
					Ctx.pull(t, p, 0.7);
				}
				Vfx.near(c.level, ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1, p.z, 10, 3, 1, 3, 0.2);
			});
			c.later(32, () -> {
				for (int k = 0; k < 4; k++) {
					c.lightning(p);
				}
				c.areaAt(p, 5, 1.4f);
				big(c, p, T, 6, 0xE8F4FF);
			});
		});

		// 雷 × 闇: 黒い雷で視界と詠唱を奪う。
		reg(T, D, "ブラックアウトボルト", "漆黒の雷が三度落ち、視界と詠唱を奪い去る。", c -> {
			announce(c, "ブラックアウトボルト", T, D);
			Vec3 p = c.aimPoint(16);
			for (int i = 0; i < 3; i++) {
				int k = i;
				c.later(8 + i * 7, () -> {
					Vec3 q = p.add(c.level.getRandom().nextGaussian() * 1.5 * k, 0, c.level.getRandom().nextGaussian() * 1.5 * k);
					Vfx.lightningLine(c.level, q.add(0, 20, 0), q, 0x2A1A40, 3);
					Vfx.near(c.level, ParticleTypes.SQUID_INK, q.x, q.y + 1, q.z, 30, 1.5, 1.5, 1.5, 0.1);
					Vfx.flash(c.level, q, 30, 0x201030, 5);
					for (LivingEntity t : c.areaAt(q, 4, 0.5f)) {
						eff(t, MobEffects.BLINDNESS, 5, 0);
						eff(t, ModEffects.SILENCE, 5, 0);
						eff(t, ModEffects.SHOCK, 3, 0);
					}
					sound(c, q, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2f, 0.5f);
				});
			}
		});

		// 雷 × 光: 光速の一閃。
		reg(T, L, "ライトニングセイバー", "光速の閃光が一直線に走り抜け、触れた敵を気絶させる。", c -> {
			announce(c, "ライトニングセイバー", T, L);
			Vec3 o = c.origin, end = o.add(c.dir.scale(20));
			c.later(6, () -> {
				Vfx.lightningLine(c.level, o, end, 0xFFFFFF, 3);
				Vfx.beam(c.level, o, end, L);
				Vfx.flash(c.level, end, 40, 0xFFFFFF, 4);
				sound(c, o, SoundEvents.TRIDENT_THUNDER.value(), 1.5f, 1.8f);
				for (LivingEntity t : c.beam(20, 1.6, 1.5f)) {
					eff(t, ModEffects.STUN, 2, 0);
				}
				c.dash(1.6, 0.1);
			});
		});

		// 雷 × 呪い: 呪いの雷が刻印となって残る。
		reg(T, C, "ヘクスサンダー", "呪いを帯びた雷が敵を撃ち、複数の呪いを一度に刻み込む。", c -> {
			announce(c, "ヘクスサンダー", T, C);
			Vec3 p = c.aimPoint(16);
			for (LivingEntity t : c.enemiesNear(p, 8)) {
				c.later(c.level.getRandom().nextInt(14), () -> {
					if (t.isAlive()) {
						c.lightning(t.position());
						c.hit(t, 0.7f);
						eff(t, ModEffects.SHOCK, 4, 0);
						eff(t, MobEffects.WEAKNESS, 8, 1);
						eff(t, ModEffects.ARMOR_BREAK, 8, 1);
						eff(t, ModEffects.INACCURACY, 8, 0);
						Vfx.burstOut(c.level, t.position().add(0, 1, 0), Vfx.dust(0x7A1E5A, 1.5f), 14, 0.2);
					}
				});
			}
			c.lightning(p);
		});

		// 炎 × 水: 水蒸気爆発。
		reg(F, Wa, "スチームカタストロフ", "炎と水が衝突して水蒸気爆発を起こし、視界を奪う濃い蒸気を残す。", c -> {
			announce(c, "スチームカタストロフ", F, Wa);
			Vec3 p = c.aimPoint(14);
			Vfx.sound(c.level, p, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.8f);
			FxDisplay.orb(c.level, p.add(0, 1.2, 0), false, 2, 10, 14);
			c.later(12, () -> {
				big(c, p, F, 7, 0xFFFFFF);
				Vfx.burstOut(c.level, p.add(0, 1, 0), ParticleTypes.CLOUD, 60, 0.7);
				c.areaAt(p, 7, 1.2f).forEach(t -> {
					eff(t, MobEffects.BLINDNESS, 6, 0);
					eff(t, ModEffects.BURN, 4, 1);
				});
				Terrain.douse(c.level, p, 7);
				c.field(p, 6, 6, 10, (cc, l) -> {
					cc.particles(ParticleTypes.CLOUD, p.x, p.y + 1, p.z, 20, 3, 1, 3, 0.02);
					l.forEach(t -> {
						cc.hit(t, 0.08f);
						eff(t, MobEffects.BLINDNESS, 2, 0);
					});
				});
			});
		});

		// 炎 × 重力: 流星群。
		reg(F, G, "メテオライトフォール", "引力が降らせる流星群。落下地点の敵は引き寄せられる。", c -> {
			announce(c, "メテオライトフォール", F, G);
			Vec3 p = c.aimPoint(18);
			FxDisplay.dangerCircle(c.level, p, 7, 20);
			barrage(c, p, 7, 10, 5, 0.55f, 3, q -> {
				Cinema.meteorImpact(c, q, 0.55f);
				c.enemiesNear(q, 6).forEach(t -> Ctx.pull(t, q, 0.5));
			});
		});

		// 炎 × 闇: 冥界の黒炎。
		reg(F, D, "ヘルフレイム", "冥界の黒い炎が床に広がり、燃える敵を恐怖で錯乱させる。", c -> {
			announce(c, "ヘルフレイム", F, D);
			Vec3 p = c.aimPoint(14);
			c.areaAt(p, 6, 0.5f);
			big(c, p, D, 6, 0x301040);
			c.field(p, 6, 8, 8, (cc, l) -> {
				cc.particles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.3, p.z, 24, 3, 0.3, 3, 0.03);
				cc.particles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 1, p.z, 8, 3, 0.8, 3, 0.02);
				l.forEach(t -> {
					cc.hit(t, 0.17f);
					eff(t, ModEffects.BURN, 3, 2);
					eff(t, ModEffects.FEAR, 3, 0);
				});
			});
		});

		// 炎 × 光: 太陽の炎。
		reg(F, L, "ソーラーフレア", "頭上に小さな太陽を生み出し、炸裂させる。敵は焼かれ、味方は癒やされる。", c -> {
			announce(c, "ソーラーフレア", F, L);
			Vec3 p = c.aimPoint(14);
			Vec3 sun = p.add(0, 6, 0);
			FxDisplay.orb(c.level, sun, false, 3.5, 30, 36);
			Tasks.repeat(0, 2, 18, i -> Vfx.rays(c.level, sun, 0xFFC040, 8, 4, 6));
			sound(c, sun, ModSounds.MAX_CHARGE, 1.4f, 1.2f);
			c.later(36, () -> {
				big(c, p, F, 9, 0xFFE090);
				Vfx.burstOut(c.level, sun, ParticleTypes.FLAME, 80, 0.9);
				c.areaAt(p, 9, 1.5f).forEach(t -> {
					eff(t, ModEffects.BURN, 6, 2);
					eff(t, MobEffects.BLINDNESS, 3, 0);
				});
				for (LivingEntity a : c.alliesNear(p, 9)) {
					c.heal(a, c.healAmount(0.4f));
				}
				Terrain.melt(c.level, p, 6, true);
			});
		});

		// 炎 × 呪い: 呪われた業火。
		reg(F, C, "カースドインフェルノ", "呪いの炎が敵を焼き続け、燃えている間は回復も防御も蝕む。", c -> {
			announce(c, "カースドインフェルノ", F, C);
			Vec3 p = c.aimPoint(14);
			big(c, p, C, 5, 0xA02070);
			c.field(p, 5.5, 8, 10, (cc, l) -> {
				cc.particles(ParticleTypes.FLAME, p.x, p.y + 0.3, p.z, 16, 2.6, 0.3, 2.6, 0.04);
				cc.particles(Vfx.dust(0x7A1E5A, 1.5f), p.x, p.y + 1, p.z, 16, 2.6, 0.8, 2.6, 0);
				l.forEach(t -> {
					cc.hit(t, 0.14f);
					eff(t, ModEffects.BURN, 4, 1);
					eff(t, ModEffects.ARMOR_BREAK, 4, 1);
					eff(t, MobEffects.WEAKNESS, 4, 0);
				});
			});
		});

		// 水 × 重力: 大渦。
		reg(Wa, G, "メイルストロム", "巨大な渦潮が敵を飲み込んで回し、中心で溺れさせる。", c -> {
			announce(c, "メイルストロム", Wa, G);
			Vec3 p = c.aimPoint(14);
			Terrain.douse(c.level, p, 6);
			Tasks.repeat(0, 2, 40, i -> {
				Vfx.vortex(c.level, p.add(0, 0.5, 0), 6, ParticleTypes.BUBBLE_POP, 30);
				Vfx.vortex(c.level, p.add(0, 1.5, 0), 4, ParticleTypes.SPLASH, 20);
				for (LivingEntity t : c.enemiesNear(p, 7)) {
					Vec3 rel = t.position().subtract(p);
					Vec3 tang = new Vec3(-rel.z, 0, rel.x).normalize().scale(0.35);
					Ctx.pull(t, p, 0.35);
					t.setDeltaMovement(t.getDeltaMovement().add(tang));
					t.syncVelocity = true;
					if (i % 5 == 0) {
						c.hit(t, 0.1f);
						eff(t, ModEffects.DROWN, 3, 0);
					}
				}
			});
			c.later(82, () -> c.areaAt(p, 5, 0.6f));
		});

		// 水 × 闇: 深海の闇。
		reg(Wa, D, "アビサルトレンチ", "光の届かない深海を足元に開き、敵の動きと視界を飲み込む。", c -> {
			announce(c, "アビサルトレンチ", Wa, D);
			Vec3 p = c.aimPoint(14);
			big(c, p, D, 7, 0x102060);
			c.field(p, 6.5, 8, 10, (cc, l) -> {
				cc.particles(ParticleTypes.DRIPPING_WATER, p.x, p.y + 2, p.z, 24, 3, 1, 3, 0);
				cc.particles(ParticleTypes.SQUID_INK, p.x, p.y + 0.5, p.z, 12, 3, 0.4, 3, 0.02);
				l.forEach(t -> {
					cc.hit(t, 0.13f);
					eff(t, MobEffects.SLOWNESS, 3, 3);
					eff(t, MobEffects.BLINDNESS, 3, 0);
					eff(t, ModEffects.DROWN, 2, 0);
					eff(t, ModEffects.WET, 6, 0);
				});
			});
		});

		// 水 × 光: 聖なる雨。
		reg(Wa, L, "ホーリーレイン", "祝福の雨が降り注ぎ、味方を癒やして状態異常を洗い流し、敵を打つ。", c -> {
			announce(c, "ホーリーレイン", Wa, L);
			Vec3 p = c.caster.position();
			Terrain.douse(c.level, p, 8);
			Tasks.repeat(0, 2, 60, i -> {
				Vfx.near(c.level, ParticleTypes.FALLING_WATER, p.x, p.y + 8, p.z, 25, 8, 0.5, 8, 0);
				Vfx.near(c.level, ParticleTypes.END_ROD, p.x, p.y + 4, p.z, 3, 7, 3, 7, 0.01);
			});
			c.field(p, 8, 6, 20, (cc, l) -> {
				for (LivingEntity a : cc.alliesNear(p, 8)) {
					cc.heal(a, cc.healAmount(0.2f));
					Ctx.cleanse(a);
				}
				l.forEach(t -> {
					cc.hit(t, 0.2f);
					eff(t, ModEffects.WET, 6, 0);
				});
			});
		});

		// 水 × 呪い: 毒沼。
		reg(Wa, C, "ヴェノムマーシュ", "足元を毒の沼に変え、踏み込んだ敵を沈めて呪い続ける。", c -> {
			announce(c, "ヴェノムマーシュ", Wa, C);
			Vec3 p = c.aimPoint(14);
			big(c, p, C, 6, 0x60A040);
			c.field(p, 6, 10, 10, (cc, l) -> {
				cc.particles(ParticleTypes.BUBBLE_POP, p.x, p.y + 0.3, p.z, 20, 3, 0.2, 3, 0.02);
				cc.particles(Vfx.dust(0x6A8A2A, 1.4f), p.x, p.y + 0.6, p.z, 14, 3, 0.3, 3, 0);
				l.forEach(t -> {
					cc.hit(t, 0.09f);
					eff(t, MobEffects.POISON, 4, 2);
					eff(t, MobEffects.SLOWNESS, 3, 2);
					eff(t, ModEffects.WET, 6, 0);
					t.setDeltaMovement(t.getDeltaMovement().multiply(1, 0.4, 1));
				});
			});
		});

		// 重力 × 闇: ブラックホール。
		reg(G, D, "イベントホライズン", "あらゆるものを呑み込む黒い穴。吸い込みの果てに、すべてを潰して消し飛ばす。", c -> {
			announce(c, "イベントホライズン", G, D);
			Vec3 p = c.aimPoint(16);
			Cinema.singularity(c, p, 80);
			Cinema.abyss(c, p, 5);
			Tasks.repeat(0, 2, 40, i -> c.enemiesNear(p, 12).forEach(t -> Ctx.pull(t, p, 0.8)));
			c.later(82, () -> {
				c.areaAt(p, 7, 1.8f);
				big(c, p, D, 8, 0x000000);
			});
		});

		// 重力 × 光: 星の落下。
		reg(G, L, "スターフォール", "天から星そのものを引きずり下ろし、一点に叩きつける。", c -> {
			announce(c, "スターフォール", G, L);
			Vec3 p = c.aimPoint(18);
			FxDisplay.dangerCircle(c.level, p, 6, 40);
			Vec3 top = p.add(0, 28, 0);
			FxDisplay.orb(c.level, top, false, 4, 10, 44);
			Tasks.repeat(0, 1, 40, i -> {
				Vec3 q = top.add(0, -28.0 * i / 40.0, 0);
				Vfx.near(c.level, ParticleTypes.END_ROD, q.x, q.y, q.z, 8, 0.8, 0.8, 0.8, 0.05);
				Vfx.near(c.level, ParticleTypes.FIREWORK, q.x, q.y + 1, q.z, 4, 0.5, 2, 0.5, 0.05);
			});
			c.later(42, () -> {
				big(c, p, L, 10, 0xFFFFFF);
				Vfx.pillar(c.level, p, 14, 4, ParticleTypes.END_ROD, 10);
				Vfx.debris(c.level, p, 5, 60);
				c.areaAt(p, 6, 2.2f).forEach(t -> eff(t, ModEffects.KNOCKDOWN, 3, 0));
			});
		});

		// 重力 × 呪い: 重圧の枷。
		reg(G, C, "グラヴィティシャックル", "呪われた重力の枷が敵を地面に縫い止め、動くほど重くなる。", c -> {
			announce(c, "グラヴィティシャックル", G, C);
			Vec3 p = c.aimPoint(14);
			big(c, p, G, 7, 0x8A5CD6);
			for (LivingEntity t : c.enemiesNear(p, 7)) {
				c.hit(t, 0.6f);
				eff(t, ModEffects.BIND, 5, 0);
				eff(t, MobEffects.SLOWNESS, 9, 3);
				eff(t, ModEffects.ARMOR_BREAK, 9, 1);
				eff(t, MobEffects.WEAKNESS, 9, 1);
				Tasks.repeat(0, 10, 8, i -> {
					if (t.isAlive()) {
						Vfx.ring(c.level, t.position().add(0, 0.1, 0), 0.9, Vfx.dust(0x7A1E5A, 1.2f), 10);
						t.setDeltaMovement(t.getDeltaMovement().multiply(1, 1, 1).add(0, -0.4, 0));
						c.hit(t, 0.08f);
					}
				});
			}
		});

		// 闇 × 光: 日蝕。
		reg(D, L, "トワイライトエクリプス", "光と闇が交互に膨らむ日蝕の波。敵は光と闇の両方に裁かれる。", c -> {
			announce(c, "トワイライトエクリプス", D, L);
			Vec3 p = c.aimPoint(12);
			FxDisplay.orb(c.level, p.add(0, 5, 0), true, 3, 20, 60);
			FxDisplay.orb(c.level, p.add(0, 5.2, 0), false, 2.2, 20, 60);
			for (int i = 0; i < 5; i++) {
				int k = i;
				c.later(14 + i * 9, () -> {
					boolean light = k % 2 == 0;
					double r = 3 + k * 1.3;
					Vfx.wave(c.level, p, r, 8, light ? ParticleTypes.END_ROD : ParticleTypes.SQUID_INK);
					FxDisplay.ring(c.level, p, light ? Element.LIGHT : Element.DARK, r, 8);
					c.areaAt(p, r, 0.3f).forEach(t -> eff(t, ModEffects.STAGGER, 3, 0));
					sound(c, p, light ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 1f, 1.2f);
				});
			}
			c.later(64, () -> {
				big(c, p, D, 9, 0xFFFFFF);
				c.areaAt(p, 8, 1.1f);
			});
		});

		// 闇 × 呪い: 死の宣告。
		reg(D, C, "デスレクイエム", "死の鎮魂歌。影の分身が敵を囲んで斬りつけ、最後に宣告の呪いを刻む。", c -> {
			announce(c, "デスレクイエム", D, C);
			Vec3 p = c.aimPoint(14);
			List<LivingEntity> list = c.enemiesNear(p, 8);
			Tasks.repeat(0, 3, 12, i -> {
				for (LivingEntity t : list) {
					if (!t.isAlive()) {
						continue;
					}
					double a = i * 1.3 + t.getId();
					Vec3 from = t.position().add(Math.cos(a) * 2.5, 1, Math.sin(a) * 2.5);
					Vfx.line(c.level, from, t.position().add(0, 1, 0), ParticleTypes.SOUL, 0.3);
					Vfx.near(c.level, ParticleTypes.SQUID_INK, from.x, from.y, from.z, 6, 0.3, 0.5, 0.3, 0.02);
					c.hit(t, 0.1f);
				}
				sound(c, p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 0.5f);
			});
			c.later(40, () -> {
				for (LivingEntity t : list) {
					if (t.isAlive()) {
						eff(t, ModEffects.DOOM, 6, 0);
						eff(t, ModEffects.FEAR, 4, 0);
						Vfx.pillar(c.level, t.position(), 4, 0.8, ParticleTypes.SOUL_FIRE_FLAME, 6);
					}
				}
				big(c, p, Element.CURSE, 8, 0x601040);
			});
		});

		// 光 × 呪い: 浄罪の裁き。
		reg(L, C, "パージャトリー", "味方の呪いを焼き清め、その分だけ敵を光の炎で裁く。", c -> {
			announce(c, "パージャトリー", L, C);
			Vec3 p = c.caster.position();
			int[] cleansed = { 0 };
			for (LivingEntity a : c.alliesNear(p, 10)) {
				for (var i : a.getActiveEffects()) {
					if (i.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
						cleansed[0]++;
					}
				}
				Ctx.cleanse(a);
				a.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.RESISTANCE, 160, 1));
				Vfx.pillar(c.level, a.position(), 3, 0.6, ParticleTypes.END_ROD, 4);
			}
			float bonus = 1f + Math.min(1f, cleansed[0] * 0.15f);
			big(c, p, L, 10, 0xFFF6C8);
			c.areaAt(p, 10, 1.1f * bonus).forEach(t -> {
				eff(t, ModEffects.BURN, 5, 1);
				eff(t, ModEffects.SILENCE, 4, 0);
				Vfx.pillar(c.level, t.position(), 5, 0.9, ParticleTypes.END_ROD, 5);
			});
		});
	}

	// ---------------------------------------------------------------------------------------------
	// 三重融合: 最大魔法 3 つ(ベース+サブ 2)。56 通りの組み合わせそれぞれに固有の名前。
	// 3 つの属性から 2 つずつ取った二重融合を順に放ち(それぞれ六割の威力)、最後に 3 属性が一点に収束して炸裂する。
	// 収束の炸裂には、3 つの属性それぞれの持ち味(感電・火傷・濡れ・拘束・盲目・脱力・回復・吹き飛ばし)が乗る。

	private static final String[] TRIPLE_NAMES = { "クリムゾンテンペスト", "ラグナネプチューン", "エーテルヴォルテックス", "ミッドナイトスコール", "ゼウスアーク",
			"マガツイカヅチ", "ミストラルバースト", "ミーティアサイクロン", "ヘルウィンドダンス", "ソーラーテンペスト", "カラミティフレア", "ノヴァメイルストロム", "アビスタイフーン",
			"ホーリーモンスーン", "ヴェノムタイフーン", "ヴォイドウィスパー", "コスモスブリーズ", "グラビティプレイグ", "トワイライトゲイル", "デッドウィンド", "ピュリファイウィンド",
			"トライデントストーム", "スーパーノヴァ", "ブラックプラズマ", "ホーリープラズマ", "ディザスタープラズマ", "マグネティックタイド", "アビサルサンダー", "ジャッジメントレイン",
			"ヴェノムストーム", "ダークマグネトロン", "ライトニングゲート", "ヘックスマグネット", "トワイライトボルト", "ドゥームサンダー", "ピュリファイボルト", "ギガスチーム",
			"ブラックスチーム", "サンクチュアリミスト", "カースドスチーム", "ダークメテオ", "ホーリーメテオ", "カースメテオ", "エクリプスフレア", "インフェルノカース",
			"ピュリファイインフェルノ", "アビスブラックホール", "ホーリーメイルストロム", "カースドトレンチ", "ムーンライトタイド", "リヴァイアサンカース", "サクラメントレイン",
			"コズミックエクリプス", "ネクロコラプス", "ジャッジメントグラビティ", "トリニティエクリプス" };

	private static void signature(Ctx c, Element e, LivingEntity t) {
		switch (e) {
			case WIND -> Ctx.push(t, c.caster.position(), 1.2, 0.4);
			case THUNDER -> eff(t, ModEffects.SHOCK, 4, 1);
			case FIRE -> eff(t, ModEffects.BURN, 6, 2);
			case WATER -> {
				eff(t, ModEffects.WET, 8, 0);
				eff(t, MobEffects.SLOWNESS, 4, 1);
			}
			case GRAVITY -> {
				eff(t, ModEffects.BIND, 3, 0);
				Ctx.pull(t, c.caster.position().add(c.dir.scale(8)), 0.5);
			}
			case DARK -> eff(t, MobEffects.BLINDNESS, 5, 0);
			case LIGHT -> eff(t, ModEffects.STAGGER, 3, 0);
			case CURSE -> {
				eff(t, MobEffects.WEAKNESS, 8, 1);
				eff(t, ModEffects.ARMOR_BREAK, 8, 1);
			}
		}
	}

	private static void triple(Element a, Element b, Element c, String name) {
		Fusion f1 = ALL.get(key(a, b)), f2 = ALL.get(key(b, c)), f3 = ALL.get(key(a, c));
		String desc = "『" + f1.name() + "』『" + f2.name() + "』『" + f3.name() + "』を三連で放ち、最後に " + a.ja + "・" + b.ja + "・" + c.ja
				+ " が一点に収束して炸裂する。";
		Spell.Form[] seq = { f1.form(), f2.form(), f3.form() };
		TRIPLE.put(key3(a, b, c), new Fusion(name, desc, ctx -> {
			if (ctx.caster instanceof net.minecraft.server.level.ServerPlayer sp) {
				sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("◆◆ 三重融合魔法『" + name + "』").withStyle(net.minecraft.ChatFormatting.GOLD));
			}
			for (Element e : new Element[] { a, b, c }) {
				FxDisplay.circle(ctx.level, ctx.caster.position().add(0, 0.05, 0), e, 2.5 + 0.8 * e.ordinal() % 3, 150, e.ordinal() % 2 == 0 ? 3f : -3f);
			}
			Vfx.sound(ctx.level, ctx.caster.position(), ModSounds.MAX_CHARGE, 1.4f, 0.8f);
			for (int i = 0; i < 3; i++) {
				int k = i;
				ctx.later(i * 45, () -> {
					if (!ctx.caster.isAlive()) {
						return;
					}
					Ctx sub = new Ctx(ctx.caster, ctx.spell, ctx.power * 0.6f);
					sub.durMult = ctx.durMult;
					quiet = true;
					try {
						seq[k].cast(sub);
					} finally {
						quiet = false;
					}
				});
			}
			ctx.later(150, () -> {
				if (!ctx.caster.isAlive()) {
					return;
				}
				Ctx fin = new Ctx(ctx.caster, ctx.spell, ctx.power);
				fin.durMult = ctx.durMult;
				Vec3 p = fin.aimPoint(16);
				Vfx.sound(ctx.level, p, ModSounds.MAX_RELEASE, 2f, 0.7f);
				int i = 0;
				for (Element e : new Element[] { a, b, c }) {
					FxDisplay.ring(ctx.level, p, e, 5 + 2.5 * i, 12);
					Vfx.pillar(ctx.level, p.add(Math.cos(i * 2.1) * 2, 0, Math.sin(i * 2.1) * 2), 16, 1.2, Fx.of(e), 8);
					Vfx.rays(ctx.level, p.add(0, 1, 0), e.color, 10, 7, 16);
					i++;
				}
				Vfx.flash(ctx.level, p, 60, 0xFFFFFF, 10);
				Vfx.shake(ctx.level, p, 48, 1.6f, 20);
				for (LivingEntity t : fin.areaAt(p, 9, 1.8f)) {
					for (Element e : new Element[] { a, b, c }) {
						signature(fin, e, t);
					}
				}
				if (a == Element.LIGHT || b == Element.LIGHT || c == Element.LIGHT) {
					for (LivingEntity al : fin.alliesNear(p, 12)) {
						fin.heal(al, fin.healAmount(0.4f));
					}
				}
			});
		}));
	}

	static {
		Element[] el = Element.values();
		int n = 0;
		for (int i = 0; i < 8; i++) {
			for (int j = i + 1; j < 8; j++) {
				for (int k = j + 1; k < 8; k++) {
					triple(el[i], el[j], el[k], TRIPLE_NAMES[n++]);
				}
			}
		}
	}
}
