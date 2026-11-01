package com.magicmod;

import static com.magicmod.Spell.St.chance;
import static com.magicmod.Spell.St.fx;
import static com.magicmod.Spell.St.stack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 物理わざ(7 カテゴリ × 9 = 63 種、設計書 12.3)。スタミナを使う。 */
public final class Techniques {

	public static final class Tech {
		public final String id;
		public final String category;
		public final int no;
		public final String name;
		public final String rank;
		public final float coef;
		public final float stamina;
		public final float ct;
		public final Spell spell;
		public final Balance.TechRow row;

		Tech(Balance.TechRow r, Spell s, float coef) {
			this.id = r.category() + "_" + r.no();
			this.category = r.category();
			this.no = r.no();
			this.name = r.name();
			this.rank = r.rank();
			this.coef = coef;
			this.stamina = r.stamina();
			this.ct = r.ct();
			this.spell = s;
			this.row = r;
		}

		/** 覚えるのに必要な熟練度 Lv。 */
		public int requiredMastery() {
			return no >= 9 ? 4 : no >= 7 ? 3 : no >= 5 ? 2 : 1;
		}
	}

	public static final List<String> CATEGORIES = List.of("blade", "polearm", "dual", "swing", "throw", "bow", "heavy");
	private static final Map<String, Tech> BY_ID = new LinkedHashMap<>();

	private Techniques() {
	}

	public static @Nullable Tech get(String id) {
		return BY_ID.get(id);
	}

	public static List<Tech> of(String cat) {
		List<Tech> l = new ArrayList<>();
		for (Tech t : BY_ID.values()) {
			if (t.category.equals(cat)) {
				l.add(t);
			}
		}
		return l;
	}

	public static String categoryName(String cat) {
		return Balance.get().categoryName.getOrDefault(cat, cat);
	}

	public static int slots(PlayerMagic pm, String cat, ItemStack weapon) {
		return pm.masteryLevel(cat) + 1 + Calc.weaponEnch(weapon, "tslot") + pm.skill("p_slot");
	}

	// ---------------------------------------------------------------------------------------------

	private static void def(String cat, int no, Spell.Form form, Spell.St... st) {
		Balance.TechRow row = null;
		for (Balance.TechRow r : Balance.get().techniques) {
			if (r.category().equals(cat) && r.no() == no) {
				row = r;
			}
		}
		if (row == null) {
			throw new IllegalStateException("balance.json に物理わざ " + cat + " " + no + " がありません");
		}
		Balance.RankRow rank = Balance.get().techRanks.get(row.rank());
		float coef = rank == null ? 1f : rank.coef();
		boolean damage = row.damage() || cat.equals("swing") && no == 2 || cat.equals("bow") && no == 7;
		Spell s = new Spell("t:" + cat + "_" + no, row.name(), null, no, row.rank(), "", row.status(), row.special(), coef,
				row.stamina(), row.ct(), damage);
		s.form = form;
		s.statuses.addAll(List.of(st));
		BY_ID.put(cat + "_" + no, new Tech(row, s, coef));
	}

	private static Spell.St bleed(float sec, int amp) {
		return (c, t, d) -> ModEffects.stack(t, ModEffects.BLEED, Math.round(sec * 20 * d), amp + c.ampBonus());
	}

