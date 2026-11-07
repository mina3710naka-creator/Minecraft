package com.magicmod;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 召喚=契約(使い魔)システム(設計書 4.7)。杖スロットとは別の契約スロットで、召喚中は MP を少しずつ使う。 */
public final class Familiars {

	public enum Kind {
		WOLF("wolf", 1, Archetype.PHYSICAL, 1, 2.0f),
		MUD_SLIME("mud_slime", 2, null, 1, 1.7f),
		FAIRY("fairy", 3, Archetype.MAGIC, 1, 1.7f),
		HAWK("hawk", 4, null, 1, 1.7f),
		GOLEM("golem", 5, Archetype.PHYSICAL, 1, 1.3f),
		SPIRIT_WOLVES("spirit_wolves", 6, Archetype.PHYSICAL, 1, 2.2f),
		ELEMENTAL("elemental", 7, Archetype.MAGIC, 1, 1.5f),
		GUARDIAN("guardian", 8, Archetype.PHYSICAL, 1, 1.8f),
		PHOENIX("phoenix", 9, Archetype.MAGIC, 1, 2.0f),
		ANCIENT_DRAGON("ancient_dragon", 10, null, 1, 1.3f),
		PEGASUS("pegasus", 11, Archetype.MAGIC, 1, 0.7f, new Balance.FamiliarRow(11, "ペガサス", "騎乗・支援", "空を駆ける翼の馬。近くにいる持ち主の落下を和らげる", 90, 8, 1.4f)),
		BEHEMOTH("behemoth", 12, Archetype.PHYSICAL, 1, 1.7f,
				new Balance.FamiliarRow(12, "ベヒーモス", "騎乗・タンク", "大地を駆ける巨獣。近くにいる持ち主の防御を高める", 200, 12, 1.6f));

		public final String id;
		public final int no;
		/** この型だと性能+10%・維持コスト-10%(設計書 13.4)。 */
		public final Archetype bonus;
		public final int count;
		public final float scale;

		/** balance.json に行がない種類(あとから足した使い魔)の数値。 */
		private final Balance.FamiliarRow own;

		Kind(String id, int no, Archetype bonus, int count, float scale) {
			this(id, no, bonus, count, scale, null);
		}

		Kind(String id, int no, Archetype bonus, int count, float scale, Balance.FamiliarRow own) {
			this.own = own;
			this.id = id;
			this.no = no;
			this.bonus = bonus;
			this.count = count;
			this.scale = scale;
		}

		public Balance.FamiliarRow row() {
			for (Balance.FamiliarRow r : Balance.get().familiars) {
				if (r.no() == no) {
					return r;
				}
			}
			return own != null ? own : new Balance.FamiliarRow(no, id, "", "", 30, 4, 1);
		}

		public String ja() {
			return row().name();
		}

		EntityType<? extends Mob> type() {
			return switch (this) {
				case WOLF -> ModEntities.FAM_WOLF;
				case SPIRIT_WOLVES -> ModEntities.FAM_SPIRIT_WOLF;
				case MUD_SLIME -> ModEntities.FAM_SLIME;
				case FAIRY -> ModEntities.FAM_FAIRY;
				case HAWK -> ModEntities.FAM_PARROT;
				case PHOENIX -> ModEntities.FAM_PHOENIX;
				case GOLEM -> ModEntities.FAM_GOLEM;
				case ELEMENTAL -> ModEntities.FAM_ELEMENTAL;
				case GUARDIAN -> ModEntities.FAM_GUARDIAN;
				case ANCIENT_DRAGON -> ModEntities.FAM_DRAGON;
				case PEGASUS -> ModEntities.FAM_PEGASUS;
				case BEHEMOTH -> ModEntities.FAM_BEHEMOTH;
			};
		}

		public static @Nullable Kind byId(String id) {
			for (Kind k : values()) {
				if (k.id.equals(id)) {
					return k;
				}
			}
			return null;
		}
	}

	private Familiars() {
	}

	public static int slots(PlayerMagic pm) {
		return Math.min(2, 1 + Math.round(Gear.stat(pm, Gear.Stat.FAMILIAR_SLOT)) + pm.skill("c_familiar"));
	}

	/** 進化段階(1〜4)。レベルが上がると見た目が変わる。 */
	public static int stageOf(int level) {
		return level >= 10 ? 4 : level >= 6 ? 3 : level >= 3 ? 2 : 1;
	}

	/** 乗れる使い魔と、乗れるようになるレベル。 */
	public static boolean rideable(Kind k) {
		return k == Kind.WOLF || k == Kind.SPIRIT_WOLVES || k == Kind.GOLEM || k == Kind.PHOENIX || k == Kind.ANCIENT_DRAGON || k == Kind.PEGASUS || k == Kind.BEHEMOTH;
	}

	public static boolean flies(Kind k) {
		return k == Kind.PHOENIX || k == Kind.ANCIENT_DRAGON || k == Kind.PEGASUS;
	}

	static double rideSpeed(Kind k) {
		return switch (k) {
			case WOLF, SPIRIT_WOLVES -> 0.42;
			case GOLEM -> 0.26;
			case BEHEMOTH -> 0.36;
			case PEGASUS -> 0.85;
			case PHOENIX -> 0.55;
			default -> 0.7;
		};
	}

	/** 各レベルに必要な累積経験値。Lv5 までは 1 レベル 100(えさでも届く)、それ以降は 1 レベルごとに必要量が増える。 */
	public static final int[] LEVEL_EXP = { 0, 100, 200, 300, 400, 550, 770, 1070, 1470, 1990 };

	public static int level(PlayerMagic pm, Kind k) {
		int exp = pm.familiarExp.getOrDefault(k.id, 0), lv = 1;
		for (int i = 1; i < LEVEL_EXP.length; i++) {
			if (exp >= LEVEL_EXP[i]) {
				lv = i + 1;
			}
		}
		return lv;
	}

	/** 性能の倍率: Lv1 は弱く(0.45)、Lv5 で 1.0(ゾンビ数体を倒せる)、その先は急に伸びて Lv10 で約 6.2(ゴーレムを一撃・ウォーデンに勝てる)。 */
	static float levelMult(int lv) {
		return lv <= 5 ? 0.45f + 0.1375f * (lv - 1) : 1f + 0.7f * (float) Math.pow(lv - 5, 1.25);
	}

	static float perf(PlayerMagic pm, Kind k) {
		float m = levelMult(level(pm, k));
		if (k.bonus != null && pm.archetype == k.bonus) {
			m *= 1.1f;
		}
		return m;
	}

	public static float upkeep(PlayerMagic pm, Kind k) {
		float u = k.row().upkeep();
		if (k.bonus != null && pm.archetype == k.bonus) {
			u *= 0.9f;
		}
		return u;
	}

	// ---------------------------------------------------------------------------------------------

	public static @Nullable LivingEntity ownerOf(LivingEntity e) {
		if (e instanceof MobClasses.Fam f && f.mmOwner() != null) {
			return e.level().getPlayerByUUID(f.mmOwner());
		}
		return null;
	}

