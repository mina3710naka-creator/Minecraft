package com.magicmod;

import static com.magicmod.Spell.St.chance;
import static com.magicmod.Spell.St.fire;
import static com.magicmod.Spell.St.freeze;
import static com.magicmod.Spell.St.fx;
import static com.magicmod.Spell.St.stack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 全 104 魔法(8 属性 × 13)の挙動。名前・MP・CT などの数値は balance.json(数値バランス表)から読み、
 * ここでは「どう当たるか」と「何が起きるか」だけを書く。
 */
public final class Spells {

	private static final Map<String, Spell> BY_ID = new LinkedHashMap<>();

	private Spells() {
	}

	public static Collection<Spell> all() {
		return BY_ID.values();
	}

	public static @Nullable Spell get(String id) {
		return BY_ID.get(id);
	}

	public static String id(Element e, int no) {
		return e.id + "_" + no;
	}

	public static @Nullable Spell get(Element e, int no) {
		return BY_ID.get(id(e, no));
	}

	public static List<Spell> of(Element e) {
		List<Spell> l = new ArrayList<>();
		for (int i = 1; i <= 13; i++) {
			Spell s = get(e, i);
			if (s != null) {
				l.add(s);
			}
		}
		return l;
	}

	/** その属性の「低燃費連打型」(魔法型の基本攻撃=魔封じの型に使う)。 */
	public static Spell rapidOf(Element e) {
		for (Spell s : of(e)) {
			if (s.isRapid()) {
				return s;
			}
		}
		return get(e, 12);
	}

	// ---------------------------------------------------------------------------------------------

	private static Spell def(Element e, int no, Spell.Form form, Spell.St... st) {
		Balance.SpellRow row = null;
		for (Balance.SpellRow r : Balance.get().spells) {
			if (r.element() == e && r.no() == no) {
				row = r;
			}
		}
		if (row == null) {
			throw new IllegalStateException("balance.json に " + e.id + " " + no + " がありません");
		}
		Balance.RankRow rank = Balance.get().spellRanks.get(row.rank());
		Spell s = new Spell(id(e, no), row.name(), e, no, row.rank(), row.tag(), row.status(), row.special(),
				rank == null ? 1f : rank.coef(), row.mp(), row.ct(), row.damage());
		s.castTicks = switch (row.rank()) {
			case "middle" -> 4;
			case "upper" -> 10;
			case "max" -> 20;
			case "throw" -> 2;
			default -> 0;
		};
		s.form = form;
		s.statuses.addAll(List.of(st));
		BY_ID.put(s.id, s);
		return s;
	}

	private static Spell.St blind(float sec) {
		return (c, t, d) -> {
			ModEffects.apply(t, MobEffects.BLINDNESS, Math.round(sec * 20 * d), 0);
			ModEffects.apply(t, ModEffects.INACCURACY, Math.round(sec * 20 * d), c.ampBonus());
		};
	}

	private static final List<Holder<MobEffect>> CURSES = List.of(MobEffects.WEAKNESS, MobEffects.SLOWNESS, ModEffects.INACCURACY,
			MobEffects.POISON, ModEffects.ARMOR_BREAK, ModEffects.SILENCE, ModEffects.MANA_SEAL);

	private static Spell.St randomCurse(int count, float sec) {
		return (c, t, d) -> {
			List<Holder<MobEffect>> pool = new ArrayList<>(CURSES);
			for (int i = 0; i < count && !pool.isEmpty(); i++) {
				Holder<MobEffect> h = pool.remove(t.getRandom().nextInt(pool.size()));
				ModEffects.apply(t, h, Math.round(sec * 20 * d), c.ampBonus());
			}
		};
	}

	private static final Spell.St WET = fx(ModEffects.WET, 8, 0);

	// ---- 追加効果(合成でサブに選ぶと受け継がれる) ----

	private static Spell.OnHit chain(int count, double range) {
		return (c, t) -> {
			if (c.depth > 0) {
				return;
			}
			c.depth++;
			LivingEntity from = t;
			List<LivingEntity> done = new ArrayList<>(List.of(t));
			float m = 0.7f;
			for (int i = 0; i < count; i++) {
				List<LivingEntity> near = c.enemiesNear(from.position(), range);
				near.removeAll(done);
				if (near.isEmpty()) {
					break;
				}
				LivingEntity next = near.get(0);
				line(c, from.position().add(0, from.getBbHeight() / 2, 0), next.position().add(0, next.getBbHeight() / 2, 0));
				c.hit(next, m);
				done.add(next);
				from = next;
				m *= 0.8f;
			}
			c.depth--;
		};
	}

	private static void line(Ctx c, Vec3 a, Vec3 b) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		for (double s = 0; s < len; s += 0.3) {
			Vec3 q = a.add(d.scale(s / len));
			c.particles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
	}

