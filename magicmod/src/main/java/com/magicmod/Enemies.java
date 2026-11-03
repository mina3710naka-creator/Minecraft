package com.magicmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** 敵(モンスター)カテゴリ(設計書 第14章)。雑魚 16・中ボス 8・属性超越ボス 8。 */
public final class Enemies {

	public enum Tier {
		MINION, MIDBOSS, BOSS
	}

	@FunctionalInterface
	interface Act {
		void run(Mob m, ServerLevel level, @Nullable LivingEntity target);
	}

	/** 一定間隔で使う能力。phase 0 = いつでも、1 = HP 50% より上、2 = 50% 以下。 */
	record Ability(int interval, int phase, Act act) {
	}

	public record Def(String id, Tier tier, Element element, @Nullable Element weak, String name, float hp, float defense,
			EntityType<? extends Mob> type, float scale, List<Ability> abilities, List<Spell.St> onAttack) {
	}

	private static final Map<EntityType<?>, Def> BY_TYPE = new LinkedHashMap<>();
	public static final Map<String, Def> BY_ID = new LinkedHashMap<>();
	private static final List<Mob> ACTIVE = new ArrayList<>();
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	private static final java.util.Set<UUID> PHASE2 = new java.util.HashSet<>();

	private Enemies() {
	}

	public static @Nullable Def defOf(Entity e) {
		return e == null ? null : BY_TYPE.get(e.getType());
	}

	public static boolean isModEnemy(Entity e) {
		return defOf(e) != null;
	}

	public static @Nullable Tier tierOf(Entity e) {
		Def d = defOf(e);
		return d == null ? null : d.tier();
	}

	/** 敵が撃つ魔法の威力倍率(プレイヤーの HP 20 に合わせて抑えめ)。 */
	public static float powerMult(LivingEntity e) {
		Def d = defOf(e);
		if (d == null) {
			return 0.3f;
		}
		float base = switch (d.tier()) {
			case MINION -> 0.3f;
			case MIDBOSS -> 0.35f;
			case BOSS -> 0.4f;
		};
		return (float) (base * Settings.get().enemyDamageMultiplier * Settings.get().difficultyDamage());
	}

	/** 表の HP(1024 超もある)を、被ダメージ倍率で再現する。防御係数もここで掛ける。 */
	public static float damageScale(LivingEntity e, Def d) {
		float hp = hpOf(d);
		return Math.min(e.getMaxHealth(), hp) / hp * (1 - d.defense());
	}

	/** 設定(難易度・ボス HP 倍率)込みの、表の HP。 */
	public static float hpOf(Def d) {
		Settings s = Settings.get();
		double m = s.difficultyHp() * switch (d.tier()) {
			case MINION -> 1.0;
			case MIDBOSS -> s.midbossHpMultiplier;
			case BOSS -> s.bossHpMultiplier;
		};
		return (float) Math.max(1, d.hp() * m);
	}

	// ---------------------------------------------------------------------------------------------

	private static Act cast(Element e, int no) {
		return (m, l, t) -> {
			if (t != null && m.distanceToSqr(t) < 32 * 32 && m.hasLineOfSight(t)) {
				Casting.mobCast(m, Spells.get(e, no));
			}
		};
	}

	private static Act selfField(Element e, double r, int sec) {
		return (m, l, t) -> {
			Spell s = Spells.get(e, 4);
			Ctx c = new Ctx(m, s, Calc.spellPower(m, s, 1, ItemStack.EMPTY));
			Vec3 p = m.position();
			c.field(p, r, sec, 10, (cc, list) -> list.forEach(x -> cc.hit(x, 0.3f)));
		};
	}