	private static void charge(Ctx c, int ticks, Runnable after) {
		c.caster.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 3, false, false));
		net.minecraft.world.level.Level lv = c.level;
		Tasks.repeat(0, 4, Math.max(1, ticks / 4), i -> c.particles(ParticleTypes.CRIT, c.caster.getX(), c.caster.getY() + 1,
				c.caster.getZ(), 6, 0.6, 0.6, 0.6, 0.1));
		c.sound(SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.6f, 0.8f);
		Tasks.later(ticks, () -> {
			if (c.caster.isAlive()) {
				after.run();
			}
		});
	}

	private static void hits(Ctx c, int n, int every, Runnable r) {
		Tasks.repeat(0, every, n, i -> {
			if (c.caster.isAlive()) {
				r.run();
			}
		});
	}

	private static MagicBolt shot(Ctx c, float speed, float spread) {
		MagicBolt b = MagicBolt.create(c, speed, 0.02f);
		if (spread > 0) {
			Vec3 d = Ctx.rotateY(c.dir, (c.caster.getRandom().nextFloat() - 0.5f) * spread);
			b.dir(d, speed);
		}
		c.level.addFreshEntity(b);
		return b;
	}

	private static void breakGuard(Ctx c, double range, int sec) {
		LivingEntity t = c.melee(range, 1f);
		if (t != null) {
			t.removeEffect(MobEffects.RESISTANCE);
			t.removeEffect(MobEffects.ABSORPTION);
			ModEffects.apply(t, ModEffects.ARMOR_BREAK, sec * 20, 0);
			if (t instanceof Player tp) {
				tp.getCooldowns().addCooldown(new ItemStack(Items.SHIELD), sec * 20);
				tp.stopUsingItem();
			}
		}
	}

	public static void init() {
		BY_ID.clear();
		String B = "blade";
		def(B, 1, c -> c.melee(3.6, 1f), bleed(4, 0));
		def(B, 2, c -> breakGuard(c, 3.6, 5));
		def(B, 3, c -> {
			c.pierceArmor = true;
			c.melee(3.6, 1f);
		}, fx(ModEffects.STUN, 1, 0));
		def(B, 4, c -> c.areaSelf(3.5, 1f), bleed(5, 0));
		def(B, 5, c -> hits(c, 2, 4, () -> c.melee(3.8, 0.5f)), bleed(5, 1));
		def(B, 6, c -> {
			c.caster.addEffect(new MobEffectInstance(ModEffects.COUNTER, 60, 0));
			if (c.player() != null) {
				PlayerMagic.of(c.player()).counterDamage = c.power;
			}
			c.ring(c.caster.position().add(0, 1, 0), 1, ParticleTypes.ENCHANTED_HIT);
		}, fx(ModEffects.STUN, 1, 0));
		def(B, 7, c -> c.cone(4.5, 40, 1f), bleed(8, 2));
		def(B, 8, c -> hits(c, 3, 3, () -> c.cone(3.8, 50, 0.34f)), bleed(8, 3));
		def(B, 9, c -> charge(c, 20, () -> {
			c.areaSelf(6, 1f);
			c.particles(ParticleTypes.SWEEP_ATTACK, c.caster.getX(), c.caster.getY() + 1, c.caster.getZ(), 20, 3, 0.3, 3, 0);
			c.sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
		}), fx(ModEffects.STUN, 3, 0), bleed(8, 3));

		String P = "polearm";
		def(P, 1, c -> c.melee(5.5, 1f), bleed(4, 0));
		def(P, 2, c -> c.cone(5, 60, 1f), fx(ModEffects.KNOCKDOWN, 1, 0));
		def(P, 3, c -> hits(c, 3, 2, () -> c.beam(6.5, 0.4, 0.34f)), bleed(5, 1));
		def(P, 4, c -> {
			LivingEntity t = c.melee(2.8, 1f);
			if (t != null) {
				Ctx.push(t, c.caster.position(), 0.6, 0.1);
			}
		}, fx(ModEffects.STUN, 1, 0));
		def(P, 5, c -> c.areaSelf(4.5, 1f), fx(ModEffects.KNOCKDOWN, 1, 0));
		def(P, 6, c -> {
			c.dash(2.0, 0.1);
			c.later(4, () -> c.melee(4.5, 1f));
		}, bleed(5, 0));
		def(P, 7, c -> c.beam(10, 0.5, 1f), bleed(8, 2));
		def(P, 8, c -> hits(c, 4, 3, () -> {
			LivingEntity t = c.melee(5.5, 0.25f);
			if (t != null && c.hits >= 4) {
				t.setDeltaMovement(t.getDeltaMovement().add(0, 0.9, 0));
				t.syncVelocity = true;
			}
		}), bleed(8, 3));
		def(P, 9, c -> {
			c.dash(2.6, 0.1);
			c.later(3, () -> c.beam(14, 0.8, 1f));
		}, fx(ModEffects.STUN, 3, 0), bleed(8, 3));

		String D = "dual";
		def(D, 1, c -> hits(c, 2, 3, () -> c.melee(3.4, 0.5f)), bleed(3, 0));
		def(D, 2, c -> {
			c.melee(3.4, 1f);
			Vec3 back = c.dir.scale(-1.3);
			c.caster.setDeltaMovement(back.x, 0.25, back.z);
			c.caster.syncVelocity = true;
		});
		def(D, 3, c -> hits(c, 5, 2, () -> c.cone(3.5, 50, 0.2f)), bleed(5, 1));
		def(D, 4, c -> hits(c, 2, 3, () -> c.cone(3.8, 40, 0.5f)), bleed(5, 0));
		def(D, 5, c -> {
			Vec3 back = c.dir.scale(-0.7);
			c.caster.setDeltaMovement(back.x, 0.1, back.z);
			c.caster.syncVelocity = true;
			c.later(6, () -> c.melee(4.5, 1f));
		}, fx(ModEffects.STAGGER, 3, 0));
		def(D, 6, c -> {
			c.dash(1.6, 0.1);
			hits(c, 4, 3, () -> c.areaSelf(2.6, 0.25f));
		}, bleed(8, 2));
		def(D, 7, c -> {
			LivingEntity t = c.rayTarget(10);
			if (t != null) {
				Vec3 behind = t.position().subtract(c.dir.scale(1.5));
				c.particles(ParticleTypes.SQUID_INK, c.caster.getX(), c.caster.getY() + 1, c.caster.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
				c.caster.teleportTo(behind.x, t.getY(), behind.z);
				c.hit(t, 1f);
			}
		}, fx(ModEffects.STAGGER, 3, 0));
		def(D, 8, c -> hits(c, 8, 2, () -> c.cone(3.5, 60, 0.125f)), bleed(10, 4));
		def(D, 9, c -> hits(c, 16, 2, () -> c.areaSelf(3, 0.07f)), bleed(12, 5));

		String S = "swing";
		def(S, 1, c -> c.areaSelf(3.5, 1f), bleed(3, 0));
		def(S, 2, c -> c.melee(7.5, 1f));
		def(S, 3, c -> {
			LivingEntity t = c.rayTarget(8);
			if (t != null) {
				c.hit(t, 1f);
				Ctx.pull(t, c.caster.position(), 1.5);
			}
			c.swing();
		}, fx(ModEffects.BIND, 1.5f, 0));
		def(S, 4, c -> hits(c, 3, 4, () -> c.cone(3.8, 80, 0.34f)), bleed(5, 0));
		def(S, 5, c -> c.areaSelf(4.5, 1f), fx(ModEffects.KNOCKDOWN, 1, 0));
		def(S, 6, c -> hits(c, 4, 4, () -> {
			c.areaSelf(3.8, 0.3f);
			if (c.caster.getRandom().nextFloat() < 0.3f) {
				c.caster.hurtServer(c.level, c.level.damageSources().generic(), 1.5f);
			}
		}), bleed(8, 2));
		def(S, 7, c -> {
			LivingEntity t = c.rayTarget(8);
			if (t != null) {
				c.hit(t, 1f);
			}
			c.swing();
		}, fx(ModEffects.BIND, 3, 1));
		def(S, 8, c -> hits(c, 5, 3, () -> c.areaSelf(5, 0.2f)), bleed(8, 3), fx(ModEffects.KNOCKDOWN, 1, 0));
		def(S, 9, c -> {
			hits(c, 10, 2, () -> c.areaSelf(4.2, 0.1f));
			c.later(22, () -> c.caster.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3)));
		}, bleed(12, 5), fx(MobEffects.NAUSEA, 4, 0));

		String T = "throw";
		def(T, 1, c -> shot(c, 2.0f, 0), bleed(3, 0));
		def(T, 2, c -> hits(c, 3, 2, () -> shot(c, 2.1f, 0.1f).hitMult = 0.34f), bleed(4, 0));
		def(T, 3, c -> {
			MagicBolt b = MagicBolt.create(c, 1.3f, 0f);
			b.boomerang = true;
			b.life(34);
			c.level.addFreshEntity(b);
		}, fx(ModEffects.STAGGER, 2, 0));
		def(T, 4, c -> shot(c, 2.0f, 0), fx(MobEffects.POISON, 6, 1));
		def(T, 5, c -> {
			for (int i = 0; i < 5; i++) {
				shot(c, 1.9f, 0.6f).hitMult = 0.34f;
			}
		}, bleed(5, 1));
		def(T, 6, c -> shot(c, 2.6f, 0).pierce = 4, bleed(8, 2));
		def(T, 7, c -> {
			if (c.player() != null) {
				PlayerMagic pm = PlayerMagic.of(c.player());
				pm.cooldowns.keySet().removeIf(k -> k.startsWith("t:throw_") && !k.equals("t:throw_7"));
				pm.stamina = Math.min(Calc.maxStamina(pm), pm.stamina + 15);
			}
			c.particles(ParticleTypes.ENCHANTED_HIT, c.caster.getX(), c.caster.getY() + 1, c.caster.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
		});
		def(T, 8, c -> hits(c, 5, 2, () -> c.melee(3.2, 0.2f)), bleed(8, 3));
		def(T, 9, c -> {
			Vec3 p = c.aimPoint(24);
			Tasks.repeat(0, 2, 30, i -> {
				Vec3 from = p.add(c.caster.getRandom().nextGaussian() * 3, 10, c.caster.getRandom().nextGaussian() * 3);
				MagicBolt b = MagicBolt.create(c, 0, 0.05f);
				b.setPos(from.x, from.y, from.z);
				b.dir(new Vec3(0, -1, 0), 1.5f);
				b.hitMult = 0.06f;
				c.level.addFreshEntity(b);
			});
		}, bleed(12, 5));

		String W = "bow";
		def(W, 1, c -> shot(c, 3.0f, 0), bleed(3, 0));
		def(W, 2, c -> charge(c, 12, () -> shot(c, 3.6f, 0)), fx(ModEffects.STUN, 1, 0));
		def(W, 3, c -> hits(c, 3, 3, () -> shot(c, 3.0f, 0.15f).hitMult = 0.4f), bleed(4, 0));
		def(W, 4, c -> shot(c, 3.2f, 0).pierce = 5, bleed(6, 1));
		def(W, 5, c -> shot(c, 2.0f, 0).homing = true, fx(ModEffects.STAGGER, 3, 0));
		def(W, 6, c -> shot(c, 3.0f, 0), chance(0.6f, fx(ModEffects.STUN, 1.5f, 0)));
		def(W, 7, c -> hits(c, 25, 4, () -> {
			Ctx cc = new Ctx(c.caster, c.spell, c.power * 0.15f);
			cc.physical = true;
			shot(cc, 3.0f, 0.05f);
		}), bleed(4, 0));
		def(W, 8, c -> {
			Vec3 p = c.aimPoint(30);
			Tasks.repeat(0, 2, 20, i -> {
				Vec3 from = p.add(c.caster.getRandom().nextGaussian() * 3, 12, c.caster.getRandom().nextGaussian() * 3);
				MagicBolt b = MagicBolt.create(c, 0, 0.05f);
				b.setPos(from.x, from.y, from.z);
				b.dir(new Vec3(0, -1, 0), 2f);
				b.hitMult = 0.1f;
				c.level.addFreshEntity(b);
			});
		}, bleed(8, 3));
		def(W, 9, c -> charge(c, 24, () -> {
			c.beam(40, 0.8, 1f);
			c.sound(SoundEvents.TRIDENT_THUNDER.value(), 1f, 1.5f);
		}), fx(ModEffects.STUN, 3, 0), bleed(8, 3));

		String H = "heavy";
		def(H, 1, c -> c.melee(4, 1f), fx(ModEffects.STUN, 1, 0));
		def(H, 2, c -> {
			c.dash(1.2, 0.1);
			c.later(4, () -> c.cone(4, 45, 1f));
		}, fx(ModEffects.KNOCKDOWN, 1, 0));
		def(H, 3, c -> {
			c.areaSelf(4, 1f);
			c.particles(ParticleTypes.EXPLOSION, c.caster.getX(), c.caster.getY(), c.caster.getZ(), 3, 1.5, 0, 1.5, 0);
		}, fx(ModEffects.KNOCKDOWN, 1, 0));
		def(H, 4, c -> breakGuard(c, 4, 8));
		def(H, 5, c -> charge(c, 8, () -> hits(c, 2, 6, () -> c.cone(4.5, 50, 0.5f))), fx(ModEffects.STUN, 2, 0));
		def(H, 6, c -> c.areaSelf(5, 1f), fx(ModEffects.KNOCKDOWN, 1.5f, 0));
		def(H, 7, c -> charge(c, 20, () -> c.melee(4.5, 1f)), fx(ModEffects.STUN, 3, 0));
		def(H, 8, c -> {
			for (LivingEntity t : c.areaSelf(7, 1f)) {
				Ctx.push(t, c.caster.position(), 2.0, 0.5);
			}
			c.particles(ParticleTypes.EXPLOSION_EMITTER, c.caster.getX(), c.caster.getY(), c.caster.getZ(), 1, 0, 0, 0, 0);
		}, fx(ModEffects.KNOCKDOWN, 2, 0));
		def(H, 9, c -> charge(c, 30, () -> {
			for (LivingEntity t : c.areaSelf(8, 1f)) {
				Ctx.push(t, c.caster.position(), 2.5, 1.0);
			}
			c.particles(ParticleTypes.EXPLOSION_EMITTER, c.caster.getX(), c.caster.getY(), c.caster.getZ(), 3, 2, 0, 2, 0);
			c.sound(SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.6f);
		}), fx(ModEffects.STUN, 4, 0), fx(ModEffects.KNOCKDOWN, 3, 0));
	}

	// ---------------------------------------------------------------------------------------------

	public static @Nullable Tech selected(ServerPlayer p) {
		String cat = ModItems.category(p.getMainHandItem());
		if (cat == null) {
			return null;
		}
		PlayerMagic pm = PlayerMagic.of(p);
		List<String> l = pm.techLoadout(cat);
		l.removeIf(String::isEmpty);
		if (l.isEmpty()) {
			return null;
		}
		int i = Math.floorMod(pm.techSel.getOrDefault(cat, 0), l.size());
		return get(l.get(i));
	}

	public static void cycle(ServerPlayer p, int dir) {
		String cat = ModItems.category(p.getMainHandItem());
		if (cat == null) {
			return;
		}
		PlayerMagic pm = PlayerMagic.of(p);
		pm.techSel.merge(cat, dir, Integer::sum);
		Tech t = selected(p);
		if (t != null) {
			Casting.msg(p, "▶ わざ: " + t.name, ChatFormatting.GOLD);
		}
		pm.dirty = true;
	}

	public static void use(ServerPlayer p) {
		ItemStack held = p.getMainHandItem();
		String cat = ModItems.category(held);
		if (cat == null) {
			Casting.msg(p, "物理わざは武器を持って使います", ChatFormatting.GRAY);
			return;
		}
		Tech t = selected(p);
		if (t == null) {
			Casting.msg(p, categoryName(cat) + " のわざが未装着です(鍛冶テーブルで習得・装着)", ChatFormatting.GRAY);
			return;
		}
		PlayerMagic pm = PlayerMagic.of(p);
		long now = p.level().getGameTime();
		if (now < pm.cooldownEnd("t:" + t.id)) {
			Casting.msg(p, t.name + ": クールタイム " + String.format("%.1f", (pm.cooldownEnd("t:" + t.id) - now) / 20f) + "秒",
					ChatFormatting.GRAY);
			return;
		}
		if (p.hasEffect(ModEffects.STUN) || p.hasEffect(ModEffects.PARALYSIS)) {
			return;
		}
		float st = Calc.techStamina(p, t, held);
		if (!p.isCreative() && pm.stamina < st) {
			Casting.msg(p, "スタミナが足りません", ChatFormatting.YELLOW);
			return;
		}
		if (!p.isCreative()) {
			pm.stamina -= st;
		}
		pm.cooldowns.put("t:" + t.id, now + Math.round(Calc.techCt(p, t, held) * 20));
		pm.lastCombat = now;
		Ctx c = new Ctx(p, t.spell, Calc.techPower(p, t, held));
		c.physical = true;
		c.durMult = 1 + 0.2f * Calc.weaponEnch(held, "bleed");
		t.spell.form.cast(c);
		addMastery(p, cat, 2);
		pm.dirty = true;
	}

	static void counter(ServerPlayer p, LivingEntity attacker) {
		Tech t = get("blade_6");
		if (t == null) {
			return;
		}
		Ctx c = new Ctx(p, t.spell, Math.max(4, PlayerMagic.of(p).counterDamage));
		c.physical = true;
		c.hit(attacker, 1.2f);
		c.particles(ParticleTypes.SWEEP_ATTACK, attacker.getX(), attacker.getY() + 1, attacker.getZ(), 3, 0.3, 0.3, 0.3, 0);
		c.sound(SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1f);
	}

	public static void addMastery(ServerPlayer p, String cat, int amount) {
		PlayerMagic pm = PlayerMagic.of(p);
		int before = pm.masteryLevel(cat);
		pm.mastery.merge(cat, amount, Integer::sum);
		int after = pm.masteryLevel(cat);
		if (after > before) {
			pm.skillPoints++;
			p.sendSystemMessage(net.minecraft.network.chat.Component.literal(categoryName(cat) + " の熟練度が Lv" + after
					+ " になった!(わざスロット +1 / スキルポイント +1)").withStyle(ChatFormatting.GOLD));
		}
	}

	/** 投擲武器を右クリックで投げる(ナイフ・手裏剣は消費、投げ槍は落ちる、ブーメランは戻る)。 */
	public static void throwWeapon(ServerPlayer p, InteractionHand hand) {
		ItemStack s = p.getItemInHand(hand);
		if (p.getCooldowns().isOnCooldown(s)) {
			return;
		}
		Balance b = Balance.get();
		float power = b.categoryBase.getOrDefault("throw", 7f) * b.res("light_attack_coef", 0.7f) * 1.4f;
		Spell sp = new Spell("t:throw_basic", "投擲", null, 0, "basic", "", "-", "-", 1, 0, 0, true);
		Ctx c = new Ctx(p, sp, power);
		c.physical = true;
		MagicBolt bolt = MagicBolt.create(c, 2.0f, 0.03f);
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		if (id.equals("boomerang")) {
			bolt.boomerang = true;
			bolt.life(30);
			p.getCooldowns().addCooldown(s, 30);
		} else if (id.equals("javelin")) {
			ItemStack drop = s.copyWithCount(1);
			bolt.impact = (cc, bb, pos, t) -> {
				if (t != null) {
					cc.hit(t, 1.6f, bb);
				}
				if (!p.isCreative()) {
					cc.level.addFreshEntity(new ItemEntity(cc.level, pos.x, pos.y + 0.2, pos.z, drop));
				}
			};
			if (!p.isCreative()) {
				s.shrink(1);
			}
			p.getCooldowns().addCooldown(s, 15);
		} else {
			if (!p.isCreative()) {
				s.shrink(1);
			}
			p.getCooldowns().addCooldown(s, 6);
		}
		p.level().addFreshEntity(bolt);
		p.level().playSound(null, p.blockPosition(), SoundEvents.TRIDENT_THROW.value(), p.getSoundSource(), 0.6f, 1.3f);
		addMastery(p, "throw", 1);
	}

	/** エアステップ: 空中でもう一回ジャンプ。 */
	public static void doubleJump(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (!p.hasEffect(ModEffects.DOUBLE_JUMP) || p.onGround() || pm.doubleJumpUsed) {
			return;
		}
		pm.doubleJumpUsed = true;
		Vec3 look = p.getLookAngle();
		p.setDeltaMovement(look.x * 0.4, 0.65, look.z * 0.4);
		p.syncVelocity = true;
		p.fallDistance = 0;
		((net.minecraft.server.level.ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 10, 0.3, 0.05,
				0.3, 0.02);
	}
}