	private static Spell.OnHit splash(double r, float mult) {
		return (c, t) -> {
			if (c.depth > 0) {
				return;
			}
			c.depth++;
			for (LivingEntity e : c.enemiesNear(t.position(), r)) {
				if (e != t) {
					c.hit(e, mult);
				}
			}
			c.particles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.5, t.getZ(), 1, 0, 0, 0, 0);
			c.depth--;
		};
	}

	private static final Spell.OnHit PUSH = (c, t) -> Ctx.push(t, c.caster.position(), 1.1, 0.25);
	private static final Spell.OnHit PULL_TO_CASTER = (c, t) -> Ctx.pull(t, c.caster.position(), 1.2);
	private static final Spell.OnHit SUCK = (c, t) -> {
		if (c.depth > 0) {
			return;
		}
		for (LivingEntity e : c.enemiesNear(t.position(), 4)) {
			if (e != t) {
				Ctx.pull(e, t.position(), 0.6);
			}
		}
	};
	private static final Spell.OnHit DRAIN = (c, t) -> c.caster.heal(Math.max(0.5f, c.power * 0.35f));
	private static final Spell.OnHit MANA_DRAIN = (c, t) -> Combat.addMp(c.caster, 4 + c.power * 0.3f);
	private static final Spell.OnHit OVERLOAD = (c, t) -> {
		MobEffectInstance cur = t.getEffect(ModEffects.OVERCHARGE);
		int amp = cur == null ? 0 : cur.getAmplifier() + 1;
		if (amp >= 4 && c.depth == 0) {
			t.removeEffect(ModEffects.OVERCHARGE);
			c.depth++;
			c.areaAt(t.position().add(0, 0.5, 0), 3, 2.5f);
			c.particles(ParticleTypes.EXPLOSION_EMITTER, t.getX(), t.getY() + 0.5, t.getZ(), 1, 0, 0, 0, 0);
			c.soundAt(t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1.2f);
			c.depth--;
		} else {
			ModEffects.apply(t, ModEffects.OVERCHARGE, 200, amp);
		}
	};
	private static final Spell.OnHit IGNITE_FLOOR = (c, t) -> {
		if (c.depth > 0) {
			return;
		}
		Vec3 p = t.position();
		c.field(p, 1.6, 2, 10, (cc, l) -> {
			cc.depth++;
			l.forEach(e -> e.igniteForTicks(40));
			cc.depth--;
		});
	};

	// ---- 共通の形 ----

	/** 単発の弾。 */
	private static Spell.Form shot(float speed, float gravity) {
		return c -> {
			c.bolt(speed, gravity);
			c.sound(Fx.castSound(c.element()), 0.8f, 1.2f);
		};
	}

	/** 連打用の短い弾。 */
	private static Spell.Form jab() {
		return c -> {
			c.bolt(2.2f, 0f).life(18);
			c.sound(Fx.castSound(c.element()), 0.4f, 1.6f);
		};
	}

	private static Spell.Form punch(double push) {
		return c -> {
			LivingEntity t = c.melee(3.6, 1f);
			if (t != null && push > 0) {
				Ctx.push(t, c.caster.position(), push, 0.2);
			}
			c.sound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1f);
		};
	}

	private static Spell.Form whip(double range) {
		return c -> {
			c.cone(range, 35, 1f);
			c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
		};
	}

	private static Spell.Form boomerang() {
		return c -> {
			MagicBolt b = c.bolt(1.3f, 0f).life(34);
			b.boomerang = true;
			c.sound(SoundEvents.TRIDENT_THROW.value(), 0.8f, 1.2f);
		};
	}

	/** 前方に壁(壁の中の敵に onTick)。 */
	private static void wall(Ctx c, int seconds, java.util.function.BiConsumer<Ctx, List<LivingEntity>> onTick, boolean reflect) {
		Vec3 flat = new Vec3(c.dir.x, 0, c.dir.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = new Vec3(0, 0, 1);
		}
		flat = flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		Vec3 center = c.caster.position().add(flat.scale(3));
		Vec3 a = center.add(side.scale(3)), b = center.subtract(side.scale(3));
		AABB box = new AABB(a, b).inflate(0.8, 0, 0.8).expandTowards(0, 3, 0);
		Tasks.repeat(0, 5, seconds * 4, i -> {
			for (double s = 0; s <= 6; s += 0.5) {
				Vec3 q = b.add(side.scale(s));
				c.particles(Fx.of(c.element()), q.x, q.y + 0.2 + (i % 4) * 0.7, q.z, 1, 0.05, 0.3, 0.05, 0);
			}
			List<LivingEntity> in = c.enemiesIn(box);
			onTick.accept(c, in);
			for (Projectile p : c.level.getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() != c.caster)) {
				if (reflect) {
					p.setDeltaMovement(p.getDeltaMovement().scale(-1));
					p.setOwner(c.caster);
				} else {
					p.setDeltaMovement(Vec3.ZERO);
				}
			}
		});
	}

	/** 自分を中心に、ついてくる範囲。 */
	private static void aura(Ctx c, double r, int seconds, int every, java.util.function.BiConsumer<Ctx, List<LivingEntity>> onTick) {
		Tasks.repeat(0, every, seconds * 20 / every, i -> {
			if (!c.caster.isAlive()) {
				return;
			}
			Vec3 p = c.caster.position().add(0, 0.3, 0);
			c.ring(p, r, Fx.of(c.element()));
			onTick.accept(c, c.enemiesNear(p, r));
		});
	}

	private static void imbue(Ctx c, Element e) {
		if (c.player() != null) {
			PlayerMagic pm = PlayerMagic.of(c.player());
			pm.imbue = e;
			pm.imbueUntil = c.level.getGameTime() + 20 * 15;
		}
	}

	private static void destroyProjectiles(Ctx c, double r) {
		for (Projectile p : c.level.getEntitiesOfClass(Projectile.class, c.caster.getBoundingBox().inflate(r),
				p -> p.getOwner() != c.caster)) {
			c.particles(ParticleTypes.POOF, p.getX(), p.getY(), p.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
			p.discard();
		}
	}

	// ---------------------------------------------------------------------------------------------

	public static void init() {
		BY_ID.clear();
		windSpells();
		thunderSpells();
		fireSpells();
		waterSpells();
		gravitySpells();
		darkSpells();
		lightSpells();
		curseSpells();
	}

	private static void windSpells() {
		Element W = Element.WIND;
		def(W, 1, c -> {
			c.bolt(1.9f, 0f).pierce = 1;
			c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.5f);
		}, stack(ModEffects.BLEED, 4, 0));
		def(W, 2, c -> {
			c.self(ModEffects.DOUBLE_JUMP, 30, 0);
			c.particles(ParticleTypes.CLOUD, c.caster.getX(), c.caster.getY(), c.caster.getZ(), 12, 0.4, 0.1, 0.4, 0.02);
			c.sound(SoundEvents.BREEZE_JUMP, 1f, 1.2f);
		});
		def(W, 3, c -> {
			for (LivingEntity t : c.cone(6, 60, 1f)) {
				Ctx.push(t, c.caster.position(), 1.6, 0.4);
			}
			c.sound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 1f);
		}, fx(ModEffects.STAGGER, 3, 0)).onHits.add(PUSH);
		def(W, 4, c -> {
			c.self(ModEffects.WIND_SHIELD, 25, 0);
			c.ring(c.caster.position().add(0, 1, 0), 1.2, ParticleTypes.CLOUD);
		});
		def(W, 5, c -> {
			Vec3 p = c.aimPoint(18);
			c.areaAt(p, 4, 0.4f);
			c.field(p, 4, 4, 5, (cc, l) -> {
				for (LivingEntity t : l) {
					Ctx.pull(t, p, 0.45);
				}
			});
			c.soundAt(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 0.6f);
		}, fx(MobEffects.LEVITATION, 1.5f, 0)).onHits.add(SUCK);
		def(W, 6, c -> {
			c.dash(2.4, 0.15);
			c.later(5, () -> c.areaSelf(2.8, 1f));
			c.sound(SoundEvents.BREEZE_SLIDE, 1f, 1.3f);
		}, fx(ModEffects.INACCURACY, 5, 0));
		def(W, 7, c -> {
			Tasks.repeat(0, 3, 4, i -> c.cone(5, 45, 0.35f));
			c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.8f);
		}, stack(ModEffects.BLEED, 6, 2));
		def(W, 8, c -> {
			Vec3 p = c.aimPoint(22);
			c.field(p, 4, 5, 10, (cc, l) -> {
				for (LivingEntity t : l) {
					cc.hit(t, 0.3f);
					t.setDeltaMovement(t.getDeltaMovement().add(0, 0.35, 0));
					t.syncVelocity = true;
				}
				cc.particles(ParticleTypes.GUST, p.x, p.y + 1.5, p.z, 2, 1, 1.5, 1, 0);
			});
		}, fx(ModEffects.SHOCK, 3, 0), fx(ModEffects.WET, 6, 0));
		def(W, 9, c -> {
			c.self(ModEffects.JET_WING, 15, 0);
			c.particles(ParticleTypes.GUST, c.caster.getX(), c.caster.getY(), c.caster.getZ(), 4, 0.5, 0.2, 0.5, 0);
			c.sound(SoundEvents.PHANTOM_FLAP, 1f, 1.2f);
		});
		def(W, 10, c -> {
			destroyProjectiles(c, 10);
			for (LivingEntity t : c.areaSelf(10, 1f)) {
				Ctx.push(t, c.caster.position(), 3.0, 0.8);
			}
			c.particles(ParticleTypes.GUST_EMITTER_LARGE, c.caster.getX(), c.caster.getY() + 1, c.caster.getZ(), 3, 3, 1, 3, 0);
			Combat.blowAwayPlants(c, 6);
			c.sound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 2f, 0.5f);
		}, fx(ModEffects.STUN, 2, 0));
		def(W, 11, c -> {
			LivingEntity t = c.rayTarget(7);
			if (t != null) {
				Ctx.pull(t, c.caster.position(), 1.6);
				c.later(5, () -> {
					c.hit(t, 1f);
					t.setDeltaMovement(t.getDeltaMovement().add(0, -0.8, 0));
					t.syncVelocity = true;
				});
			}
			c.swing();
		}, fx(ModEffects.KNOCKDOWN, 1.5f, 0)).onHits.add(PULL_TO_CASTER);
		def(W, 12, c -> {
			c.cone(3.8, 55, 1f);
			c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.7f);
		}, stack(ModEffects.BLEED, 4, 4));
		def(W, 13, boomerang(), fx(ModEffects.STAGGER, 2, 0));
	}

	private static void thunderSpells() {
		Element T = Element.THUNDER;
		def(T, 1, shot(2.3f, 0f), fx(ModEffects.SHOCK, 4, 0));
		def(T, 2, punch(0), fx(ModEffects.PARALYSIS, 1.5f, 0));
		def(T, 3, shot(2.0f, 0f), fx(ModEffects.SHOCK, 4, 0)).onHits.add(chain(3, 6));
		def(T, 4, c -> {
			Vec3 p = c.aimPoint(14);
			c.field(p, 3.5, 4, 10, (cc, l) -> l.forEach(t -> cc.hit(t, 0.25f)));
		}, fx(MobEffects.SLOWNESS, 2, 1), fx(ModEffects.SHOCK, 2, 0));
		def(T, 5, c -> {
			c.beam(26, 0.5, 1f);
			c.sound(SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 1.5f);
		}, fx(ModEffects.PARALYSIS, 2.5f, 0));
		def(T, 6, shot(1.8f, 0f), fx(ModEffects.SHOCK, 5, 1)).onHits.add(OVERLOAD);
		def(T, 7, c -> wall(c, 6, (cc, l) -> l.forEach(t -> {
			cc.applyStatuses(t);
			Ctx.push(t, cc.caster.position(), 0.8, 0.1);
		}), true), fx(MobEffects.SLOWNESS, 2, 1));
		def(T, 8, c -> {
			Vec3 p = c.aimPoint(26);
			c.lightning(p);
			c.areaAt(p, 4.5, 1f);
		}, fx(ModEffects.STUN, 1.5f, 0));
		def(T, 9, shot(2.0f, 0f), fx(ModEffects.PARALYSIS, 1.5f, 0)).onHits.add(MANA_DRAIN);
		def(T, 10, c -> {
			Vec3 p = c.aimPoint(32);
			for (int i = 0; i < 4; i++) {
				final int k = i;
				c.later(i * 4, () -> c.lightning(p.add((k % 2) * 3 - 1.5, 0, (k / 2) * 3 - 1.5)));
			}
			for (LivingEntity t : c.areaAt(p, 7, 1f)) {
				ModEffects.stack(t, ModEffects.OVERCHARGE, 200, 3);
				ModEffects.stack(t, ModEffects.OVERCHARGE, 200, 3);
			}
		}, fx(ModEffects.PARALYSIS, 5, 1));
		def(T, 11, punch(1.2), chance(0.3f, fx(ModEffects.PARALYSIS, 1.5f, 0)));
		def(T, 12, c -> {
			c.cone(8, 35, 0.7f);
			c.later(4, () -> c.cone(8, 35, 0.4f));
			c.sound(SoundEvents.TRIDENT_THUNDER.value(), 0.4f, 2f);
		}, fx(ModEffects.SHOCK, 3, 0));
		def(T, 13, c -> {
			c.dash(1.3, 0.1);
			c.later(3, () -> c.areaSelf(2.5, 1f));
		}, fx(ModEffects.SHOCK, 2, 0));
	}

	private static void fireSpells() {
		Element F = Element.FIRE;
		def(F, 1, c -> {
			MagicBolt b = c.bolt(1.6f, 0.01f);
			b.impact = (cc, bolt, pos, t) -> {
				cc.areaAt(pos, 2, 1f);
				cc.particles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
				cc.soundAt(pos, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.4f);
			};
			c.sound(SoundEvents.FIRECHARGE_USE, 0.8f, 1f);
		}, fire(3)).onHits.add(splash(1.5, 0.5f));
		def(F, 2, c -> {
			punch(0).cast(c);
			imbue(c, Element.FIRE);
		}, fire(4));
		def(F, 3, c -> aura(c, 3, 5, 10, (cc, l) -> l.forEach(t -> cc.hit(t, 0.25f))), fire(4));
		def(F, 4, c -> {
			Vec3 p = c.aimPoint(14);
			c.field(p, 3, 5, 10, (cc, l) -> l.forEach(t -> cc.hit(t, 0.25f)));
		}, fire(3)).onHits.add(IGNITE_FLOOR);
		def(F, 5, c -> {
			MagicBolt b = c.bolt(2.6f, 0f);
			b.pierce = 5;
			c.sound(SoundEvents.BLAZE_SHOOT, 1f, 0.8f);
		}, fire(6), fx(ModEffects.BURN, 6, 1));
		def(F, 6, c -> {
			for (LivingEntity t : c.enemiesNear(c.caster.position(), 6)) {
				c.hit(t, 1f);
			}
			c.self(ModEffects.HEAT_HAZE, 15, 0);
			c.ring(c.caster.position(), 6, ParticleTypes.FLAME);
		}, blind(3));
		def(F, 7, c -> wall(c, 6, (cc, l) -> l.forEach(t -> cc.hit(t, 0.3f)), false), fire(5));
		def(F, 8, c -> {
			Vec3 p = c.aimPoint(28);
			for (int i = 0; i < 3; i++) {
				final Vec3 q = i == 0 ? p : p.add(c.caster.getRandom().nextGaussian() * 2.5, 0, c.caster.getRandom().nextGaussian() * 2.5);
				final float m = i == 0 ? 1f : 0.4f;
				c.later(i * 8, () -> meteor(c, q, m));
			}
		}, fire(6), fx(ModEffects.STUN, 1, 0));
		def(F, 9, c -> {
			c.areaSelf(4, 1f);
			c.self(ModEffects.REBIRTH, 120, 0);
			c.particles(ParticleTypes.FLAME, c.caster.getX(), c.caster.getY() + 1, c.caster.getZ(), 40, 0.6, 1, 0.6, 0.1);
			c.sound(SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
		}, fire(4));
		def(F, 10, c -> {
			Tasks.repeat(0, 2, 20, i -> {
				if (!c.caster.isAlive()) {
					return;
				}
				c.cone(10, 22, 0.06f);
				if (i % 4 == 0) {
					c.sound(SoundEvents.BLAZE_SHOOT, 0.8f, 0.5f);
				}
			});
			Vec3 p = c.aimPoint(10);
			c.field(p, 2.5, 6, 10, (cc, l) -> {
				cc.particles(ParticleTypes.LAVA, p.x, p.y + 0.2, p.z, 3, 1, 0.1, 1, 0);
				l.forEach(t -> cc.hit(t, 0.12f));
			});
		}, fire(10), fx(ModEffects.BURN, 10, 2));
		def(F, 11, punch(1.5), fire(6));
		def(F, 12, c -> {
			c.cone(3.8, 55, 1f);
			c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.5f);
		}, stack(ModEffects.BURN, 4, 4), fire(2));
		def(F, 13, c -> {
			for (LivingEntity t : c.cone(8, 30, 1f)) {
				Ctx.pull(t, c.caster.position(), 0.9);
			}
			c.sound(SoundEvents.FIRECHARGE_USE, 0.8f, 0.8f);
		}, fire(3)).onHits.add(PULL_TO_CASTER);
	}

	private static void meteor(Ctx c, Vec3 p, float mult) {
		Vec3 from = p.add(c.caster.getRandom().nextGaussian(), 14, c.caster.getRandom().nextGaussian());
		MagicBolt b = MagicBolt.create(c, 0f, 0.06f);
		b.setPos(from.x, from.y, from.z);
		b.dir(p.subtract(from).normalize(), 1.2f);
		b.life(60);
		b.impact = (cc, bolt, pos, t) -> {
			cc.areaAt(pos, 3.5, mult);
			cc.particles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
			cc.particles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 12, 1.5, 0.2, 1.5, 0);
			cc.soundAt(pos, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.8f);
		};
		c.level.addFreshEntity(b);
	}

	private static void waterSpells() {
		Element A = Element.WATER;
		def(A, 1, shot(1.7f, 0.01f), fx(MobEffects.SLOWNESS, 3, 0), WET);
		def(A, 2, c -> {
			punch(0).cast(c);
			c.caster.clearFire();
			imbue(c, Element.WATER);
		}, fx(MobEffects.SLOWNESS, 3, 0), WET);
		def(A, 3, shot(1.1f, 0f), fx(ModEffects.BIND, 2, 0), WET);
		def(A, 4, c -> {
			for (LivingEntity a : c.alliesNear(c.caster.position(), 6)) {
				c.heal(a, Math.max(3, c.power * 0.7f));
				a.clearFire();
			}
			c.particles(ParticleTypes.FALLING_WATER, c.caster.getX(), c.caster.getY() + 3, c.caster.getZ(), 60, 3, 0.3, 3, 0);
			c.sound(SoundEvents.WEATHER_RAIN, 1f, 1.2f);
		});
		def(A, 5, c -> {
			for (LivingEntity t : c.cone(8, 50, 1f)) {
				Ctx.push(t, c.caster.position(), 1.7, 0.3);
			}
			c.sound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1f, 0.8f);
		}, fx(ModEffects.KNOCKDOWN, 1, 0), WET).onHits.add(PUSH);
		def(A, 6, punch(0), freeze(3, 0.35f), fx(MobEffects.SLOWNESS, 3, 1));
		def(A, 7, c -> {
			for (LivingEntity a : c.alliesNear(c.caster.position(), 4)) {
				a.addEffect(new MobEffectInstance(ModEffects.AQUA_SHIELD, 20 * 20, 0));
				a.clearFire();
			}
			c.ring(c.caster.position().add(0, 1, 0), 1.3, ParticleTypes.SPLASH);
		});
		def(A, 8, c -> {
			Vec3 p = c.aimPoint(20);
			c.field(p, 4, 5, 10, (cc, l) -> l.forEach(t -> {
				Ctx.pull(t, p, 0.35);
				cc.hit(t, 0.2f);
			}));
		}, fx(ModEffects.BIND, 3, 1), fx(ModEffects.DROWN, 5, 0), WET);
		def(A, 9, c -> {
			Vec3 p = c.caster.position();
			c.field(p, 5, 10, 20, (cc, l) -> {
				for (LivingEntity a : cc.alliesNear(p, 5)) {
					cc.heal(a, Math.max(1.5f, cc.power * 0.25f));
				}
			});
		});
		def(A, 10, c -> {
			Vec3 p = c.aimPoint(24);
			c.areaAt(p, 8, 0.6f);
			c.field(p, 8, 6, 10, (cc, l) -> l.forEach(t -> {
				cc.hit(t, 0.12f);
				Ctx.pull(t, p, 0.25);
			}));
			c.particles(ParticleTypes.SPLASH, p.x, p.y + 1, p.z, 200, 5, 1, 5, 0.2);
			c.soundAt(p, SoundEvents.GENERIC_SPLASH, 2f, 0.5f);
		}, fx(ModEffects.DROWN, 8, 1), fx(ModEffects.KNOCKDOWN, 1.5f, 0), WET);
		def(A, 11, punch(0.6), fx(MobEffects.SLOWNESS, 3, 0), WET);
		def(A, 12, whip(8), fx(ModEffects.KNOCKDOWN, 1, 0), WET);
		def(A, 13, jab(), fx(MobEffects.SLOWNESS, 1.5f, 0), WET);
	}

	private static void gravitySpells() {
		Element G = Element.GRAVITY;
		def(G, 1, c -> {
			MagicBolt b = c.bolt(1.5f, 0.03f);
			b.impact = (cc, bolt, pos, t) -> {
				for (LivingEntity e : cc.areaAt(pos, 2.5, 1f)) {
					Ctx.pull(e, pos, 0.6);
				}
			};
			c.sound(SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.6f);
		}, fx(MobEffects.SLOWNESS, 3, 1));
		def(G, 2, c -> {
			c.self(MobEffects.SLOW_FALLING, 30, 0);
			c.caster.fallDistance = 0;
		});
		def(G, 3, c -> {
			LivingEntity t = c.rayTarget(15);
			if (t != null) {
				c.hit(t, 1f);
				Ctx.pull(t, c.caster.position(), 1.8);
				line(c, c.origin, t.position().add(0, t.getBbHeight() / 2, 0));
			} else {
				c.bolt(1.8f, 0f);
			}
		}, fx(ModEffects.BIND, 1.5f, 0)).onHits.add(PULL_TO_CASTER);
		def(G, 4, shot(1.6f, 0f), fx(MobEffects.LEVITATION, 3, 1));
		def(G, 5, c -> {
			LivingEntity t = c.rayTarget(16);
			if (t == null) {
				return;
			}
			t.setDeltaMovement(0, 1.0, 0);
			t.syncVelocity = true;
			c.later(9, () -> {
				t.setDeltaMovement(0, -2.5, 0);
				t.syncVelocity = true;
				c.hit(t, 1f);
				c.particles(ParticleTypes.EXPLOSION, t.getX(), t.getY(), t.getZ(), 1, 0, 0, 0, 0);
			});
		}, fx(ModEffects.STUN, 1, 0));
		def(G, 6, c -> {
			Vec3 p = c.aimPoint(16);
			c.field(p, 5, 5, 10, (cc, l) -> l.forEach(t -> {
				cc.hit(t, 0.15f);
				ModEffects.apply(t, MobEffects.LEVITATION, 15, 0);
			}));
		}, fx(MobEffects.SLOWNESS, 3, 1));
		def(G, 7, c -> wall(c, 6, (cc, l) -> l.forEach(t -> {
			cc.applyStatuses(t);
			Ctx.push(t, cc.caster.position(), 0.5, 0);
		}), false), fx(ModEffects.BIND, 1.5f, 0));
		def(G, 8, c -> {
			Vec3 p = c.aimPoint(20);
			c.field(p, 6, 4, 4, (cc, l) -> {
				for (LivingEntity t : l) {
					Ctx.pull(t, p, 0.5);
				}
				for (ItemEntity it : cc.level.getEntitiesOfClass(ItemEntity.class, new AABB(p, p).inflate(6))) {
					Vec3 d = p.subtract(it.position());
					it.setDeltaMovement(d.normalize().scale(0.3));
				}
				if (cc.level.getGameTime() % 10 == 0) {
					l.forEach(t -> cc.hit(t, 0.1f));
				}
				cc.particles(ParticleTypes.PORTAL, p.x, p.y + 1, p.z, 20, 2, 1, 2, 1);
			});
		}, fx(ModEffects.BIND, 3, 1)).onHits.add(SUCK);
		def(G, 9, c -> {
			LivingEntity t = c.rayTarget(16);
			if (t != null) {
				c.hit(t, 1f);
				t.setDeltaMovement(0, -2, 0);
				t.syncVelocity = true;
			}
		}, fx(ModEffects.STUN, 3, 0), fx(ModEffects.BIND, 3, 0));
		def(G, 10, c -> {
			Vec3 p = c.aimPoint(24);
			c.field(p, 8, 3, 4, (cc, l) -> {
				l.forEach(t -> Ctx.pull(t, p, 0.8));
				cc.particles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1, p.z, 30, 3, 1, 3, 0.5);
			});
			c.later(60, () -> {
				c.areaAt(p.add(0, 0.5, 0), 4.5, 1.5f);
				c.particles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 1, p.z, 1, 0, 0, 0, 0);
				c.soundAt(p, SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 0.5f);
			});
		}, fx(ModEffects.BIND, 4, 2), fx(ModEffects.STUN, 4, 0));
		def(G, 11, punch(0), fx(ModEffects.STUN, 1, 0), fx(ModEffects.BIND, 1.5f, 0));
		def(G, 12, boomerang(), fx(ModEffects.BIND, 1, 0));
		def(G, 13, c -> {
			c.melee(3.6, 1f);
			c.sound(SoundEvents.PLAYER_ATTACK_WEAK, 0.6f, 1.4f);
		}, fx(MobEffects.LEVITATION, 0.5f, 0));
	}

	private static void darkSpells() {
		Element D = Element.DARK;
		def(D, 1, shot(1.9f, 0f), blind(4));
		def(D, 2, c -> {
			punch(0).cast(c);
			imbue(c, Element.DARK);
		}, blind(3));
		def(D, 3, shot(1.7f, 0f), fx(ModEffects.FEAR, 4, 0));
		def(D, 4, c -> {
			Vec3 p = c.aimPoint(14);
			c.field(p, 4, 6, 20, (cc, l) -> {
				cc.particles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 1, p.z, 25, 2, 1, 2, 0.01);
				l.forEach(t -> cc.hit(t, 0.2f));
			});
		}, blind(2));
		def(D, 5, shot(1.8f, 0f), fx(MobEffects.WEAKNESS, 5, 0)).onHits.add(DRAIN);
		def(D, 6, c -> {
			Vec3 to = c.origin.add(c.dir.scale(9));
			BlockHitResult bh = c.level.clip(new ClipContext(c.origin, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.caster));
			Vec3 dst = bh.getType() == HitResult.Type.MISS ? to : bh.getLocation().subtract(c.dir.scale(0.8));
			c.particles(ParticleTypes.SQUID_INK, c.caster.getX(), c.caster.getY() + 1, c.caster.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
			c.caster.teleportTo(dst.x, Math.max(dst.y - 1.5, c.caster.getY() - 3), dst.z);
			c.caster.fallDistance = 0;
			c.sound(SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.4f);
		});
		def(D, 7, shot(1.8f, 0f), fx(MobEffects.WEAKNESS, 6, 0), fx(MobEffects.WITHER, 6, 0));
		def(D, 8, c -> c.areaAt(c.aimPoint(16), 4, 1f), fx(ModEffects.CONFUSION, 6, 0), fx(MobEffects.NAUSEA, 6, 0));
		def(D, 9, c -> {
			c.caster.addEffect(new MobEffectInstance(ModEffects.CLONE, 20 * 20, 2));
			c.particles(ParticleTypes.SQUID_INK, c.caster.getX(), c.caster.getY() + 1, c.caster.getZ(), 30, 0.6, 0.8, 0.6, 0.02);
			c.sound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1f);
		});
		def(D, 10, c -> {
			Vec3 p = c.aimPoint(20);
			c.field(p, 6, 8, 10, (cc, l) -> {
				cc.particles(ParticleTypes.SCULK_SOUL, p.x, p.y + 0.3, p.z, 6, 3, 0.2, 3, 0.02);
				l.forEach(t -> cc.hit(t, 0.15f));
			});
		}, fx(MobEffects.WEAKNESS, 4, 2), fx(ModEffects.FEAR, 3, 0));
		def(D, 11, c -> {
			LivingEntity t = c.melee(3.6, 1f);
			if (t != null) {
				c.caster.heal(Math.max(0.5f, c.power * 0.25f));
			}
		}, blind(2));
		def(D, 12, whip(8), fx(MobEffects.WEAKNESS, 4, 0));
		def(D, 13, jab(), fx(ModEffects.INACCURACY, 1.5f, 0));
	}

	private static void lightSpells() {
		Element L = Element.LIGHT;
		def(L, 1, shot(2.5f, 0f), blind(2));
		def(L, 2, c -> {
			punch(0).cast(c);
			c.heal(c.caster, Math.max(1, c.power * 0.3f));
		});
		def(L, 3, c -> {
			for (LivingEntity a : c.alliesNear(c.caster.position(), 6)) {
				Ctx.cleanse(a);
			}
			for (LivingEntity t : c.enemiesNear(c.caster.position(), 6)) {
				for (MobEffectInstance i : List.copyOf(t.getActiveEffects())) {
					if (i.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) {
						t.removeEffect(i.getEffect());
					}
				}
			}
			c.ring(c.caster.position(), 6, ParticleTypes.END_ROD);
			c.sound(SoundEvents.BEACON_ACTIVATE, 0.6f, 1.6f);
		});
		def(L, 4, c -> {
			c.self(MobEffects.NIGHT_VISION, 60, 0);
			for (LivingEntity t : c.enemiesNear(c.caster.position(), 28)) {
				t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
			}
			c.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f);
		});
		def(L, 5, c -> {
			c.beam(30, 0.6, 1f);
			c.sound(SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f);
		}, blind(3));
		def(L, 6, c -> {
			Vec3 p = c.caster.position();
			c.field(p, 6, 8, 20, (cc, l) -> {
				for (LivingEntity a : cc.alliesNear(p, 6)) {
					cc.heal(a, Math.max(1, cc.power * 0.2f));
				}
			});
		});
		def(L, 7, c -> {
			Vec3 p = c.aimPoint(18);
			c.areaAt(p, 5, 1f);
			for (LivingEntity a : c.alliesNear(p, 5)) {
				Ctx.cleanse(a);
			}
			c.particles(ParticleTypes.END_ROD, p.x, p.y + 4, p.z, 60, 3, 2, 3, 0.02);
		}, blind(3));
		def(L, 8, c -> {
			for (LivingEntity a : c.alliesNear(c.caster.position(), 4)) {
				a.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 30, 3));
			}
			c.ring(c.caster.position().add(0, 1, 0), 1.5, ParticleTypes.END_ROD);
			c.sound(SoundEvents.BEACON_ACTIVATE, 1f, 1.2f);
		});
		def(L, 9, c -> {
			for (LivingEntity a : c.alliesNear(c.caster.position(), 8)) {
				a.addEffect(new MobEffectInstance(ModEffects.REBIRTH, 20 * 300, 0));
			}
			c.particles(ParticleTypes.TOTEM_OF_UNDYING, c.caster.getX(), c.caster.getY() + 1, c.caster.getZ(), 40, 1, 1, 1, 0.3);
			c.sound(SoundEvents.TOTEM_USE, 0.6f, 1.4f);
		});
		def(L, 10, c -> {
			Vec3 p = c.aimPoint(32);
			for (int y = 0; y < 24; y++) {
				c.particles(ParticleTypes.END_ROD, p.x, p.y + y, p.z, 6, 1.2, 0.3, 1.2, 0);
			}
			c.areaAt(p, 7, 1f);
			c.soundAt(p, SoundEvents.BEACON_DEACTIVATE, 2f, 0.6f);
		}, fx(ModEffects.STUN, 3, 0), blind(5));
		def(L, 11, punch(0.6), blind(2));
		def(L, 12, c -> {
			whip(8).cast(c);
			for (LivingEntity a : c.alliesNear(c.caster.position(), 3)) {
				Ctx.cleanse(a);
			}
		}, blind(2));
		def(L, 13, jab(), fx(ModEffects.INACCURACY, 1.5f, 0));
	}

	private static void curseSpells() {
		Element C = Element.CURSE;
		def(C, 1, shot(1.7f, 0f), fx(MobEffects.WEAKNESS, 8, 0));
		def(C, 2, shot(1.7f, 0f), fx(MobEffects.SLOWNESS, 8, 1));
		def(C, 3, shot(1.7f, 0f), fx(ModEffects.INACCURACY, 8, 0), fx(MobEffects.BLINDNESS, 4, 0));
		def(C, 4, shot(1.7f, 0f), fx(MobEffects.POISON, 8, 1));
		def(C, 5, shot(1.7f, 0f), fx(ModEffects.ARMOR_BREAK, 10, 0));
		def(C, 6, shot(1.7f, 0f), fx(ModEffects.SILENCE, 6, 0));
		def(C, 7, shot(1.7f, 0f), fx(MobEffects.WEAKNESS, 6, 0), fx(MobEffects.SLOWNESS, 6, 0)).onHits.add(DRAIN);
		def(C, 8, shot(1.7f, 0f), fx(ModEffects.CONFUSION, 6, 0), fx(MobEffects.NAUSEA, 6, 0));
		def(C, 9, shot(1.7f, 0f), fx(ModEffects.MANA_SEAL, 10, 0));
		def(C, 10, c -> {
			Vec3 p = c.aimPoint(20);
			c.areaAt(p, 4, 1f);
			c.particles(ParticleTypes.SCULK_SOUL, p.x, p.y + 1, p.z, 40, 2, 1, 2, 0.02);
			c.soundAt(p, SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
		}, fx(ModEffects.DOOM, 10, 0));
		def(C, 11, c -> c.melee(3.6, 1f), randomCurse(1, 6));
		def(C, 12, whip(8), randomCurse(2, 5));
		def(C, 13, jab(), (c, t, d) -> ModEffects.stack(t, CURSES.get(t.getRandom().nextInt(3)), Math.round(60 * d), 3));
	}

	// ---------------------------------------------------------------------------------------------
	// 合成魔法(設計書 第8章)

	/** 固有の名前がある固定レシピ(8.3 / 8.5)。キーは「ベース+サブ…」。 */
	public static final Map<String, String> FIXED_RECIPES = new LinkedHashMap<>();

	static {
		FIXED_RECIPES.put("fire_1+thunder_3", "プラズマファイア");
		FIXED_RECIPES.put("water_5+gravity_8", "タイダルグラビティ");
		FIXED_RECIPES.put("curse_10+dark_5", "絶望の刻印");
		FIXED_RECIPES.put("thunder_5+water_1", "スチームランス");
		FIXED_RECIPES.put("wind_5+fire_4", "ファイアトルネード");
		FIXED_RECIPES.put("gravity_1+dark_7", "虚無の重弾");
		FIXED_RECIPES.put("light_5+curse_5", "破魔の光線");
		FIXED_RECIPES.put("wind_1+curse_4", "毒風の刃");
	}

	public static String recipeKey(String base, List<String> subs) {
		StringBuilder sb = new StringBuilder(base);
		subs.stream().sorted().forEach(s -> sb.append('+').append(s));
		return sb.toString();
	}

	/** 呪いとの合成親和性(設計書 11.4)。 */
	public static float curseAffinity(Spell base, List<Spell> subs) {
		List<Spell> all = new ArrayList<>(subs);
		all.add(base);
		boolean curse = all.stream().anyMatch(s -> s.element == Element.CURSE);
		if (!curse) {
			return 1f;
		}
		float best = 1.15f;
		for (Spell s : all) {
			if (s.element == Element.DARK) {
				best = Math.max(best, 1.4f);
			} else if (s.element == Element.THUNDER || s.element == Element.GRAVITY) {
				best = Math.max(best, 1.25f);
			}
		}
		if (all.stream().anyMatch(s -> s.element == Element.LIGHT)) {
			best = Math.min(best, 0.85f);
		}
		return best;
	}

	/** 属性の組み合わせの「相性ヒント」(炉の中で光る)。 */
	public static boolean goodPair(Element a, Element b) {
		if (a == b) {
			return true;
		}
		return pair(a, b, Element.FIRE, Element.THUNDER) || pair(a, b, Element.WATER, Element.GRAVITY)
				|| pair(a, b, Element.CURSE, Element.DARK) || pair(a, b, Element.WIND, Element.FIRE)
				|| pair(a, b, Element.WATER, Element.THUNDER) || pair(a, b, Element.WIND, Element.CURSE)
				|| pair(a, b, Element.GRAVITY, Element.DARK) || pair(a, b, Element.CURSE, Element.THUNDER)
				|| pair(a, b, Element.CURSE, Element.GRAVITY);
	}

	private static boolean pair(Element a, Element b, Element x, Element y) {
		return a == x && b == y || a == y && b == x;
	}

	public static final float[] TIER_POWER = { 0.8f, 1.0f, 1.2f };

	/** 合成魔法を組み立てる: ベースの形 + サブの状態異常・追加効果。 */
	public static @Nullable Spell build(PlayerMagic.Synth sy) {
		Spell base = get(sy.base());
		if (base == null) {
			return null;
		}
		List<Spell> subs = new ArrayList<>();
		for (String id : sy.subs()) {
			Spell s = get(id);
			if (s != null) {
				subs.add(s);
			}
		}
		float subMp = 0, subCt = 0;
		for (Spell s : subs) {
			subMp += s.mp * 0.5f;
			subCt += s.ct * 0.2f;
		}
		int tier = Math.max(1, Math.min(3, sy.tier()));
		Spell out = new Spell("syn:" + sy.id(), sy.name(), base.element, base.no, base.rank, base.tag,
				base.status, base.special, base.coef * TIER_POWER[tier - 1], (base.mp + subMp) * 1.2f, base.ct * 1.3f + subCt,
				base.damage || subs.stream().anyMatch(s -> s.damage));
		out.castTicks = base.castTicks + 4;
		out.form = base.form;
		out.statuses.addAll(base.statuses);
		out.onHits.addAll(base.onHits);
		for (Spell s : subs) {
			out.statuses.addAll(s.statuses);
			out.onHits.addAll(s.onHits);
			if (s.element != base.element && !out.extraElements.contains(s.element)) {
				out.extraElements.add(s.element);
			}
		}
		out.slotCost = 2;
		out.synthesized = true;
		out.curseBonus = curseAffinity(base, subs);
		return out;
	}

	public static String autoName(Spell base, List<Spell> subs) {
		String key = recipeKey(base.id, subs.stream().map(s -> s.id).toList());
		String fixed = FIXED_RECIPES.get(key);
		if (fixed != null) {
			return fixed;
		}
		StringBuilder sb = new StringBuilder();
		for (Spell s : subs) {
			sb.append(s.element.ja);
		}
		return base.name + "・" + sb + "式";
	}

	/** 木の葉や草を吹き飛ばす(テンペストブレイカーの「地形の軽い変化」)。 */
	static boolean isLightPlant(BlockState s) {
		return s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.FLOWERS)
				|| s.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS) || s.is(net.minecraft.world.level.block.Blocks.TALL_GRASS)
				|| s.is(net.minecraft.world.level.block.Blocks.FERN) || s.is(net.minecraft.world.level.block.Blocks.SNOW);
	}

	static BlockPos[] around(BlockPos c, int r) {
		List<BlockPos> l = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 4, r))) {
			if (p.distSqr(c) <= r * r) {
				l.add(p.immutable());
			}
		}
		return l.toArray(new BlockPos[0]);
	}
}