	private static Act summon(EntityType<? extends Mob> type, int n) {
		return (m, l, t) -> {
			if (t == null) {
				return;
			}
			int near = l.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(16), x -> x.getType() == type).size();
			for (int i = near; i < n; i++) {
				Mob s = type.create(l, EntitySpawnReason.MOB_SUMMONED);
				if (s != null) {
					s.snapTo(m.getX() + m.getRandom().nextGaussian() * 2, m.getY(), m.getZ() + m.getRandom().nextGaussian() * 2, 0, 0);
					s.setTarget(t);
					l.addFreshEntity(s);
					l.sendParticles(ParticleTypes.POOF, s.getX(), s.getY() + 0.5, s.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
				}
			}
		};
	}

	private static final Act TELEPORT_AWAY = (m, l, t) -> {
		if (t != null && m.distanceToSqr(t) < 16) {
			Vec3 away = m.position().subtract(t.position()).normalize().scale(8);
			l.sendParticles(ParticleTypes.WITCH, m.getX(), m.getY() + 1, m.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
			m.teleportTo(m.getX() + away.x, m.getY(), m.getZ() + away.z);
			l.playSound(null, m.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, m.getSoundSource(), 1f, 1f);
		}
	};

	private static final Act ENRAGE = (m, l, t) -> {
		m.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
		m.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 0));
	};

	private static Act doomAll() {
		return (m, l, t) -> {
			for (ServerPlayer p : l.getEntitiesOfClass(ServerPlayer.class, m.getBoundingBox().inflate(20), p -> !p.isCreative())) {
				if (!p.hasEffect(ModEffects.DOOM)) {
					p.addEffect(new MobEffectInstance(ModEffects.DOOM, 20 * 15, 0));
					p.sendSystemMessage(Component.literal("時限の呪いをかけられた! 15秒以内に解呪(解呪の聖水・浄化魔法)しよう")
							.withStyle(ChatFormatting.DARK_PURPLE));
				}
			}
			l.playSound(null, m.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, m.getSoundSource(), 1.5f, 0.6f);
		};
	}

	/** 既存の敵に能力を足す(BossMoves から)。 */
	static void more(String id, Ability... extra) {
		Def d = BY_ID.get(id);
		List<Ability> l = new ArrayList<>(d.abilities());
		l.addAll(List.of(extra));
		Def nd = new Def(d.id(), d.tier(), d.element(), d.weak(), d.name(), d.hp(), d.defense(), d.type(), d.scale(), l, d.onAttack());
		BY_ID.put(id, nd);
		BY_TYPE.put(d.type(), nd);
	}

	static Ability a(int interval, Act act) {
		return new Ability(interval, 0, act);
	}

	static Ability p1(int interval, Act act) {
		return new Ability(interval, 1, act);
	}

	static Ability p2(int interval, Act act) {
		return new Ability(interval, 2, act);
	}

	private static void def(String id, Tier tier, EntityType<? extends Mob> type, float scale, List<Ability> ab, Spell.St... onAttack) {
		String name = ModEntities.NAMES.get(id);
		Balance.EnemyRow row = null;
		for (Balance.EnemyRow r : Balance.get().enemies) {
			if (r.name().equals(name)) {
				row = r;
			}
		}
		if (row == null) {
			throw new IllegalStateException("balance.json に敵 " + name + " がありません");
		}
		Def d = new Def(id, tier, row.element(), row.weak(), name, row.hp(), row.defense(), type, scale, ab, List.of(onAttack));
		BY_TYPE.put(type, d);
		BY_ID.put(id, d);
	}

	public static void init() {
		Element W = Element.WIND, T = Element.THUNDER, F = Element.FIRE, A = Element.WATER, G = Element.GRAVITY, D = Element.DARK,
				L = Element.LIGHT, C = Element.CURSE;
		Spell.St wet = Spell.St.fx(ModEffects.WET, 6, 0);
		// 雑魚
		def("wind_sprite", Tier.MINION, ModEntities.WIND_SPRITE, 0.8f, List.of(a(70, cast(W, 1))));
		def("tornado_bat", Tier.MINION, ModEntities.TORNADO_BAT, 0.6f, List.of(a(90, cast(W, 3))), Spell.St.fx(ModEffects.STAGGER, 2, 0));
		def("spark_jelly", Tier.MINION, ModEntities.SPARK_JELLY, 1f, List.of(a(100, selfField(T, 2.5, 3))), Spell.St.fx(ModEffects.SHOCK, 3, 0));
		def("volt_rat", Tier.MINION, ModEntities.VOLT_RAT, 1.4f, List.of(), Spell.St.stack(ModEffects.SHOCK, 4, 3));
		def("flame_imp", Tier.MINION, ModEntities.FLAME_IMP, 0.7f, List.of(a(60, cast(F, 1))), Spell.St.fire(3));
		def("magma_hound", Tier.MINION, ModEntities.MAGMA_HOUND, 0.45f, List.of(a(80, selfField(F, 1.8, 4))), Spell.St.fire(4));
		def("tideling", Tier.MINION, ModEntities.TIDELING, 0.9f, List.of(a(70, cast(A, 13))), Spell.St.fx(MobEffects.SLOWNESS, 3, 1), wet);
		def("kraken_spawn", Tier.MINION, ModEntities.KRAKEN_SPAWN, 0.8f, List.of(a(90, cast(A, 3))), Spell.St.fx(ModEffects.BIND, 1.5f, 0), wet);
		def("gravity_mimic", Tier.MINION, ModEntities.GRAVITY_MIMIC, 1f, List.of(a(100, cast(G, 1))));
		def("void_bat", Tier.MINION, ModEntities.VOID_BAT, 0.6f, List.of(a(110, cast(G, 4))), Spell.St.fx(MobEffects.LEVITATION, 1, 0));
		def("shadow_stalker", Tier.MINION, ModEntities.SHADOW_STALKER, 0.9f, List.of(a(80, cast(D, 1))), Spell.St.fx(MobEffects.BLINDNESS, 2, 0));
		def("nightmare_bat", Tier.MINION, ModEntities.NIGHTMARE_BAT, 0.5f, List.of(a(120, cast(D, 4))), Spell.St.fx(MobEffects.DARKNESS, 4, 0));
		def("lumin_sprite", Tier.MINION, ModEntities.LUMIN_SPRITE, 1f, List.of(a(60, cast(L, 1))), Spell.St.fx(MobEffects.BLINDNESS, 2, 0));
		def("holy_guardian", Tier.MINION, ModEntities.HOLY_GUARDIAN, 0.6f, List.of(a(140, cast(L, 11))));
		def("cursed_puppet", Tier.MINION, ModEntities.CURSED_PUPPET, 0.9f, List.of(a(80, cast(C, 2))));
		def("jinx_moth", Tier.MINION, ModEntities.JINX_MOTH, 0.7f, List.of(a(90, cast(C, 13))),
				(c, t, d) -> t.addEffect(new MobEffectInstance(List.of(MobEffects.WEAKNESS, MobEffects.SLOWNESS, MobEffects.POISON,
						ModEffects.INACCURACY).get(t.getRandom().nextInt(4)), 100, 0)));
		// 中ボス
		def("gale_harpy", Tier.MIDBOSS, ModEntities.GALE_HARPY, 1.7f, List.of(a(120, cast(W, 5)), p1(160, cast(W, 8)), p2(100, cast(W, 7))));
		def("thunder_wolf_alpha", Tier.MIDBOSS, ModEntities.THUNDER_WOLF_ALPHA, 0.75f,
				List.of(a(80, cast(T, 3)), a(160, cast(T, 8)), p2(300, summon(ModEntities.VOLT_RAT, 3))), Spell.St.fx(ModEffects.SHOCK, 4, 1));
		def("salamander_king", Tier.MIDBOSS, ModEntities.SALAMANDER_KING, 1f, List.of(a(70, cast(F, 1)), a(140, selfField(F, 3.5, 4)),
				p2(200, cast(F, 5))), Spell.St.fire(5));
		def("leviathan_juvenile", Tier.MIDBOSS, ModEntities.LEVIATHAN_JUVENILE, 0.8f, List.of(a(100, cast(A, 5)), p2(200, cast(A, 8))));
		def("orbit_golem", Tier.MIDBOSS, ModEntities.ORBIT_GOLEM, 1.4f, List.of(a(60, cast(G, 1)), a(200, cast(G, 9)), p2(160, cast(G, 8))));
		def("abyss_executor", Tier.MIDBOSS, ModEntities.ABYSS_EXECUTOR, 1.3f, List.of(a(100, cast(D, 7)), a(300, cast(D, 9)),
				p2(260, summon(ModEntities.SHADOW_STALKER, 2))));
		def("holy_knight_remnant", Tier.MIDBOSS, ModEntities.HOLY_KNIGHT, 1.25f, List.of(a(120, cast(L, 5)), a(300, cast(L, 8)),
				p2(140, cast(L, 12))));
		def("curse_witch", Tier.MIDBOSS, ModEntities.CURSE_WITCH, 1.2f, List.of(a(40, TELEPORT_AWAY), a(60, (m, l, t) -> {
			int no = 1 + m.getRandom().nextInt(9);
			cast(C, no).run(m, l, t);
		})));
		// 属性超越ボス
		def("zepharion", Tier.BOSS, ModEntities.ZEPHARION, 4f, List.of(p1(120, cast(W, 8)), p1(80, cast(W, 3)), p2(300, cast(W, 10)),
				p2(90, cast(W, 13))), Spell.St.fx(ModEffects.STAGGER, 3, 0));
		def("gardonius", Tier.BOSS, ModEntities.GARDONIUS, 2.5f, List.of(a(60, cast(T, 3)), p1(140, cast(T, 5)), p2(240, cast(T, 10)),
				p2(400, ENRAGE)));
		def("igdrum", Tier.BOSS, ModEntities.IGDRUM, 1.1f, List.of(a(80, cast(F, 1)), a(160, cast(F, 8)), p1(260, cast(F, 10)),
				p2(120, selfField(F, 5, 4)), p2(200, cast(F, 10))));
		def("melusine", Tier.BOSS, ModEntities.MELUSINE, 2.5f, List.of(p1(100, cast(A, 5)), p1(120, cast(A, 3)), p2(300, cast(A, 10)),
				p2(160, cast(A, 8))), wet);
		def("singularis", Tier.BOSS, ModEntities.SINGULARIS, 2f, List.of(a(90, cast(G, 3)), p1(140, cast(G, 9)), p2(220, cast(G, 8)),
				p2(400, cast(G, 10))));
		def("necrovant", Tier.BOSS, ModEntities.NECROVANT, 2f, List.of(a(80, cast(D, 1)), p1(300, summon(ModEntities.SHADOW_STALKER, 3)),
				p2(240, cast(D, 10)), p2(160, cast(D, 8))));
		def("seraphiel", Tier.BOSS, ModEntities.SERAPHIEL, 3f, List.of(a(100, cast(L, 5)), p1(80, cast(L, 1)), p2(260, cast(L, 10)),
				p2(200, (m, l, t) -> m.heal(m.getMaxHealth() * 0.02f))));
		def("morodis", Tier.BOSS, ModEntities.MORODIS, 1.4f, List.of(a(100, cast(C, 12)), p1(140, cast(C, 4)), p1(160, cast(C, 6)),
				p2(420, doomAll()), p2(120, cast(C, 8))));

		BossMoves.register();
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof Mob m && defOf(m) != null) {
				setup(m);
				ACTIVE.add(m);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof Mob m) {
				ACTIVE.remove(m);
				ServerBossEvent bar = BARS.remove(m.getUUID());
				if (bar != null) {
					bar.removeAllPlayers();
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		registerSpawns();
	}

	private static void registerSpawns() {
		for (Def d : BY_ID.values()) {
			if (d.tier() != Tier.MINION) {
				continue;
			}
			@SuppressWarnings("unchecked")
			EntityType<Mob> type = (EntityType<Mob>) d.type();
			boolean water = d.id().equals("tideling") || d.id().equals("kraken_spawn");
			if (water) {
				if (d.id().equals("tideling")) {
					SpawnPlacements.register(type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
							(t, l, r, p, rnd) -> Drowned.checkDrownedSpawnRules(cast(t), l, r, p, rnd));
				} else {
					SpawnPlacements.register(type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
							(t, l, r, p, rnd) -> Guardian.checkGuardianSpawnRules(cast(t), l, r, p, rnd));
				}
				BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_OCEAN).or(BiomeSelectors.tag(BiomeTags.IS_RIVER)),
						MobCategory.MONSTER, type, 6, 1, 2);
				continue;
			}
			SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(t, l, r, p, rnd) -> Monster.checkMonsterSpawnRules(t, l, r, p, rnd));
			int weight = Settings.get().minionSpawnWeight;
			if (weight <= 0) {
				continue;
			}
			BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, type, weight, 1, 2);
			if (d.element() == Element.FIRE) {
				BiomeModifications.addSpawn(BiomeSelectors.foundInTheNether(), MobCategory.MONSTER, type, 12, 1, 2);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <T> T cast(Object o) {
		return (T) o;
	}

	/** 初めて読み込まれたときに HP・大きさなどを設定する。 */
	private static void setup(Mob m) {
		Def d = defOf(m);
		if (d == null || m.entityTags().contains("mm_init")) {
			return;
		}
		m.addTag("mm_init");
		if (m.getAttribute(Attributes.MAX_HEALTH) != null) {
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024, hpOf(d)));
		}
		if (m.getAttribute(Attributes.SCALE) != null) {
			m.getAttribute(Attributes.SCALE).setBaseValue(d.scale());
		}
		if (m.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
			m.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(switch (d.tier()) {
				case MINION -> 4;
				case MIDBOSS -> 7;
				case BOSS -> 10;
			} * Settings.get().enemyDamageMultiplier * Settings.get().difficultyDamage());
		}
		if (m.getAttribute(Attributes.FOLLOW_RANGE) != null && d.tier() != Tier.MINION) {
			m.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40);
		}
		if (d.id().equals("tornado_bat") && m.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
			m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
		}
		if (d.tier() == Tier.BOSS && m.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
			m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
		}
		if (m instanceof AbstractCubeMob cube) {
			cube.setSize(d.tier() == Tier.MIDBOSS ? 4 : 2, false);
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024, hpOf(d)));
		}
		m.setHealth(m.getMaxHealth());
		if (d.tier() != Tier.MINION) {
			m.setPersistenceRequired();
			m.setCustomName(Component.literal(d.name()).withStyle(d.tier() == Tier.BOSS ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
			Tasks.later(2, () -> {
				if (m.isAlive()) {
					BossFx.intro(m, (ServerLevel) m.level(), d);
				}
			});
		}
	}

	private static void tick() {
		for (Mob m : List.copyOf(ACTIVE)) {
			if (!m.isAlive() || !(m.level() instanceof ServerLevel level)) {
				ACTIVE.remove(m);
				ServerBossEvent bar = BARS.remove(m.getUUID());
				if (bar != null) {
					bar.removeAllPlayers();
				}
				continue;
			}
			Def d = defOf(m);
			if (d == null) {
				continue;
			}
			long t = level.getGameTime() + m.getId() * 7L;
			int phase = m.getHealth() > m.getMaxHealth() * 0.5f ? 1 : 2;
			LivingEntity target = m.getTarget();
			if (target != null && (!target.isAlive() || target instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator()))) {
				target = null;
			}
			if (target != null && !m.hasEffect(ModEffects.STUN) && !m.hasEffect(ModEffects.SILENCE)) {
				for (Ability ab : d.abilities()) {
					if ((ab.phase() == 0 || ab.phase() == phase) && t % ab.interval() == 0) {
						ab.act().run(m, level, target);
					}
				}
			}
			if (d.tier() != Tier.MINION) {
				bossBar(m, level, d, phase);
			}
			BossFx.aura(m, level, d, phase, t);
			if (d.tier() != Tier.MINION && phase == 2 && PHASE2.add(m.getUUID())) {
				BossFx.phaseTwo(m, level, d);
			}
		}
	}

	private static void bossBar(Mob m, ServerLevel level, Def d, int phase) {
		ServerBossEvent bar = BARS.computeIfAbsent(m.getUUID(), id -> {
			ServerBossEvent b = new ServerBossEvent(UUID.randomUUID(), Component.literal(d.name()), color(d.element()),
					d.tier() == Tier.BOSS ? BossEvent.BossBarOverlay.NOTCHED_10 : BossEvent.BossBarOverlay.PROGRESS);
			if (d.tier() == Tier.BOSS) {
				b.setDarkenScreen(true);
			}
			return b;
		});
		bar.setProgress(m.getHealth() / m.getMaxHealth());
		bar.setName(Component.literal(d.name() + (phase == 2 ? " [第2形態]" : "") + "  弱点: " + (d.weak() == null ? "-" : d.weak().ja)));
		if (level.getGameTime() % 10 == 0) {
			for (ServerPlayer p : level.players()) {
				boolean near = p.distanceToSqr(m) < 48 * 48;
				if (near && !bar.getPlayers().contains(p)) {
					bar.addPlayer(p);
				} else if (!near && bar.getPlayers().contains(p)) {
					bar.removePlayer(p);
				}
			}
		}
	}

	private static BossEvent.BossBarColor color(Element e) {
		return switch (e) {
			case WIND -> BossEvent.BossBarColor.GREEN;
			case THUNDER -> BossEvent.BossBarColor.YELLOW;
			case FIRE -> BossEvent.BossBarColor.RED;
			case WATER -> BossEvent.BossBarColor.BLUE;
			case GRAVITY, CURSE -> BossEvent.BossBarColor.PURPLE;
			case DARK -> BossEvent.BossBarColor.PINK;
			case LIGHT -> BossEvent.BossBarColor.WHITE;
		};
	}

	static void onHurt(LivingEntity t, DamageSource src, float taken) {
		Entity attacker = src.getEntity();
		if (attacker instanceof Mob m && Combat.CURRENT.get() == null && src.getDirectEntity() == m) {
			Def d = defOf(m);
			if (d != null && !d.onAttack().isEmpty()) {
				Ctx c = new Ctx(m, null, 0);
				for (Spell.St st : d.onAttack()) {
					st.apply(c, t, Combat.statusResist(t));
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// ドロップ・報酬

	static void onKilled(ServerPlayer p, LivingEntity t) {
		Def d = defOf(t);
		if (d == null) {
			return;
		}
		PlayerMagic pm = PlayerMagic.of(p);
		ServerLevel level = (ServerLevel) t.level();
		List<ItemStack> drops = new ArrayList<>();
		var rnd = t.getRandom();
		float extra = 0.05f * pm.skill("e_" + d.element().id + "_sustain");
		switch (d.tier()) {
			case MINION -> {
				drops.add(new ItemStack(ModItems.crystal(d.element()), 1 + rnd.nextInt(2)));
				if (rnd.nextFloat() < 0.5f + extra) {
					drops.add(new ItemStack(ModItems.minionDrop(d.element())));
				}
				if (rnd.nextFloat() < 0.4f) {
					drops.add(new ItemStack(ModItems.CRYSTAL_SHARD, 1 + rnd.nextInt(2)));
				}
			}
			case MIDBOSS -> {
				drops.add(new ItemStack(ModItems.midbossDrop(d.element()), 1 + rnd.nextInt(2)));
				drops.add(new ItemStack(ModItems.crystal(d.element()), 4 + rnd.nextInt(3)));
				drops.add(new ItemStack(ModItems.essence(d.element()), 1 + (rnd.nextFloat() < extra ? 1 : 0)));
				drops.add(new ItemStack(ModItems.RUNE_STONE, 2));
				if (rnd.nextFloat() < 0.25f) {
					drops.add(randomGear(rnd, false));
				}
				int sp = pm.defeated.add(d.id()) ? 3 : 1;
				pm.skillPoints += sp;
				p.sendSystemMessage(Component.literal(d.name() + " を倒した! スキルポイント +" + sp).withStyle(ChatFormatting.GOLD));
			}
			case BOSS -> {
				drops.add(new ItemStack(ModItems.core(d.element())));
				drops.addAll(catalysts(d.element()));
				drops.add(new ItemStack(ModItems.essence(d.element()), 4));
				drops.add(new ItemStack(ModItems.STARDUST_SHARD, 1));
				if (rnd.nextFloat() < 0.5f) {
					drops.add(randomGear(rnd, true));
				}
				int sp = pm.defeated.add(d.id()) ? 5 : 2;
				pm.skillPoints += sp;
				p.sendSystemMessage(Component.literal(d.name() + " を討伐した! 「" + ModItems.core(d.element()).getDefaultInstance().getHoverName().getString()
						+ "」を手に入れた(スキルポイント +" + sp + ")").withStyle(ChatFormatting.GOLD));
				level.playSound(null, t.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, t.getSoundSource(), 1f, 1f);
			}
		}
		for (ItemStack s : drops) {
			ItemEntity it = new ItemEntity(level, t.getX(), t.getY() + 0.5, t.getZ(), s);
			it.setDefaultPickUpDelay();
			level.addFreshEntity(it);
		}
		// 弱点属性で倒すと本の EXP ボーナス(設計書 2.2)
		Ctx c = Combat.CURRENT.get();
		if (d.weak() != null) {
			pm.pendingBookExp.merge(d.weak(), d.tier() == Tier.MINION ? 15f : 80f, Float::sum);
		}
	}

	/** 属性超越ボスの共通触媒(設計書 14.3)。 */
	public static List<ItemStack> catalysts(Element e) {
		return switch (e) {
			case WIND -> List.of(new ItemStack(Items.ELYTRA));
			case THUNDER -> List.of(new ItemStack(Items.TRIDENT));
			case FIRE -> List.of(new ItemStack(Items.DRAGON_HEAD), new ItemStack(Items.NETHER_STAR));
			case WATER -> List.of(new ItemStack(Items.HEART_OF_THE_SEA));
			case GRAVITY -> List.of(new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.NETHER_STAR));
			case DARK -> List.of(new ItemStack(Items.WITHER_SKELETON_SKULL), new ItemStack(Items.DRAGON_HEAD));
			case LIGHT -> List.of(new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.NETHER_STAR));
			case CURSE -> List.of(new ItemStack(Items.WITHER_SKELETON_SKULL, 3), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
		};
	}

	private static ItemStack randomGear(net.minecraft.util.RandomSource rnd, boolean archetypeGear) {
		List<Gear.Kind> pool = new ArrayList<>();
		for (Gear.Kind k : Gear.Kind.values()) {
			if (archetypeGear == (k.only != null) || !archetypeGear) {
				pool.add(k);
			}
		}
		Item it = ModItems.GEAR.get(pool.get(rnd.nextInt(pool.size())));
		return new ItemStack(it);
	}

	/** 封印の祭壇から呼び出す。 */
	public static boolean summonAt(ServerLevel level, Def d, Vec3 pos) {
		Mob m = d.type().create(level, EntitySpawnReason.TRIGGERED);
		if (m == null) {
			return false;
		}
		m.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360, 0);
		level.addFreshEntity(m);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
		level.playSound(null, m.blockPosition(), d.tier() == Tier.BOSS ? SoundEvents.WITHER_SPAWN : SoundEvents.EVOKER_PREPARE_SUMMON,
				m.getSoundSource(), 1.2f, 1f);
		return true;
	}
}
