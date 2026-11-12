package com.magicmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 究極ボス「終焉神アポカリプス」。終焉の祭壇を築くと、自動で作られる闘技場の中央に現れる。
 * - 同調する属性が 10 秒ごとに変わる(Omnis と同じ仕組み)。その属性は半減、反対の属性が弱点。
 * - HP 75% / 50% / 25% で 4 段階。段階が上がるほど技が増え、予兆が短くなる。
 * - 「終焉の光」: 柱の陰に入らないと即死級。闘技場の 6 本の黒曜石の柱で遮る。
 * - 「重力の井戸」: 全員を中心へ引きずり込んでから爆発。
 * - 使い魔・変身・融合魔法まで総動員しないと削りきれない体力(表の HP 24000・防御係数 0.45)。
 */
public final class Apocalypse {

	public static final String ID = "apocalypse";
	public static final int ARENA_R = 24;
	private static final Map<UUID, Vec3> ARENA = new HashMap<>();
	private static final Map<UUID, Integer> STAGE = new HashMap<>();
	private static final Map<UUID, Long> NEXT_LIGHT = new HashMap<>();
	private static final Map<UUID, Long> NEXT_WELL = new HashMap<>();
	private static final Map<UUID, Long> LOCK = new HashMap<>();
	private static final Map<UUID, Long> BORN = new HashMap<>();
	private static final int ENRAGE_TICKS = 20 * 60 * 8;

	private Apocalypse() {
	}

	// ---- 条件と素材 ----

	public static boolean unlocked(PlayerMagic pm) {
		return pm.defeated.contains(Omnis.ID) && Guild.rank(pm) >= 4 && pm.forms.size() >= 3;
	}

	public static List<String> requirements(PlayerMagic pm) {
		return List.of((pm.defeated.contains(Omnis.ID) ? "§a" : "§c") + "万象の魔王オムニスの討伐",
				(Guild.rank(pm) >= 4 ? "§a" : "§c") + "ギルドランク 4 以上(いま " + Guild.rank(pm) + ")",
				(pm.forms.size() >= 3 ? "§a" : "§c") + "変身を 3 つ以上解放(いま " + pm.forms.size() + ")");
	}

	public static Cost cost() {
		Cost c = Cost.of().add(ModItems.OMNIS_HEART, 3).add(Items.NETHER_STAR, 3).add(ModItems.STARDUST_SHARD, 8);
		for (Element x : Element.values()) {
			c.add(ModItems.core(x), 2);
		}
		return c;
	}