	public static boolean contract(ServerPlayer p, Kind k) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (pm.contracts.contains(k.id)) {
			Casting.msg(p, k.ja() + " とはすでに契約しています", ChatFormatting.GRAY);
			return false;
		}
		if (pm.contracts.size() >= slots(pm)) {
			Casting.msg(p, "契約スロットがいっぱいです(ステータス画面 [K] で解約できます)", ChatFormatting.RED);
			return false;
		}
		pm.contracts.add(k.id);
		p.sendSystemMessage(Component.literal(k.ja() + " と契約した! [H] で召喚/送還").withStyle(ChatFormatting.AQUA));
		p.level().playSound(null, p.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, p.getSoundSource(), 0.8f, 1.2f);
		return true;
	}

	public static void toggle(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (!pm.activeFamiliars.isEmpty()) {
			dismissAll(p);
			pm.familiarsOut = false;
			Casting.msg(p, "使い魔を送還しました", ChatFormatting.GRAY);
			return;
		}
		summonAll(p, pm);
	}

	/** 攻撃モーション(モデルの振り)を始める。長さは少しゆっくりめ。 */
	static void swing(Mob m) {
		m.swing(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.component.SwingAnimation(net.minecraft.world.item.SwingAnimationType.WHACK, 9), true);
	}

	private static final java.util.Map<UUID, Long> LUNGE = new java.util.HashMap<>(), FANG = new java.util.HashMap<>(), CLAW = new java.util.HashMap<>();

	private static int wolfStage(Mob m) {
		var n = m.getCustomName();
		if (n != null) {
			var mt = java.util.regex.Pattern.compile("Lv(\\d+)").matcher(n.getString());
			if (mt.find()) {
				return stageOf(Integer.parseInt(mt.group(1)));
			}
		}
		return 1;
	}

	/** ウルフ系が噛みついたとき: 出血を重ね、段階が上がると追加のダメージと体力吸収。 */
	static void onBite(Mob wolf, LivingEntity target) {
		if (!(wolf instanceof MobClasses.Fam f) || !(wolf.level() instanceof ServerLevel level)) {
			return;
		}
		int stage = wolfStage(wolf);
		ModEffects.stack(target, ModEffects.BLEED, 100 + stage * 20, 3 + stage);
		Vfx.near(level, ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 4, 0.3, 0.3, 0.3, 0.1);
		if (stage >= 3) {
			wolf.heal(wolf.getMaxHealth() * 0.04f);
		}
		if (stage >= 2 && ownerOf(wolf) instanceof ServerPlayer p) {
			Ctx c = new Ctx(p, Spells.get(Element.WATER, 1), f.mmKind().row().attack() * perf(PlayerMagic.of(p), f.mmKind()) * (0.6f + 0.3f * stage));
			c.hit(target, 1f);
		}
	}

	/** ウルフ系の戦い方。段階 2=飛びかかり、3=爪の連撃と群れの鼓舞、4=ファントムファング。 */
	static void wolfCombat(Mob m, ServerPlayer p, ServerLevel level, long t, @Nullable LivingEntity target, int stage, float perf, Kind k) {
		long now = level.getGameTime();
		if (stage >= 2 && t % 20 == 0 && m.getHealth() < m.getMaxHealth()) {
			m.heal(1f + stage);
		}
		if (stage >= 3 && t % 40 == 0 && m.distanceToSqr(p) < 256) {
			p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0, false, false));
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		double d2 = m.distanceToSqr(target);
		float power = k.row().attack() * perf * 1.5f + 6f;
		Vec3 to = target.position().subtract(m.position());
		Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
		// 飛びかかり(段階 2 から)
		if (stage >= 2 && d2 > 9 && d2 < 100 && now >= LUNGE.getOrDefault(m.getUUID(), 0L) && m.onGround()) {
			LUNGE.put(m.getUUID(), now + 80 - stage * 10);
			m.setDeltaMovement(dir.x * 1.3, 0.4, dir.z * 1.3);
			swing(m);
			m.setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
			Vfx.sound(level, m.position(), ModSounds.RIDE_DASH, 0.8f, 1.5f);
			Ctx c = new Ctx(p, Spells.get(Element.WATER, 1), power);
			Tasks.later(8, () -> {
				if (m.isAlive() && target.isAlive() && m.distanceToSqr(target) < 12) {
					c.hit(target, 1.8f);
					ModEffects.apply(target, ModEffects.KNOCKDOWN, 30, 0);
					Vfx.slash(m, dir, 3.4, 70, Element.WATER, 0.6);
					Vfx.shake(level, m.position(), 10, 0.4f, 4);
				}
			});
		}
		// 爪の連撃(段階 3 から)
		if (stage >= 3 && d2 < 12 && now >= CLAW.getOrDefault(m.getUUID(), 0L)) {
			CLAW.put(m.getUUID(), now + 36);
			Ctx c = new Ctx(p, Spells.get(Element.WATER, 1), power);
			for (int i = 0; i < 3; i++) {
				int q = i;
				Tasks.later(i * 3, () -> {
					if (m.isAlive()) {
						swing(m);
						Vfx.slash(m, dir, 3.6, 75, Element.WATER, q % 2 == 0 ? 0.9 : -0.9);
						c.areaAt(m.position().add(dir.scale(1.5)), 3.2, 0.45f);
					}
				});
			}
		}
		// ファントムファング(段階 4)
		if (stage >= 4 && d2 < 144 && now >= FANG.getOrDefault(m.getUUID(), 0L)) {
			FANG.put(m.getUUID(), now + 120);
			swing(m);
			Ctx c = new Ctx(p, Spells.get(Element.DARK, 1), power * 1.4f);
			Vfx.sound(level, m.position(), ModSounds.BOSS_ROAR, 1f, 1.5f);
			FxDisplay.circle(level, target.position().add(0, 0.05, 0), Element.WATER, 4, 20, 5f);
			for (int i = 0; i < 4; i++) {
				int q = i;
				Tasks.later(6 + i * 4, () -> {
					if (target.isAlive()) {
						double a = q * Math.PI / 2;
						Vec3 from = target.position().add(Math.cos(a) * 3, 1, Math.sin(a) * 3);
						Vfx.line(level, from, target.position().add(0, 1, 0), ParticleTypes.SOUL_FIRE_FLAME, 0.3);
						Vfx.burstOut(level, from, ParticleTypes.SOUL_FIRE_FLAME, 8, 0.2);
						c.hit(target, 1.2f);
						Vfx.impact(level, target.position().add(0, 1, 0), Element.WATER, 1.2f);
					}
				});
			}
		}
	}

	/** 使い魔のまとうオーラ(種類ごと・進化段階が上がるほど濃くなる)。 */
	static void aura(Mob m, Kind k, int stage, long t) {
		if (stage < 2 || t % 4 != 0 || !(m.level() instanceof ServerLevel l)) {
			return;
		}
		double w = m.getBbWidth() * 0.5, h = m.getBbHeight();
		int n = stage - 1;
		double x = m.getX(), y = m.getY(), z = m.getZ();
		var r = m.getRandom();
		switch (k) {
			case WOLF, SPIRIT_WOLVES -> Vfx.near(l, ParticleTypes.SOUL_FIRE_FLAME, x + r.nextGaussian() * w, y + h * 0.5, z + r.nextGaussian() * w, n, 0.2, 0.3, 0.2, 0.01);
			case MUD_SLIME -> Vfx.near(l, ParticleTypes.ITEM_SLIME, x, y + h * 0.5, z, n, w, 0.3, w, 0);
			case FAIRY -> Vfx.near(l, ParticleTypes.END_ROD, x, y + h * 0.6, z, n, w, 0.4, w, 0.01);
			case HAWK -> Vfx.near(l, ParticleTypes.CLOUD, x, y + h * 0.4, z, n, w, 0.2, w, 0.02);
			case GOLEM -> Vfx.near(l, ParticleTypes.CRIT, x, y + h * 0.7, z, n, w, 0.5, w, 0.02);
			case ELEMENTAL -> Vfx.near(l, ParticleTypes.FLAME, x, y + h * 0.5, z, n * 2, w, 0.5, w, 0.02);
			case GUARDIAN -> Vfx.near(l, ParticleTypes.ENCHANT, x, y + h * 0.7, z, n * 3, w, 0.5, w, 0.5);
			case PHOENIX -> {
				Vfx.near(l, ParticleTypes.FLAME, x, y + h * 0.5, z, n * 2, w, 0.3, w, 0.03);
				Vfx.near(l, ParticleTypes.LAVA, x, y + h * 0.3, z, 1, w, 0.2, w, 0);
			}
			case ANCIENT_DRAGON -> {
				Element e = Element.values()[(int) (t / 40 % 8)];
				Vfx.near(l, Fx.of(e), x, y + h * 0.5, z, n * 2, w * 1.2, 0.8, w * 1.2, 0.03);
			}
			case PEGASUS -> {
				Vfx.near(l, ParticleTypes.END_ROD, x, y + h * 0.4, z, n, w * 1.2, 0.4, w * 1.2, 0.01);
				Vfx.near(l, ParticleTypes.CLOUD, x, y + 0.2, z, 1, w, 0.1, w, 0);
			}
			case BEHEMOTH -> Vfx.near(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.3, z, n, w, 0.1, w, 0.01);
		}
	}

	/** 進化(Lv3・6・10)の演出。 */
	static void evolveFx(ServerPlayer p, Entity e, Kind k, int stage) {
		ServerLevel lv = (ServerLevel) p.level();
		Element el = k == Kind.PHOENIX ? Element.FIRE : k == Kind.PEGASUS || k == Kind.FAIRY || k == Kind.GUARDIAN ? Element.LIGHT : k == Kind.ANCIENT_DRAGON ? Element.GRAVITY : Element.WIND;
		Vec3 at = e.position();
		FxDisplay.circle(lv, at.add(0, 0.05, 0), el, 3 + stage, 40, 5f);
		FxDisplay.ring(lv, at, el, 4 + stage * 1.5, 12);
		Vfx.pillar(lv, at, 8 + stage * 2, 1.2, ParticleTypes.END_ROD, 8);
		Vfx.burstOut(lv, at.add(0, e.getBbHeight() / 2, 0), ParticleTypes.TOTEM_OF_UNDYING, 40, 0.5);
		Vfx.flash(lv, at, 24, 0xFFFFFF, 6);
		Vfx.sound(lv, at, ModSounds.EVOLVE, 1.2f, 1f);
		String[] names = { "", "", "第二形態", "第三形態", "最終形態" };
		p.sendSystemMessage(Component.literal("◆ " + k.ja() + " が進化した!(" + names[Math.min(4, stage)] + ")").withStyle(ChatFormatting.GOLD));
		BossFx.title(lv, at, 24, Component.literal(k.ja()).withStyle(ChatFormatting.GOLD), Component.literal(names[Math.min(4, stage)]), 60);
	}

	/** 使い魔のスキル([J])。クールタイムは 30 秒から、使い魔の Lv で短くなる。 */
	public static void skill(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		ServerLevel level = (ServerLevel) p.level();
		Mob m = null;
		Kind k = null;
		for (UUID id : pm.activeFamiliars) {
			Entity e = level.getEntity(id);
			if (e instanceof MobClasses.Fam f && e instanceof Mob mm) {
				m = mm;
				k = f.mmKind();
			}
		}
		if (m == null) {
			Casting.msg(p, "使い魔を呼んでいません([H] で召喚)", ChatFormatting.GRAY);
			return;
		}
		long now = level.getGameTime();
		if (now < pm.cooldownEnd("famskill")) {
			Casting.msg(p, "スキルは回復中(" + (pm.cooldownEnd("famskill") - now) / 20 + "秒)", ChatFormatting.GRAY);
			return;
		}
		int lv = level(pm, k);
		float perf = perf(pm, k);
		Vec3 at = p.position();
		boolean used = true;
		switch (k) {
			case WOLF -> {
				for (LivingEntity a : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10), e -> Combat.isAlly(p, e))) {
					a.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
					a.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
				}
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(10), e -> Combat.isEnemy(p, e))) {
					ModEffects.apply(e, ModEffects.FEAR, 80, 0);
				}
				Vfx.sound(level, m.position(), ModSounds.BOSS_ROAR, 1.2f, 1.4f);
				FxDisplay.ring(level, m.position(), Element.WIND, 8, 10);
				Casting.msg(p, "ウルフの咆哮!(加速・攻撃力上昇、敵は恐怖)", ChatFormatting.AQUA);
			}
			case MUD_SLIME -> {
				Vec3 c = m.position();
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(7), e -> Combat.isEnemy(p, e))) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 3));
				}
				p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, Math.min(4, (int) perf + 1)));
				FxDisplay.circle(level, c.add(0, 0.05, 0), Element.WATER, 7, 160, 2f);
				Vfx.burstOut(level, c.add(0, 1, 0), ParticleTypes.ITEM_SLIME, 40, 0.5);
				Casting.msg(p, "粘液の壁!(周囲の敵を鈍足に、あなたに吸収)", ChatFormatting.AQUA);
			}
			case FAIRY -> {
				for (LivingEntity a : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9), e -> Combat.isAlly(p, e))) {
					a.heal(10f * perf);
					Ctx.cleanse(a);
					level.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + a.getBbHeight() + 0.2, a.getZ(), 5, 0.3, 0.2, 0.3, 0);
				}
				Vfx.pillar(level, at, 6, 1.5, ParticleTypes.END_ROD, 6);
				Vfx.sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1f, 1.6f);
				Casting.msg(p, "癒しの光!(範囲回復・状態異常の解除)", ChatFormatting.AQUA);
			}
			case HAWK -> {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48), e -> Combat.isEnemy(p, e))) {
					e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false));
				}
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
				Vfx.sound(level, m.position(), SoundEvents.PARROT_IMITATE_PHANTOM, 1f, 1.5f);
				Casting.msg(p, "鷹の目!(広範囲の敵を発光表示・暗視)", ChatFormatting.AQUA);
			}
			case GUARDIAN -> {
				for (LivingEntity a : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9), e -> Combat.isAlly(p, e))) {
					a.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, Math.min(4, (int) perf + 1)));
					a.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 200, 1));
				}
				FxDisplay.circle(level, at.add(0, 0.05, 0), Element.LIGHT, 9, 120, 3f);
				FxDisplay.ring(level, at, Element.LIGHT, 9, 12);
				Casting.msg(p, "守護の結界!(味方に吸収と防御)", ChatFormatting.AQUA);
			}
			case ELEMENTAL -> {
				var held = p.getMainHandItem();
				Element el = held.isEmpty() ? Element.FIRE : Books.firstElement(held);
				if (el == null) {
					el = Element.FIRE;
				}
				Spell sp = Spells.get(el, 1);
				Ctx c = new Ctx(p, sp, k.row().attack() * perf * 2.2f + 8f);
				LivingEntity tg = m.getTarget();
				Vec3 c0 = tg != null ? tg.position() : c.aimPoint(16);
				Vfx.sound(level, c0, ModSounds.MAX_RELEASE, 1.2f, 1.2f);
				FxDisplay.circle(level, c0.add(0, 0.05, 0), el, 6, 30, 4f);
				Element fe = el;
				Tasks.later(20, () -> {
					c.areaAt(c0, 6, 1.6f);
					FxDisplay.ring(level, c0, fe, 7, 10);
					Vfx.pillar(level, c0, 12, 1.5, Fx.of(fe), 6);
				});
				Casting.msg(p, "エレメンタルの大爆発!(" + el.ja + ")", el.fmt);
			}
			default -> {
				used = false;
				Casting.msg(p, k.ja() + " は、騎乗中の右クリックで専用攻撃を使います", ChatFormatting.GRAY);
			}
		}
		if (used) {
			swing(m);
			Vfx.sound(level, m.position(), ModSounds.SKILL_USE, 1f, 1f);
			pm.cooldowns.put("famskill", now + Math.max(300, 600 - lv * 25));
		}
	}

	/** [H]+ホイール: 召喚する使い魔を選ぶ。出しているときは、その場で入れ替える。 */
	public static void pick(ServerPlayer p, int dir) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (pm.contracts.isEmpty()) {
			Casting.msg(p, "契約している使い魔がいません", ChatFormatting.GRAY);
			return;
		}
		int n = pm.contracts.size();
		pm.familiarPick = Math.floorMod(pm.familiarPick + dir, n);
		StringBuilder sb = new StringBuilder("使い魔: ");
		for (int i = 0; i < n; i++) {
			Kind k = Kind.byId(pm.contracts.get(i));
			sb.append(i == pm.familiarPick ? "§b▶ " : "§7").append(k == null ? "?" : k.ja()).append(i == pm.familiarPick ? "§r  " : "  ");
		}
		Casting.msg(p, sb.toString(), ChatFormatting.WHITE);
		p.level().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), p.getSoundSource(), 0.3f, 1.6f);
		if (!pm.activeFamiliars.isEmpty()) {
			dismissAll(p);
			pm.familiarsOut = false;
			summonAll(p, pm);
		}
	}

	/** 契約している使い魔を全部呼ぶ(ログインし直したときなどにも使う)。 */
	static void summonAll(ServerPlayer p, PlayerMagic pm) {
		if (pm.contracts.isEmpty()) {
			Casting.msg(p, "契約している使い魔がいません(召喚祭壇で契約書を作る)", ChatFormatting.GRAY);
			return;
		}
		// 使い魔は同時に 1 体だけ。契約が複数あるときは、送還するたびに順番に切り替わる
		Kind pick = Kind.byId(pm.contracts.get(Math.floorMod(pm.familiarPick, pm.contracts.size())));
		if (pick == null) {
			return;
		}
		List<Kind> want = List.of(pick);
		long now = p.level().getGameTime();
		for (Kind k : want) {
			long cd = pm.familiarCooldown.getOrDefault(k.id, 0L);
			if (now < cd) {
				Casting.msg(p, k.ja() + " は回復中です(" + (cd - now) / 20 + "秒)", ChatFormatting.GRAY);
				continue;
			}
			for (int i = 0; i < k.count; i++) {
				summon(p, pm, k);
			}
		}
		pm.familiarsOut = !pm.activeFamiliars.isEmpty();
	}

	/** ログインし直し・復活・次元移動のあと、出していた使い魔を呼び直す。 */
	static void initPersistence() {
		initRide();
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> resummonLater(h.player, 40));
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> resummonLater(newP, 60));
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((p, from, to) -> {
			PlayerMagic pm = PlayerMagic.of(p);
			boolean out = pm.familiarsOut;
			pm.activeFamiliars.clear();
			pm.familiarsOut = out;
			resummonLater(p, 20);
		});
	}

	private static void resummonLater(ServerPlayer p, int ticks) {
		Tasks.later(ticks, () -> {
			PlayerMagic pm = PlayerMagic.of(p);
			if (!p.isRemoved() && p.isAlive() && pm.familiarsOut && pm.activeFamiliars.isEmpty() && !pm.contracts.isEmpty()) {
				summonAll(p, pm);
				if (!pm.activeFamiliars.isEmpty()) {
					Casting.msg(p, "使い魔が戻ってきた", ChatFormatting.AQUA);
				}
			}
		});
	}

	private static void summon(ServerPlayer p, PlayerMagic pm, Kind k) {
		ServerLevel level = (ServerLevel) p.level();
		Mob m = k.type().create(level, EntitySpawnReason.MOB_SUMMONED);
		if (m == null) {
			return;
		}
		Vec3 pos = p.position().add(p.getRandom().nextGaussian(), 0.5, p.getRandom().nextGaussian());
		m.snapTo(pos.x, pos.y, pos.z, p.getYRot(), 0);
		((MobClasses.Fam) m).mmSet(p.getUUID(), k);
		Balance.FamiliarRow r = k.row();
		float perf = perf(pm, k);
		float hp = r.hp() * perf;
		if (k == Kind.SPIRIT_WOLVES) {
			hp *= 1f;
		}
		if (m.getAttribute(Attributes.MAX_HEALTH) != null) {
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024, hp));
		}
		if (m.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
			m.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Math.max(0.5, r.attack() * perf));
		}
		if (m.getAttribute(Attributes.SCALE) != null) {
			m.getAttribute(Attributes.SCALE).setBaseValue(k.scale * (1 + 0.1f * (stageOf(level(pm, k)) - 1)));
		}
		if (k != Kind.WOLF && k != Kind.SPIRIT_WOLVES) {
			// ほかの使い魔も全体に底上げ(体力 ×1.5、攻撃 ×1.6)
			if (m.getAttribute(Attributes.MAX_HEALTH) != null) {
				m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024, hp * 1.5f));
			}
			if (m.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
				m.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Math.max(0.5, r.attack() * perf * 1.6f));
			}
		}
		if (k == Kind.WOLF || k == Kind.SPIRIT_WOLVES) {
			// ウルフ系は前線の主力: 体力・攻撃力・足を大きく底上げ
			if (m.getAttribute(Attributes.MAX_HEALTH) != null) {
				m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024, hp * 2.4f));
			}
			if (m.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
				m.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(Math.max(0.5, r.attack() * perf * 2.6f));
			}
			if (m.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
				m.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.42);
			}
			if (m.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
				m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.5);
			}
		}
		m.setHealth(m.getMaxHealth());
		if (m instanceof Slime s) {
			s.setSize(2, false);
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024, hp));
			m.setHealth(m.getMaxHealth());
		}
		if (m instanceof Parrot par) {
			par.setComponent(net.minecraft.core.component.DataComponents.PARROT_VARIANT, k == Kind.PHOENIX ? Parrot.Variant.RED_BLUE : Parrot.Variant.GRAY);
		}
		Glow.apply(m, k == Kind.PEGASUS ? Element.LIGHT : k == Kind.BEHEMOTH ? Element.GRAVITY : k == Kind.PHOENIX ? Element.FIRE : k == Kind.ANCIENT_DRAGON ? Element.GRAVITY : k == Kind.FAIRY || k == Kind.GUARDIAN
				? Element.LIGHT : k == Kind.HAWK ? Element.WIND : k == Kind.ELEMENTAL ? Element.THUNDER : Element.WATER);
		m.setCustomName(Component.literal(k.ja() + " Lv" + level(pm, k)).withStyle(k == Kind.ANCIENT_DRAGON ? ChatFormatting.GOLD : ChatFormatting.AQUA));
		m.setCustomNameVisible(false);
		m.setPersistenceRequired();
		level.addFreshEntity(m);
		pm.activeFamiliars.add(m.getUUID());
		int col = k == Kind.PHOENIX ? 0xFF5A1A : k == Kind.ANCIENT_DRAGON ? 0xB040FF : k == Kind.FAIRY || k == Kind.GUARDIAN ? 0xFFF2A0 : 0x60E0C0;
		Vfx.magicCircle(level, pos.subtract(0, 0.5, 0), k == Kind.ANCIENT_DRAGON ? 3.5 : 1.6, level.getGameTime() * 0.1, col, 0xFFFFFF);
		Element fe = k == Kind.PHOENIX ? Element.FIRE : k == Kind.ANCIENT_DRAGON ? Element.GRAVITY : k == Kind.FAIRY || k == Kind.GUARDIAN
				? Element.LIGHT : Element.WIND;
		FxDisplay.circle(level, pos.subtract(0, 0.45, 0), fe, k == Kind.ANCIENT_DRAGON ? 4 : 1.8, 30, 6f);
		Vfx.pillar(level, pos.subtract(0, 0.5, 0), k == Kind.ANCIENT_DRAGON ? 8 : 3, 0.6, Vfx.fade(0xFFFFFF, col, 1.2f), 3);
		Vfx.burstOut(level, pos.add(0, 0.5, 0), ParticleTypes.END_ROD, 12, 0.2);
		level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.5, pos.z, 15, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, p.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, p.getSoundSource(), 0.6f, 1.4f);
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, p.getSoundSource(), 1f, 0.8f);
		if (k == Kind.ANCIENT_DRAGON) {
			level.playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, p.getSoundSource(), 1f, 1.2f);
			Vfx.shake(level, pos, 24, 0.8f, 10);
		}
	}

	public static void dismissAll(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		ServerLevel level = (ServerLevel) p.level();
		for (UUID id : pm.activeFamiliars) {
			Entity e = level.getEntity(id);
			if (e != null) {
				Vfx.vortex(level, e.position().add(0, 0.5, 0), 1.5, ParticleTypes.END_ROD, 12);
				level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
				e.discard();
			}
		}
		pm.activeFamiliars.clear();
	}

	/** フェニックスと契約&召喚中なら、身代わりにして契約を失う。 */
	public static boolean consumePhoenix(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		ServerLevel level = (ServerLevel) p.level();
		for (UUID id : List.copyOf(pm.activeFamiliars)) {
			Entity e = level.getEntity(id);
			if (e instanceof MobClasses.Fam f && f.mmKind() == Kind.PHOENIX) {
				e.discard();
				pm.activeFamiliars.remove(id);
				pm.contracts.remove(Kind.PHOENIX.id);
				return true;
			}
		}
		return false;
	}

	public static void addExp(Entity killer, int amount) {
		if (killer instanceof MobClasses.Fam f && killer instanceof LivingEntity le && ownerOf(le) instanceof ServerPlayer p) {
			PlayerMagic pm = PlayerMagic.of(p);
			int before = level(pm, f.mmKind());
			pm.familiarExp.merge(f.mmKind().id, amount, Integer::sum);
			int after = level(pm, f.mmKind());
			if (after > before) {
				p.sendSystemMessage(Component.literal(f.mmKind().ja() + " が Lv" + after + " になった!(次の召喚から反映)").withStyle(ChatFormatting.AQUA));
			}
		}
	}

	/** 持ち主側の毎 tick(維持 MP・やられた使い魔の後片付け)。 */
	static void tickOwner(ServerPlayer p, PlayerMagic pm) {
		if (pm.activeFamiliars.isEmpty()) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		float upkeep = 0;
		EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
		for (UUID id : List.copyOf(pm.activeFamiliars)) {
			Entity e = level.getEntity(id);
			if (!(e instanceof MobClasses.Fam f) || !e.isAlive()) {
				pm.activeFamiliars.remove(id);
				if (e instanceof MobClasses.Fam f2) {
					pm.familiarCooldown.put(f2.mmKind().id, level.getGameTime() + 20 * 60);
				}
				continue;
			}
			kinds.add(f.mmKind());
		}
		for (Kind k : kinds) {
			upkeep += upkeep(pm, k);
		}
		if (!p.isCreative()) {
			pm.mp -= upkeep / 20f;
			if (pm.mp <= 0) {
				pm.mp = 0;
				dismissAll(p);
				pm.familiarsOut = false;
				Casting.msg(p, "MPが尽きて、使い魔が帰ってしまった", ChatFormatting.BLUE);
			}
		}
	}

	public static List<String> describeActive(ServerPlayer p, PlayerMagic pm) {
		List<String> out = new ArrayList<>();
		ServerLevel level = (ServerLevel) p.level();
		for (UUID id : pm.activeFamiliars) {
			Entity e = level.getEntity(id);
			if (e instanceof MobClasses.Fam f && e instanceof LivingEntity le) {
				out.add(f.mmKind().ja() + " " + Math.round(le.getHealth()) + "/" + Math.round(le.getMaxHealth()));
			}
		}
		return out;
	}

	// ---------------------------------------------------------------------------------------------
	// 使い魔の行動

	/** 持ち主についていく。 */
	public static class FollowGoal extends Goal {
		private final PathfinderMob mob;

		public FollowGoal(PathfinderMob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			LivingEntity o = ownerOf(mob);
			return o != null && !mob.isVehicle() && mob.getTarget() == null && mob.distanceToSqr(o) > 25;
		}

		@Override
		public void tick() {
			LivingEntity o = ownerOf(mob);
			if (o != null) {
				mob.getNavigation().moveTo(o, 1.25);
			}
		}
	}

	/** 騎乗中の操作(持ち主の入力で動かす)。乗っているあいだは true を返し、通常の AI は止まる。 */
	private static boolean rideTick(Mob m, ServerPlayer p, Kind k) {
		boolean ridden = p.getVehicle() == m;
		if (!ridden) {
			if (m.entityTags().contains("mm_ridden")) {
				m.removeTag("mm_ridden");
				m.setNoGravity(false);
			}
			return false;
		}
		if (!m.entityTags().contains("mm_ridden")) {
			m.addTag("mm_ridden");
		}
		m.setTarget(null);
		m.getNavigation().stop();
		var in = p.getLastClientInput();
		double fwd = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0), side = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
		float yaw = p.getYRot();
		m.setYRot(yaw);
		m.yRotO = yaw;
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		double sp = rideSpeed(k);
		Long dash = DASH.get(m.getUUID());
		if (dash != null && p.level().getGameTime() < dash) {
			sp *= 2.6;
			fwd = 1;
		}
		double rad = Math.toRadians(yaw);
		Vec3 f = new Vec3(-Math.sin(rad), 0, Math.cos(rad)), r = new Vec3(Math.cos(rad), 0, Math.sin(rad));
		Vec3 move = f.scale(fwd).add(r.scale(side * -1));
		Vec3 cur = m.getDeltaMovement();
		if (flies(k)) {
			m.setNoGravity(true);
			Vec3 look = p.getLookAngle();
			Vec3 want = Vec3.ZERO;
			if (fwd != 0 || side != 0) {
				want = new Vec3(move.x, fwd * look.y, move.z).normalize().scale(sp);
			}
			if (in.jump()) {
				want = want.add(0, sp * 0.6, 0);
			}
			m.setDeltaMovement(cur.scale(0.6).add(want.scale(0.4)));
		} else {
			double nx = move.lengthSqr() > 0 ? move.normalize().x * sp : 0, nz = move.lengthSqr() > 0 ? move.normalize().z * sp : 0;
			double y = cur.y;
			if (in.jump() && m.onGround()) {
				y = k == Kind.GOLEM ? 0.5 : 0.62;
			}
			m.setDeltaMovement(cur.x * 0.5 + nx * 0.5, y, cur.z * 0.5 + nz * 0.5);
		}
		m.fallDistance = 0;
		p.fallDistance = 0;
		return true;
	}

	/** 種類ごとの好物。あげると経験値が入る(Lv5 まではしっかり、それ以降はほんの少し)。 */
	public static List<net.minecraft.world.item.Item> foods(Kind k) {
		return switch (k) {
			case WOLF -> List.of(net.minecraft.world.item.Items.BEEF, net.minecraft.world.item.Items.COOKED_BEEF, net.minecraft.world.item.Items.PORKCHOP,
					net.minecraft.world.item.Items.COOKED_PORKCHOP, net.minecraft.world.item.Items.MUTTON, net.minecraft.world.item.Items.COOKED_MUTTON);
			case MUD_SLIME -> List.of(net.minecraft.world.item.Items.SLIME_BALL, net.minecraft.world.item.Items.CLAY_BALL);
			case FAIRY -> List.of(net.minecraft.world.item.Items.HONEY_BOTTLE, net.minecraft.world.item.Items.SWEET_BERRIES,
					net.minecraft.world.item.Items.GLOW_BERRIES);
			case HAWK -> List.of(net.minecraft.world.item.Items.WHEAT_SEEDS, net.minecraft.world.item.Items.BEETROOT_SEEDS,
					net.minecraft.world.item.Items.MELON_SEEDS, net.minecraft.world.item.Items.PUMPKIN_SEEDS);
			case GOLEM -> List.of(net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.COPPER_INGOT, net.minecraft.world.item.Items.COAL);
			case SPIRIT_WOLVES -> List.of(net.minecraft.world.item.Items.BONE, net.minecraft.world.item.Items.ROTTEN_FLESH,
					net.minecraft.world.item.Items.SPIDER_EYE);
			case ELEMENTAL -> List.of(net.minecraft.world.item.Items.BLAZE_POWDER, net.minecraft.world.item.Items.REDSTONE,
					net.minecraft.world.item.Items.LAPIS_LAZULI);
			case GUARDIAN -> List.of(net.minecraft.world.item.Items.PRISMARINE_SHARD, net.minecraft.world.item.Items.PRISMARINE_CRYSTALS);
			case PHOENIX -> List.of(net.minecraft.world.item.Items.MAGMA_CREAM, net.minecraft.world.item.Items.BLAZE_ROD, net.minecraft.world.item.Items.FIRE_CHARGE);
			case ANCIENT_DRAGON -> List.of(net.minecraft.world.item.Items.ENDER_PEARL, net.minecraft.world.item.Items.CHORUS_FRUIT,
					net.minecraft.world.item.Items.DIAMOND);
			case PEGASUS -> List.of(net.minecraft.world.item.Items.GOLDEN_CARROT, net.minecraft.world.item.Items.APPLE, net.minecraft.world.item.Items.SUGAR);
			case BEHEMOTH -> List.of(net.minecraft.world.item.Items.WHEAT, net.minecraft.world.item.Items.BREAD, net.minecraft.world.item.Items.CARROT);
		};
	}

	static String foodNames(Kind k) {
		StringBuilder sb = new StringBuilder();
		for (var it : foods(k)) {
			if (sb.length() > 0) {
				sb.append("・");
			}
			sb.append(it.getDefaultInstance().getHoverName().getString());
		}
		return sb.toString();
	}

	/** えさ: Lv5(経験値 400)までは 1 個で +40。それを超えたら 1 個で +2 だけ。 */
	static final int FEED_CAP = 400;

	static void feed(ServerPlayer p, Entity entity, Kind k, net.minecraft.world.item.ItemStack stack) {
		PlayerMagic pm = PlayerMagic.of(p);
		int before = level(pm, k);
		int exp = pm.familiarExp.getOrDefault(k.id, 0);
		boolean capped = exp >= FEED_CAP;
		exp = capped ? exp + 2 : Math.min(FEED_CAP, exp + 40);
		pm.familiarExp.put(k.id, exp);
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		ServerLevel lv = (ServerLevel) p.level();
		lv.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(), capped ? 1 : 5, 0.3, 0.2, 0.3, 0);
		lv.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EAT.value(), p.getSoundSource(), 0.8f, 1.2f);
		int after = level(pm, k);
		if (entity instanceof Mob m) {
			refresh(m, pm, k);
		}
		if (after > before) {
			p.sendSystemMessage(Component.literal(k.ja() + " が Lv" + after + " になった!").withStyle(ChatFormatting.AQUA));
			Vfx.burstOut(lv, entity.position().add(0, entity.getBbHeight() / 2, 0), ParticleTypes.END_ROD, 20, 0.3);
			if (stageOf(after) > stageOf(before)) {
				evolveFx(p, entity, k, stageOf(after));
			}
		}
		Casting.msg(p, k.ja() + " Lv" + after + (capped ? "(Lv5 を超えるとえさではほとんど育たない)" : "  経験値 " + exp + "/" + FEED_CAP), ChatFormatting.AQUA);
	}

	/** レベルが変わったら、名前(=見た目の進化段階)と大きさをその場で更新する。 */
	static void refresh(Mob m, PlayerMagic pm, Kind k) {
		int lv = level(pm, k);
		m.setCustomName(Component.literal(k.ja() + " Lv" + lv).withStyle(k == Kind.ANCIENT_DRAGON ? ChatFormatting.GOLD : ChatFormatting.AQUA));
		if (m.getAttribute(Attributes.SCALE) != null) {
			m.getAttribute(Attributes.SCALE).setBaseValue(k.scale * (1 + 0.1f * (stageOf(lv) - 1)));
		}
	}

	static void initRide() {
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((pl, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND || !(entity instanceof MobClasses.Fam f)
					|| !(pl instanceof ServerPlayer sp) || ownerOf((LivingEntity) entity) != sp) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			Kind k = f.mmKind();
			var held = sp.getMainHandItem();
			if (foods(k).contains(held.getItem())) {
				feed(sp, entity, k, held);
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			if (sp.isShiftKeyDown()) {
				// しゃがみ+右クリック: Lv・経験値・好物を表示
				PlayerMagic pm = PlayerMagic.of(sp);
				int exp = pm.familiarExp.getOrDefault(k.id, 0);
				Casting.msg(sp, k.ja() + " Lv" + level(pm, k) + "(経験値 " + exp + ")  好物: " + foodNames(k)
						+ (rideable(k) ? "  右クリックで騎乗(Lv3〜)" : ""), ChatFormatting.AQUA);
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			if (rideable(k)) {
				// 右クリック: 騎乗
				return mount(sp, entity, k);
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		// 乗っているあいだの右クリック = 使い魔ごとの専用攻撃
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((pl, world, hand) -> {
			if (pl.getVehicle() instanceof MobClasses.Fam f && rideable(f.mmKind())) {
				if (!world.isClientSide() && pl instanceof ServerPlayer sp && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
					rideAttack(sp, (Mob) pl.getVehicle(), f.mmKind());
				}
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
	}

	private static final java.util.Map<UUID, Long> DASH = new java.util.HashMap<>();

	/** 騎乗中の専用攻撃。 */
	static void rideAttack(ServerPlayer p, Mob m, Kind k) {
		PlayerMagic pm = PlayerMagic.of(p);
		long now = p.level().getGameTime();
		String key = "ride:" + k.id;
		if (now < pm.cooldownEnd(key)) {
			return;
		}
		swing(m);
		Balance.FamiliarRow r = k.row();
		float power = r.attack() * perf(pm, k) * 1.2f + 6f;
		ServerLevel level = (ServerLevel) p.level();
		switch (k) {
			case WOLF, SPIRIT_WOLVES -> {
				pm.cooldowns.put(key, now + 50);
				Spell sp = Spells.get(Element.WIND, 1);
				Ctx c = new Ctx(p, sp, power);
				DASH.put(m.getUUID(), now + 12);
				Vfx.sound(level, m.position(), ModSounds.RIDE_DASH, 1f, 1.1f);
				java.util.Set<UUID> hit = new java.util.HashSet<>();
				Vfx.sound(level, m.position(), ModSounds.cast(Element.WIND), 1f, 0.8f);
				Tasks.repeat(0, 1, 12, i -> {
					if (!m.isAlive()) {
						return;
					}
					for (LivingEntity t : c.enemiesNear(m.position(), 3.2)) {
						if (hit.add(t.getUUID())) {
							c.hit(t, 0.8f);
							Ctx.push(t, m.position(), 0.8, 0.3);
						}
					}
					Vfx.near(level, ParticleTypes.CLOUD, m.getX(), m.getY() + 0.5, m.getZ(), 6, 0.6, 0.4, 0.6, 0.02);
					Vfx.near(level, Vfx.dust(0x9AE8FF, 1.4f), m.getX(), m.getY() + 0.8, m.getZ(), 6, 0.7, 0.5, 0.7, 0);
				});
			}
			case GOLEM -> {
				pm.cooldowns.put(key, now + 80);
				Ctx c = new Ctx(p, Spells.get(Element.GRAVITY, 1), power);
				Vec3 f = new Vec3(c.dir.x, 0, c.dir.z).normalize();
				Vec3 at = m.position().add(f.scale(4));
				FxDisplay.dangerCircle(level, at, 6, 8);
				Tasks.later(8, () -> {
					c.areaAt(at, 6, 1.5f).forEach(t -> ModEffects.apply(t, ModEffects.KNOCKDOWN, 40, 0));
					Vfx.debris(level, at, 5, 50);
					FxDisplay.ring(level, at, Element.GRAVITY, 6, 8);
					Vfx.shake(level, at, 28, 1.0f, 12);
					Vfx.sound(level, at, net.minecraft.sounds.SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4f, 0.7f);
				});
				Vfx.sound(level, m.position(), net.minecraft.sounds.SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.6f);
			}
			case PHOENIX -> {
				pm.cooldowns.put(key, now + 60);
				Ctx c = new Ctx(p, Spells.get(Element.FIRE, 1), power);
				Vfx.sound(level, m.position(), ModSounds.cast(Element.FIRE), 1.2f, 0.9f);
				Tasks.repeat(0, 2, 10, i -> {
					if (m.isAlive()) {
						c.cone(14, 26, 0.2f).forEach(t -> ModEffects.apply(t, ModEffects.BURN, 80, 1));
						Cinema.breathTick(c, i);
					}
				});
			}
			case PEGASUS -> {
				pm.cooldowns.put(key, now + 60);
				Ctx c = new Ctx(p, Spells.get(Element.LIGHT, 1), power);
				DASH.put(m.getUUID(), now + 14);
				Vfx.sound(level, m.position(), ModSounds.RIDE_DASH, 1f, 1.4f);
				java.util.Set<UUID> hit = new java.util.HashSet<>();
				Vfx.sound(level, m.position(), ModSounds.cast(Element.LIGHT), 1.2f, 1.4f);
				Tasks.repeat(0, 1, 14, i -> {
					if (!m.isAlive()) {
						return;
					}
					for (LivingEntity t : c.enemiesNear(m.position(), 3.5)) {
						if (hit.add(t.getUUID())) {
							c.hit(t, 0.9f);
							ModEffects.apply(t, ModEffects.STAGGER, 40, 0);
						}
					}
					Vfx.near(level, ParticleTypes.END_ROD, m.getX(), m.getY() + 0.6, m.getZ(), 8, 0.8, 0.5, 0.8, 0.03);
					Vfx.near(level, Vfx.dust(0xFFF6C8, 1.6f), m.getX(), m.getY() + 0.8, m.getZ(), 6, 0.9, 0.5, 0.9, 0);
					if (i % 3 == 0) {
						Vfx.ring(level, m.position().add(0, 0.5, 0), 2.5, ParticleTypes.END_ROD, 14);
					}
				});
			}
			case BEHEMOTH -> {
				pm.cooldowns.put(key, now + 90);
				Ctx c = new Ctx(p, Spells.get(Element.GRAVITY, 1), power);
				DASH.put(m.getUUID(), now + 10);
				Vfx.sound(level, m.position(), ModSounds.RIDE_DASH, 1f, 0.7f);
				java.util.Set<UUID> hit = new java.util.HashSet<>();
				Vfx.sound(level, m.position(), ModSounds.BOSS_ROAR, 1.2f, 0.7f);
				Tasks.repeat(0, 1, 10, i -> {
					if (!m.isAlive()) {
						return;
					}
					for (LivingEntity t : c.enemiesNear(m.position(), 3.6)) {
						if (hit.add(t.getUUID())) {
							c.hit(t, 1.0f);
							Ctx.push(t, m.position(), 1.4, 0.5);
							ModEffects.apply(t, ModEffects.KNOCKDOWN, 30, 0);
						}
					}
					Vfx.debris(level, m.position(), 2.5, 14);
				});
				Tasks.later(11, () -> {
					if (m.isAlive()) {
						c.areaAt(m.position(), 5, 0.5f);
						Vfx.shake(level, m.position(), 24, 0.9f, 10);
					}
				});
			}
			default -> {
				pm.cooldowns.put(key, now + 70);
				Element el = Element.values()[(int) (now / 70 % 8)];
				Spell sp = Spells.get(el, 1);
				Ctx c = new Ctx(p, sp, power);
				Vfx.sound(level, m.position(), ModSounds.BOSS_ROAR, 1.2f, 1.2f);
				p.sendSystemMessage(Component.literal("ブレス: " + el.ja).withStyle(el.fmt));
				Tasks.repeat(0, 2, 12, i -> {
					if (!m.isAlive()) {
						return;
					}
					c.cone(16, 22, 0.2f);
					Vec3 from = Vfx.hand(p), d = p.getLookAngle();
					for (int q = 0; q < 8; q++) {
						Vfx.move(level, Fx.of(el), from, d.scale(0.9 + level.getRandom().nextDouble() * 0.6).add(level.getRandom().nextGaussian() * 0.1,
								level.getRandom().nextGaussian() * 0.08, level.getRandom().nextGaussian() * 0.1));
					}
					Vfx.shake(level, p.position(), 8, 0.3f, 3);
				});
			}
		}
	}

	static net.minecraft.world.InteractionResult mount(ServerPlayer sp, Entity entity, Kind k) {
		if (!rideable(k)) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		PlayerMagic pm = PlayerMagic.of(sp);
		int lv = level(pm, k);
		if (lv < 3) {
			Casting.msg(sp, k.ja() + " に乗れるのは Lv3 から(いまは Lv" + lv + ")", ChatFormatting.GRAY);
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		sp.startRiding(entity, true, true);
		Casting.msg(sp, k.ja() + " に乗った(WASD で移動" + (flies(k) ? "・視線の向きに飛ぶ・ジャンプで上昇" : "・ジャンプで跳ぶ") + "、しゃがみで降りる)", ChatFormatting.AQUA);
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	public static void mobTick(Mob m) {
		if (!(m instanceof MobClasses.Fam f) || !(m.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity owner = ownerOf(m);
		if (!(owner instanceof ServerPlayer p) || !PlayerMagic.of(p).activeFamiliars.contains(m.getUUID())) {
			m.discard();
			return;
		}
		Kind k = f.mmKind();
		if (rideTick(m, p, k)) {
			return;
		}
		long t = level.getGameTime() + m.getId();
		aura(m, k, stageOf(level(PlayerMagic.of(p), k)), t);
		double dist = m.distanceToSqr(p);
		if (dist > 24 * 24) {
			m.teleportTo(p.getX() + m.getRandom().nextGaussian(), p.getY() + 0.5, p.getZ() + m.getRandom().nextGaussian());
			m.getNavigation().stop();
		}
		// 狙う相手: 持ち主を殴った敵 → 持ち主が殴った敵 → 近くの敵
		if (t % 10 == 0) {
			LivingEntity target = m.getTarget();
			if (target == null || !target.isAlive() || !Combat.isEnemy(p, target)) {
				LivingEntity cand = p.getLastHurtByMob();
				if (cand == null || !cand.isAlive() || !Combat.isEnemy(p, cand)) {
					cand = p.getLastHurtMob();
				}
				if (cand == null || !cand.isAlive() || !Combat.isEnemy(p, cand) || cand.distanceToSqr(p) > 400) {
					cand = null;
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(12),
							e -> e instanceof net.minecraft.world.entity.monster.Enemy && Combat.isEnemy(p, e))) {
						cand = e;
						break;
					}
				}
				m.setTarget(cand);
			}
		}
		LivingEntity target = m.getTarget();
		PlayerMagic pm = PlayerMagic.of(p);
		float perf = perf(pm, k);
		if (k == Kind.WOLF || k == Kind.SPIRIT_WOLVES) {
			wolfCombat(m, p, level, t, target, stageOf(level(pm, k)), perf, k);
		}
		// 飛ぶ使い魔・スライムは自分で動かす
		if (m instanceof MobClasses.FamAllay || m instanceof MobClasses.FamVex || m instanceof MobClasses.FamGhast) {
			Vec3 goal;
			if (target != null && k != Kind.FAIRY) {
				goal = target.position().add(0, k == Kind.ANCIENT_DRAGON ? 4 : 1, 0);
			} else {
				goal = p.position().add(Math.sin(t * 0.05) * 2, 2.2, Math.cos(t * 0.05) * 2);
			}
			if (m instanceof MobClasses.FamGhast) {
				// ゴースト系(ドラゴン・ペガサス)は、なめらかに加減速しながら飛ぶ(急な向き変えや震えをなくす)
				Vec3 to = goal.subtract(m.position());
				double fd = to.length();
				double sp = Math.min(k == Kind.PEGASUS ? 0.7 : 0.5, fd * 0.1);
				Vec3 want = fd > 0.6 ? to.normalize().scale(sp) : Vec3.ZERO;
				m.setNoGravity(true);
				Vec3 v = m.getDeltaMovement().scale(0.92).add(want.scale(0.08));
				m.setDeltaMovement(v);
				if (v.horizontalDistanceSqr() > 0.0009) {
					float want2 = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
					float cur = m.getYRot();
					float diff = net.minecraft.util.Mth.wrapDegrees(want2 - cur);
					float yaw = cur + net.minecraft.util.Mth.clamp(diff, -4f, 4f);
					m.setYRot(yaw);
					m.setYBodyRot(yaw);
					m.setYHeadRot(yaw);
				}
			} else {
				m.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, k == Kind.ANCIENT_DRAGON ? 1.0 : 1.4);
			}
			if (target != null && k == Kind.GUARDIAN && m.distanceToSqr(target) < 4 && t % 20 == 0) {
				swing(m);
				m.doHurtTarget(level, target);
			}
		} else if (m instanceof MobClasses.FamSlime) {
			Vec3 goal = target != null ? target.position() : p.position();
			if (m.onGround() && m.position().distanceToSqr(goal) > 6 && t % 15 == 0) {
				Vec3 d = goal.subtract(m.position()).normalize();
				m.setDeltaMovement(d.x * 0.5, 0.45, d.z * 0.5);
				m.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
			}
			if (target != null && m.distanceToSqr(target) < 3 && t % 20 == 0) {
				swing(m);
				m.doHurtTarget(level, target);
			}
		}
		switch (k) {
			case MUD_SLIME, GOLEM -> {
				if (t % 60 == 0) {
					taunt(m, p, k == Kind.GOLEM ? 10 : 8);
				}
			}
			case FAIRY -> {
				if (t % 60 == 0) {
					for (LivingEntity a : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(6), e -> Combat.isAlly(p, e))) {
						if (a.getHealth() < a.getMaxHealth()) {
							a.heal(2f * perf);
							level.sendParticles(ParticleTypes.HEART, a.getX(), a.getY() + a.getBbHeight() + 0.2, a.getZ(), 2, 0.3, 0.1, 0.3, 0);
						}
					}
				}
			}
			case HAWK -> {
				if (t % 40 == 0) {
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(28),
							e -> e instanceof net.minecraft.world.entity.monster.Enemy)) {
						e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
					}
				}
			}
			case GUARDIAN -> {
				if (t % 200 == 0) {
					p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, Math.min(3, (int) perf)));
					level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
				}
			}
			case ELEMENTAL -> {
				if (target != null && t % 30 == 0 && m.distanceToSqr(target) < 256) {
					swing(m);
					ItemStackHolder.elementalCast(m, p, target, k.row().attack() * perf);
				}
			}
			case PEGASUS -> {
				if (t % 40 == 0 && m.distanceToSqr(p) < 400) {
					p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
				}
			}
			case BEHEMOTH -> {
				if (t % 40 == 0 && m.distanceToSqr(p) < 400) {
					p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0, false, false));
				}
			}
			case PHOENIX -> {
				if (t % 10 == 0) {
					level.sendParticles(ParticleTypes.FLAME, m.getX(), m.getY() + 0.5, m.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
				}
			}
			case ANCIENT_DRAGON -> {
				if (target != null && t % 50 == 0 && m.distanceToSqr(target) < 600) {
					swing(m);
					Element[] all = Element.values();
					for (int i = 0; i < 3; i++) {
						Element e = all[m.getRandom().nextInt(all.length)];
						Spell s = Spells.get(e, 1);
						Ctx c = new Ctx(m, s, k.row().attack() * perf * 0.5f);
						MagicBolt b = c.bolt(1.6f, 0f);
						b.dir(Ctx.rotateY(c.dir, (i - 1) * 0.2), 1.6f);
					}
					level.playSound(null, m.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, m.getSoundSource(), 0.6f, 1.4f);
				}
			}
			default -> {
			}
		}
	}

	private static void taunt(Mob m, Player owner, double r) {
		for (Mob e : m.level().getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(r),
				e -> e.getTarget() == owner && Combat.isEnemy(owner, e))) {
			e.setTarget(m);
		}
		((ServerLevel) m.level()).sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 2, 0.2,
				0.1, 0.2, 0);
	}

	/** エレメンタル: 杖スロット 1 番目の本の属性で攻撃を代行する(設計書 13.4.1)。 */
	static final class ItemStackHolder {
		static void elementalCast(Mob m, ServerPlayer p, LivingEntity target, float power) {
			Element e = null;
			net.minecraft.world.item.ItemStack w = Casting.heldCaster(p);
			if (!w.isEmpty()) {
				e = Books.firstElement(w);
			}
			if (e == null) {
				e = Element.FIRE;
			}
			Spell s = Spells.get(e, 1);
			Ctx c = new Ctx(m, s, power);
			s.form.cast(c);
		}
	}
}
