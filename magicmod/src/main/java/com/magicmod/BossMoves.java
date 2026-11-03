package com.magicmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 予兆つきの攻撃パターン。地面に赤い危険範囲が出て、中が満ちた瞬間に攻撃が来る(円・ドーナツ・直線・十字・連続落下・突進)。
 * 回避ステップ(ジャスト回避の報酬つき)で避けるのが前提の作り。予兆の長さは settings.json の telegraphTimeMultiplier で変えられる。
 * 1 体が同時に出せる予兆は 1 つだけ。第 2 形態は予兆が 2 割短くなる。
 */
public final class BossMoves {

	private static final Map<UUID, Long> BUSY = new HashMap<>();

	private BossMoves() {
	}

	private static int windup(Mob m, double sec) {
		double k = Settings.get().telegraphTimeMultiplier;
		if (m.getHealth() <= m.getMaxHealth() * 0.5f && Enemies.tierOf(m) != Enemies.Tier.MINION) {
			k *= 0.8;
		}
		return Math.max(6, (int) Math.round(sec * 20 * k));
	}

	/** 予兆を始められるなら true(同時に 1 つだけ)。 */
	private static boolean begin(Mob m, ServerLevel l, int ticks, boolean hold) {
		long now = l.getGameTime();
		if (BUSY.getOrDefault(m.getUUID(), 0L) > now) {
			return false;
		}
		BUSY.put(m.getUUID(), now + ticks + 8);
		if (hold) {
			m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks + 4, 6, false, false));
			m.getNavigation().stop();
		}
		l.playSound(null, m.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), m.getSoundSource(), 1.4f, 0.5f);
		return true;
	}

	private static Ctx ctx(Mob m, Element el) {
		Spell sp = Spells.get(el, 1);
		return new Ctx(m, sp, Calc.spellPower(m, sp, 1, ItemStack.EMPTY));
	}

	private static void ticks(ServerLevel l, int windup, java.util.function.IntConsumer each) {
		Tasks.repeat(0, 4, Math.max(1, windup / 4), each);
	}

	/** 自分の足元から円(近づいていると当たる)。 */
	static Enemies.Act slam(Element el, double r, double sec, float mult) {
		return (m, l, t) -> {
			int w = windup(m, sec);
			if (!begin(m, l, w, true)) {
				return;
			}
			Vec3 c = m.position();
			FxDisplay.dangerCircle(l, c, r, w);
			ticks(l, w, i -> Vfx.ring(l, c.add(0, 0.2, 0), r, Vfx.dust(0xFF4040, 1f), (int) (r * 8)));
			Tasks.later(w, () -> {
				if (m.isAlive()) {
					ctx(m, el).areaAt(c, r, mult);
					Vfx.shake(l, c, r * 5, 0.6f, 6);
				}
			});
		};
	}

	/** 相手のいる場所に円(動けば避けられる)。 */
	static Enemies.Act strike(Element el, double r, double sec, float mult) {
		return (m, l, t) -> {
			if (t == null) {
				return;
			}
			int w = windup(m, sec);
			if (!begin(m, l, w, false)) {
				return;
			}
			Vec3 c = t.position();
			FxDisplay.dangerCircle(l, c, r, w);
			ticks(l, w, i -> Vfx.ring(l, c.add(0, 0.2, 0), r, Vfx.dust(0xFF4040, 1f), (int) (r * 8)));
			Tasks.later(w, () -> {
				if (m.isAlive()) {
					Vfx.pillar(l, c, 10, r * 0.5, Vfx.fade(0xFFFFFF, Vfx.palette(el)[0], 1.5f), 2);
					ctx(m, el).areaAt(c, r, mult);
					Vfx.shake(l, c, 20, 0.5f, 5);
				}
			});
		};
	}

	/** 相手の周りに n 個の円が時間差で落ちる。 */
	static Enemies.Act rain(Element el, int n, double r, double sec, float mult) {
		return (m, l, t) -> {
			if (t == null) {
				return;
			}
			int w = windup(m, sec);
			if (!begin(m, l, w + n * 4, false)) {
				return;
			}
			RandomSource rnd = l.getRandom();
			for (int i = 0; i < n; i++) {
				final int k = i;
				Vec3 c = i == 0 ? t.position() : t.position().add(rnd.nextGaussian() * 4, 0, rnd.nextGaussian() * 4);
				Tasks.later(k * 4, () -> {
					if (!m.isAlive()) {
						return;
					}
					FxDisplay.dangerCircle(l, c, r, w);
					Tasks.later(w, () -> {
						if (m.isAlive()) {
							Vfx.pillar(l, c, 8, r * 0.4, Vfx.fade(0xFFFFFF, Vfx.palette(el)[0], 1.4f), 2);
							ctx(m, el).areaAt(c, r, mult);
						}
					});
				});
			}
		};
	}

	/** ドーナツ: 自分の近く(inner の内側)だけが安全。 */
	static Enemies.Act donut(Element el, double inner, double outer, double sec, float mult) {
		return (m, l, t) -> {
			int w = windup(m, sec);
			if (!begin(m, l, w, true)) {
				return;
			}
			Vec3 c = m.position();
			FxDisplay.dangerDonut(l, c, inner, outer, w);
			ticks(l, w, i -> Vfx.ring(l, c.add(0, 0.2, 0), outer, Vfx.dust(0xFF4040, 1f), (int) (outer * 6)));
			Tasks.later(w, () -> {
				if (!m.isAlive()) {
					return;
				}
				Ctx cx = ctx(m, el);
				for (LivingEntity x : cx.enemiesNear(c, outer)) {
					if (x.position().distanceTo(c) > inner) {
						cx.hit(x, mult);
					}
				}
				Vfx.shockwave(l, c, outer, el);
				Vfx.shake(l, c, outer * 4, 0.9f, 8);
			});
		};
	}

	private static void hitRect(Mob m, Element el, Vec3 from, Vec3 dir, double len, double width, float mult) {
		ServerLevel l = (ServerLevel) m.level();
		Vec3 f = new Vec3(dir.x, 0, dir.z).normalize();
		Ctx cx = ctx(m, el);
		Vec3 mid = from.add(f.scale(len / 2));
		for (LivingEntity x : cx.enemiesNear(mid, len / 2 + width + 1)) {
			Vec3 rel = x.position().subtract(from);
			double along = rel.x * f.x + rel.z * f.z;
			double perp = Math.abs(rel.x * -f.z + rel.z * f.x);
			if (along >= -0.5 && along <= len + 0.5 && perp <= width / 2 + x.getBbWidth() / 2) {
				cx.hit(x, mult);
			}
		}
		for (double s = 0; s <= len; s += 1.5) {
			Vec3 q = from.add(f.scale(s));
			Vfx.impact(l, q.add(0, 0.5, 0), el, 0.8f);
		}
		Vfx.beam(l, from.add(0, 1, 0), from.add(f.scale(len)).add(0, 1, 0), el);
	}

	/** 直線(dash なら、その線に沿って突進して反対側へ抜ける)。 */
	static Enemies.Act line(Element el, double len, double width, double sec, float mult, boolean dash) {
		return (m, l, t) -> {
			if (t == null) {
				return;
			}
			int w = windup(m, sec);
			if (!begin(m, l, w, true)) {
				return;
			}
			Vec3 from = m.position();
			Vec3 dir = t.position().subtract(from);
			dir = new Vec3(dir.x, 0, dir.z);
			if (dir.lengthSqr() < 0.01) {
				return;
			}
			final Vec3 d = dir.normalize();
			FxDisplay.dangerRect(l, from, d, len, width, w);
			Tasks.later(w, () -> {
				if (!m.isAlive()) {
					return;
				}
				hitRect(m, el, from, d, len, width, mult);
				if (dash) {
					Vec3 end = from.add(d.scale(len - 1));
					Vfx.line(l, from.add(0, 1, 0), end.add(0, 1, 0), Vfx.fade(0xFFFFFF, Vfx.palette(el)[0], 2f), 0.3);
					m.teleportTo(end.x, m.getY(), end.z);
				}
				Vfx.shake(l, from, 24, 0.5f, 5);
			});
		};
	}

	/** 十字(2 本の直線が同時に)。 */
	static Enemies.Act cross(Element el, double len, double width, double sec, float mult) {
		return (m, l, t) -> {
			int w = windup(m, sec);
			if (!begin(m, l, w, true)) {
				return;
			}
			Vec3 c = m.position();
			double base = t == null ? 0 : Math.atan2(t.getZ() - c.z, t.getX() - c.x);
			Vec3[] dirs = new Vec3[4];
			for (int i = 0; i < 4; i++) {
				double a = base + i * Math.PI / 2;
				dirs[i] = new Vec3(Math.cos(a), 0, Math.sin(a));
				FxDisplay.dangerRect(l, c, dirs[i], len, width, w);
			}
			Tasks.later(w, () -> {
				if (m.isAlive()) {
					for (Vec3 d : dirs) {
						hitRect(m, el, c, d, len, width, mult);
					}
					Vfx.shake(l, c, 28, 0.7f, 8);
				}
			});
		};
	}

	/** まとめて登録する(Enemies.init から)。 */
	static void register() {
		Element W = Element.WIND, T = Element.THUNDER, F = Element.FIRE, A = Element.WATER, G = Element.GRAVITY, D = Element.DARK,
				L = Element.LIGHT, C = Element.CURSE;
		// 雑魚の重い一撃
		Enemies.more("magma_hound", Enemies.a(170, slam(F, 2.5, 0.9, 1.6f)));
		Enemies.more("gravity_mimic", Enemies.a(190, slam(G, 3, 1.0, 1.5f)));
		// 中ボス
		Enemies.more("gale_harpy", Enemies.a(110, strike(W, 3, 1.0, 2.2f)), Enemies.a(210, line(W, 12, 2, 1.1, 1.6f, true)),
				Enemies.p2(260, rain(W, 4, 2.5, 1.2, 2.2f)));
		Enemies.more("thunder_wolf_alpha", Enemies.a(130, line(T, 10, 2, 0.9, 2.0f, true)), Enemies.a(200, slam(T, 5, 1.2, 2.4f)),
				Enemies.p2(280, cross(T, 10, 1.5, 1.3, 2.0f)));
		Enemies.more("salamander_king", Enemies.a(150, slam(F, 4.5, 1.2, 2.5f)), Enemies.a(110, strike(F, 3, 1.0, 2.2f)),
				Enemies.p2(260, donut(F, 3, 9, 1.5, 2.5f)));
		Enemies.more("leviathan_juvenile", Enemies.a(180, donut(A, 3, 8, 1.4, 2.4f)), Enemies.a(240, rain(A, 3, 3, 1.2, 2.0f)),
				Enemies.p2(200, line(A, 14, 3, 1.4, 2.4f, false)));
		Enemies.more("orbit_golem", Enemies.a(170, slam(G, 5, 1.3, 2.6f)), Enemies.a(230, rain(G, 4, 2.5, 1.2, 2.2f)),
				Enemies.p2(120, strike(G, 4, 1.0, 2.6f)));
		Enemies.more("abyss_executor", Enemies.a(140, line(D, 10, 1.8, 0.9, 2.2f, true)), Enemies.a(100, strike(D, 3, 1.0, 2.0f)),
				Enemies.p2(260, cross(D, 10, 1.5, 1.2, 2.2f)));
		Enemies.more("holy_knight_remnant", Enemies.a(120, line(L, 12, 2, 0.9, 2.2f, true)), Enemies.a(190, slam(L, 4, 1.2, 2.4f)),
				Enemies.p2(250, rain(L, 5, 2.2, 1.1, 2.0f)));
		Enemies.more("curse_witch", Enemies.a(150, rain(C, 3, 2.5, 1.2, 1.8f)), Enemies.p2(240, donut(C, 2.5, 8, 1.4, 2.2f)));
		// 属性超越ボス
		Enemies.more("zepharion", Enemies.p1(100, strike(W, 4, 1.0, 2.5f)), Enemies.p1(220, rain(W, 6, 3, 1.2, 2.5f)),
				Enemies.p2(260, donut(W, 3, 12, 1.6, 3.0f)), Enemies.p2(340, cross(W, 16, 2.5, 1.3, 2.8f)));
		Enemies.more("gardonius", Enemies.a(200, rain(T, 6, 2.5, 1.1, 2.6f)), Enemies.a(150, line(T, 14, 2, 0.8, 2.6f, true)),
				Enemies.p2(300, cross(T, 14, 2, 1.2, 3.0f)), Enemies.p2(240, slam(T, 6, 1.2, 3.0f)));
		Enemies.more("igdrum", Enemies.a(200, slam(F, 6, 1.3, 3.0f)), Enemies.a(180, line(F, 16, 3, 1.5, 3.0f, false)),
				Enemies.p2(260, rain(F, 8, 3, 1.2, 2.8f)), Enemies.p2(330, donut(F, 4, 12, 1.6, 3.0f)));
		Enemies.more("melusine", Enemies.a(220, donut(A, 4, 11, 1.6, 3.0f)), Enemies.a(240, rain(A, 6, 3, 1.2, 2.6f)),
				Enemies.p2(200, line(A, 18, 3.5, 1.5, 3.0f, false)), Enemies.p2(300, slam(A, 7, 1.4, 3.2f)));
		Enemies.more("singularis", Enemies.a(210, slam(G, 6, 1.4, 3.0f)), Enemies.a(140, strike(G, 5, 1.1, 2.8f)),
				Enemies.p2(250, rain(G, 7, 3, 1.2, 2.8f)), Enemies.p2(330, donut(G, 3, 12, 1.6, 3.0f)));
		Enemies.more("necrovant", Enemies.a(220, cross(D, 14, 2, 1.3, 2.8f)), Enemies.a(120, strike(D, 4, 1.0, 2.6f)),
				Enemies.p2(260, rain(D, 8, 2.8, 1.1, 2.8f)), Enemies.p2(340, slam(D, 7, 1.4, 3.2f)));
		Enemies.more("seraphiel", Enemies.a(150, line(L, 16, 2.5, 0.9, 2.8f, true)), Enemies.a(230, rain(L, 6, 2.5, 1.1, 2.8f)),
				Enemies.p2(280, donut(L, 4, 12, 1.5, 3.0f)), Enemies.p2(340, cross(L, 16, 2.5, 1.3, 3.0f)));
		Enemies.more("morodis", Enemies.a(110, strike(C, 4, 1.0, 2.4f)), Enemies.a(240, donut(C, 3, 10, 1.4, 2.8f)),
				Enemies.p2(220, rain(C, 8, 2.8, 1.1, 2.6f)), Enemies.p2(320, cross(C, 14, 2, 1.3, 3.0f)));
	}

	static void clear() {
		BUSY.clear();
	}

	static List<String> guide() {
		return List.of("赤い範囲が満ちた瞬間に攻撃が来る。[Alt]で回避ステップ(スタミナ20)", "円=範囲外へ / ドーナツ=ボスの足元の安全地帯へ / 直線・十字=線の外へ",
				"ぎりぎりで避けると「ジャスト回避」(スタミナ返還・MP回復・威力+25%)");
	}
}