	public static boolean alive(ServerLevel l, Vec3 near) {
		return !l.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(near, near).inflate(80), m -> m.getType() == ModEntities.APOCALYPSE && m.isAlive())
				.isEmpty();
	}

	// ---- 闘技場 ----

	/** 祭壇を中心に、円形の黒曜石の舞台・6 本の柱・縁の灯りを作る(上は 30 ブロックまで空にする)。 */
	public static void buildArena(ServerLevel l, BlockPos altar) {
		int y0 = altar.getY() - 1;
		for (int dx = -ARENA_R - 1; dx <= ARENA_R + 1; dx++) {
			for (int dz = -ARENA_R - 1; dz <= ARENA_R + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > ARENA_R + 0.5) {
					continue;
				}
				BlockPos fl = new BlockPos(altar.getX() + dx, y0, altar.getZ() + dz);
				boolean edge = d > ARENA_R - 1.5;
				boolean ring = Math.abs(d - 12) < 0.6 || Math.abs(d - 4) < 0.5;
				l.setBlock(fl, edge ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : ring ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
						: Blocks.OBSIDIAN.defaultBlockState(), 3);
				l.setBlock(fl.below(), Blocks.OBSIDIAN.defaultBlockState(), 3);
				for (int y = 1; y <= 30; y++) {
					BlockPos p = fl.above(y);
					if (!p.equals(altar) && !l.getBlockState(p).isAir()) {
						l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				if (edge && ((dx + 100) * 7 + (dz + 100) * 3) % 5 == 0) {
					l.setBlock(fl.above(), Blocks.SEA_LANTERN.defaultBlockState(), 3);
				}
			}
		}
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * i / 6 + 0.26;
			int cx = altar.getX() + (int) Math.round(Math.cos(a) * 15);
			int cz = altar.getZ() + (int) Math.round(Math.sin(a) * 15);
			for (int y = 1; y <= 13; y++) {
				for (int ox = -1; ox <= 1; ox++) {
					for (int oz = -1; oz <= 1; oz++) {
						boolean cap = y >= 12;
						l.setBlock(new BlockPos(cx + ox, y0 + y, cz + oz), cap ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState(), 3);
					}
				}
			}
			l.setBlock(new BlockPos(cx, y0 + 14, cz), Blocks.SEA_LANTERN.defaultBlockState(), 3);
		}
		l.playSound(null, altar, SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.HOSTILE, 2f, 0.5f);
	}

	/** 儀式: 闘技場を作り、少し置いてから出現させる。 */
	public static void ritual(ServerLevel l, BlockPos altar) {
		buildArena(l, altar);
		Vec3 c = Vec3.atBottomCenterOf(altar.above());
		BossFx.title(l, c, 80, Component.literal("終焉の祭壇").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.literal("世界の終わりが、目を覚ます…").withStyle(ChatFormatting.GRAY), 50);
		Tasks.repeat(0, 4, 20, i -> {
			double h = i * 0.7;
			Vfx.pillar(l, c, 6 + h, 3.5 - i * 0.12, ParticleTypes.PORTAL, 8);
			Vfx.shake(l, c, 40, 0.3f + i * 0.04f, 6);
		});
		Tasks.later(90, () -> {
			Vec3 pos = c.add(0, 0, 9);
			Enemies.Def d = Enemies.BY_ID.get(ID);
			if (Enemies.summonAt(l, d, pos)) {
				l.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(4), m -> m.getType() == ModEntities.APOCALYPSE)
						.forEach(m -> ARENA.put(m.getUUID(), c));
				Vfx.flash(l, c, 60, 0xFFFFFF, 20);
			}
		});
	}

	// ---- 毎 tick ----

	static int stage(Mob m) {
		float f = m.getHealth() / m.getMaxHealth();
		return f > 0.75f ? 1 : f > 0.5f ? 2 : f > 0.25f ? 3 : 4;
	}

	static void tick(Mob m, ServerLevel l) {
		UUID id = m.getUUID();
		long now = l.getGameTime();
		BORN.putIfAbsent(id, now);
		int st = stage(m);
		int prev = STAGE.getOrDefault(id, 0);
		if (st != prev) {
			STAGE.put(id, st);
			if (prev != 0) {
				stageUp(m, l, st);
			}
		}
		Vec3 c = ARENA.get(id);
		if (c != null && m.position().distanceToSqr(c) > 34 * 34) {
			Vec3 back = c.add(0, 0, 8);
			m.teleportTo(back.x, back.y, back.z);
		}
		if (st >= 4 && now % 100 == 0) {
			m.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 1));
			m.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 0));
		}
		if (now - BORN.get(id) == ENRAGE_TICKS) {
			BossFx.title(l, m.position(), 80, Component.literal("時が尽きる").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
					Component.literal("終焉の光が加速する").withStyle(ChatFormatting.GRAY), 50);
		}
		// 本体の周りの輪
		if (now % 3 == 0) {
			Vfx.near(l, ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 6, m.getBbWidth() * 0.6, m.getBbHeight() * 0.4, m.getBbWidth() * 0.6, 0.05);
		}
	}

	private static void stageUp(Mob m, ServerLevel l, int st) {
		String sub = switch (st) {
			case 2 -> "終焉の光が放たれる — 柱の陰へ";
			case 3 -> "従者が呼び出される";
			default -> "世界が終わる。持てる全てを使え";
		};
		BossFx.title(l, m.position(), 80, Component.literal("第 " + st + " 段階").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				Component.literal(sub).withStyle(ChatFormatting.GRAY), 50);
		Vfx.shake(l, m.position(), 60, 1.2f, 20);
		Vfx.flash(l, m.position(), 40, 0xFFFFFF, 10);
		FxDisplay.ring(l, m.position(), Element.CURSE, 16, 12);
		if (st == 3) {
			guards().run(m, l, m.getTarget());
		}
	}

	static void forget(Mob m) {
		UUID id = m.getUUID();
		ARENA.remove(id);
		STAGE.remove(id);
		NEXT_LIGHT.remove(id);
		NEXT_WELL.remove(id);
		LOCK.remove(id);
		BORN.remove(id);
		Omnis.forget(m);
	}

	// ---- 技 ----

	static List<Enemies.Ability> abilities() {
		return List.of(Enemies.a(70, flurry()), Enemies.a(120, moves()), Enemies.a(200, endLight()), Enemies.a(240, well()), Enemies.a(400, guards()),
				Enemies.a(130, doubleMoves()));
	}

	private static Enemies.Act moves() {
		return (m, l, t) -> {
			Element el = Omnis.attunement(m);
			if (el == null || t == null) {
				return;
			}
			int st = stage(m);
			List<Enemies.Act> pool = List.of(BossMoves.strike(el, 5, 1.0, 2.8f), BossMoves.slam(el, 8, 1.3, 3.0f), BossMoves.rain(el, 6 + st * 2, 3.2, 1.1, 2.6f),
					BossMoves.donut(el, 3.5, 14, 1.5, 3.0f), BossMoves.cross(el, 20, 2.8, 1.3, 2.8f), BossMoves.line(el, 22, 3.2, 1.2, 2.8f, true));
			pool.get(l.getRandom().nextInt(pool.size())).run(m, l, t);
		};
	}

	/** 3 段階目から、時間差でもう 1 つ予兆を重ねる(同時に出せない決まりなので間をあけて)。 */
	private static Enemies.Act doubleMoves() {
		return (m, l, t) -> {
			if (stage(m) >= 3 && t != null) {
				Tasks.later(40, () -> {
					if (m.isAlive()) {
						moves().run(m, l, m.getTarget());
					}
				});
			}
		};
	}

	private static Enemies.Act flurry() {
		return (m, l, t) -> {
			Element el = Omnis.attunement(m);
			if (el == null || t == null || !m.hasLineOfSight(t)) {
				return;
			}
			int[] nos = stage(m) >= 3 ? new int[] { 1, 3, 5, 8, 10 } : new int[] { 1, 3, 5 };
			for (int i = 0; i < nos.length; i++) {
				int no = nos[i];
				Tasks.later(i * 3, () -> Casting.mobCast(m, Spells.get(el, no)));
			}
		};
	}

	/** 従者を呼ぶ(場に 4 体まで、1 回に 2 体)。 */
	private static Enemies.Act guards() {
		return (m, l, t) -> {
			if (t == null) {
				return;
			}
			List<EntityType<? extends Mob>> kinds = List.of(ModEntities.SHADOW_STALKER, ModEntities.HOLY_KNIGHT, ModEntities.CURSE_WITCH);
			int near = l.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(40), x -> x != m && Enemies.isModEnemy(x)).size();
			for (int i = near; i < 4 && i < near + 2; i++) {
				Mob s = kinds.get(l.getRandom().nextInt(kinds.size())).create(l, EntitySpawnReason.MOB_SUMMONED);
				if (s == null) {
					continue;
				}
				double a = l.getRandom().nextDouble() * Math.PI * 2;
				s.snapTo(m.getX() + Math.cos(a) * 6, m.getY(), m.getZ() + Math.sin(a) * 6, 0, 0);
				s.setTarget(t);
				l.addFreshEntity(s);
				FxDisplay.ring(l, s.position(), Element.DARK, 3, 6);
				l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, s.getX(), s.getY() + 1, s.getZ(), 14, 0.4, 0.8, 0.4, 0.04);
			}
		};
	}

	/** 重力の井戸: 3 秒かけて全員を中心(ボス)へ引き寄せ、爆発する。 */
	private static Enemies.Act well() {
		return (m, l, t) -> {
			int st = stage(m);
			long now = l.getGameTime();
			if (st < 2 || t == null || now < NEXT_WELL.getOrDefault(m.getUUID(), 0L) || now < LOCK.getOrDefault(m.getUUID(), 0L)) {
				return;
			}
			NEXT_WELL.put(m.getUUID(), now + 700);
			LOCK.put(m.getUUID(), now + 110);
			Vec3 c = m.position();
			Component warn = Component.literal("重力の井戸 — 引き込まれる!").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
			l.players().forEach(p -> {
				if (p.position().distanceToSqr(c) < 60 * 60) {
					p.sendSystemMessage(warn);
				}
			});
			Vfx.sound(l, c, SoundEvents.WARDEN_SONIC_CHARGE, 3f, 0.6f);
			Tasks.repeat(0, 2, 30, i -> {
				if (!m.isAlive()) {
					return;
				}
				Vfx.vortex(l, c, 14 - i * 0.4, ParticleTypes.REVERSE_PORTAL, 24);
				for (ServerPlayer p : l.getEntitiesOfClass(ServerPlayer.class, m.getBoundingBox().inflate(34), x -> !x.isCreative() && !x.isSpectator())) {
					Vec3 d = c.subtract(p.position());
					double len = d.length();
					if (len > 3) {
						p.setDeltaMovement(p.getDeltaMovement().scale(0.6).add(d.normalize().scale(0.22)));
						p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
					}
				}
			});
			Tasks.later(64, () -> {
				if (m.isAlive()) {
					BossMoves.slam(Element.GRAVITY, 9, 0.9, 3.4f).run(m, l, m.getTarget() == null ? t : m.getTarget());
				}
			});
		};
	}

	/** 終焉の光: 6 秒チャージ。ボスとの間に障害物(柱)が無いプレイヤーに致命の光。 */
	private static Enemies.Act endLight() {
		return (m, l, t) -> {
			int st = stage(m);
			long now = l.getGameTime();
			if (st < 2 || now < NEXT_LIGHT.getOrDefault(m.getUUID(), 0L) || now < LOCK.getOrDefault(m.getUUID(), 0L)) {
				return;
			}
			boolean enraged = now - BORN.getOrDefault(m.getUUID(), now) > ENRAGE_TICKS;
			int charge = enraged ? 80 : st >= 4 ? 100 : 120;
			LOCK.put(m.getUUID(), now + charge + 60);
			NEXT_LIGHT.put(m.getUUID(), now + charge + (enraged ? 160 : st >= 4 ? 280 : 420));
			m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, charge + 6, 6, false, false));
			m.getNavigation().stop();
			Component warn = Component.literal("終焉の光 — 柱の陰に隠れろ!").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
			l.players().forEach(p -> {
				if (p.position().distanceToSqr(m.position()) < 70 * 70) {
					p.sendSystemMessage(warn);
				}
			});
			Vfx.sound(l, m.position(), SoundEvents.BEACON_POWER_SELECT, 4f, 0.5f);
			Tasks.repeat(0, 4, charge / 4, i -> {
				if (!m.isAlive()) {
					return;
				}
				Vec3 eye = m.getEyePosition();
				float prog = i / (float) (charge / 4);
				Vfx.burstOut(l, eye, Vfx.fade(0xFFFFFF, 0xFFD060, 2f), 6 + i, 0.15 + prog * 0.4);
				Vfx.sound(l, eye, SoundEvents.BEACON_AMBIENT, 3f, 0.5f + prog * 1.5f);
				for (ServerPlayer p : l.getEntitiesOfClass(ServerPlayer.class, m.getBoundingBox().inflate(60), x -> !x.isCreative() && !x.isSpectator())) {
					Vec3 to = p.getEyePosition();
					boolean hit = exposed(l, m, eye, to);
					Vfx.line(l, eye, to, hit ? ParticleTypes.END_ROD : ParticleTypes.SMOKE, hit ? 1.5 : 3);
				}
			});
			Tasks.later(charge, () -> {
				if (!m.isAlive()) {
					return;
				}
				Vec3 eye = m.getEyePosition();
				Vfx.flash(l, eye, 70, 0xFFFFFF, 12);
				Vfx.shake(l, eye, 70, 1.5f, 16);
				Vfx.sound(l, eye, SoundEvents.GENERIC_EXPLODE.value(), 4f, 0.5f);
				for (ServerPlayer p : l.getEntitiesOfClass(ServerPlayer.class, m.getBoundingBox().inflate(60), x -> !x.isCreative() && !x.isSpectator())) {
					Vec3 to = p.getEyePosition();
					boolean ex = exposed(l, m, eye, to);
					if (ex) {
						Vfx.beam(l, eye, to, Element.LIGHT);
						p.hurtServer(l, l.damageSources().indirectMagic(m, m), 60f);
					} else {
						Vfx.beam(l, eye, eye.add(to.subtract(eye).normalize().scale(Math.min(eye.distanceTo(to), 40))), Element.LIGHT);
					}
				}
			});
		};
	}

	private static boolean exposed(ServerLevel l, Mob m, Vec3 from, Vec3 to) {
		return l.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m)).getType() == HitResult.Type.MISS;
	}

	// ---- 報酬 ----

	public static void reward(ServerPlayer p, PlayerMagic pm, List<ItemStack> drops, ServerLevel level) {
		drops.add(new ItemStack(ModItems.APOCALYPSE_SHARD, 3));
		for (Element e : Element.values()) {
			drops.add(new ItemStack(ModItems.essence(e), 8));
		}
		drops.add(new ItemStack(ModItems.STARDUST_SHARD, 8));
		drops.add(new ItemStack(Items.NETHER_STAR, 1));
		boolean first = pm.defeated.add(ID);
		pm.skillPoints += first ? 30 : 8;
		for (ServerPlayer o : level.players()) {
			o.sendSystemMessage(Component.literal(p.getName().getString() + " が終焉神アポカリプスを討ち取った!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		}
	}
}
