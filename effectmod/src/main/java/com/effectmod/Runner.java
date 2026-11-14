package com.effectmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 効果の発動(きっかけの判定・クールダウン・各効果の中身)。 */
public final class Runner {

	public static final EquipmentSlot[] HAND = {EquipmentSlot.MAINHAND};
	public static final EquipmentSlot[] OFFHAND = {EquipmentSlot.OFFHAND};
	public static final EquipmentSlot[] ALL = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
			EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** 効果の中で起きたダメージで、さらに効果が発動して無限ループにならないようにする。 */
	private static boolean busy;
	private static final Map<String, Long> COOLDOWNS = new HashMap<>();
	private static final List<Temp> TEMPS = new ArrayList<>();

	private record Temp(ServerLevel level, BlockPos pos, Block block, long expire) {
	}

	private Runner() {
	}

	public static boolean busy() {
		return busy;
	}

	public static List<Entry> entries(ItemStack s) {
		List<Entry> l = s.get(ModContent.EFFECTS);
		return l == null ? List.of() : l;
	}

	public static boolean hasTrigger(ItemStack s, Parser.Trigger t) {
		for (Entry e : entries(s)) {
			if (e.trigger().equals(t.name())) {
				return true;
			}
		}
		return false;
	}

	private static int cooldown(Entry e) {
		return switch (Parser.Action.valueOf(e.action())) {
			case SLASH -> 10;
			case SHOCKWAVE -> 20;
			case EXPLODE -> 30;
			case LIGHTNING -> 40;
			case IGNITE, LIFESTEAL, KNOCKBACK -> 5;
			case FIREBALL -> 15;
			case FREEZE -> 10;
			case HEAL -> 30;
			case DASH, LEAP, BLINK -> 30;
			case POTION -> e.trigger().equals("PASSIVE") ? 0 : 10;
			case PLACE -> 2;
		};
	}

	/** きっかけ t が起きたときに、slots の装備に付いた効果を発動する。target は相手(いなければ null)。 */
	public static void fire(ServerPlayer p, Parser.Trigger t, EquipmentSlot[] slots, LivingEntity target) {
		if (busy || p.isSpectator() || !p.isAlive()) {
			return;
		}
		ServerLevel lv = p.level();
		for (EquipmentSlot slot : slots) {
			ItemStack st = p.getItemBySlot(slot);
			if (st.isEmpty()) {
				continue;
			}
			List<Entry> es = entries(st);
			for (int i = 0; i < es.size(); i++) {
				Entry e = es.get(i);
				if (!e.trigger().equals(t.name())) {
					continue;
				}
				String key = p.getUUID() + slot.name() + i + e.action();
				long now = lv.getGameTime();
				Long last = COOLDOWNS.get(key);
				if (last != null && now - last < cooldown(e)) {
					continue;
				}
				if (e.chance() < 100 && p.getRandom().nextInt(100) >= e.chance()) {
					continue;
				}
				COOLDOWNS.put(key, now);
				busy = true;
				try {
					run(p, lv, e, target);
				} catch (RuntimeException ex) {
					EffectMod.LOGGER.warn("effect failed: {}", e.text(), ex);
				} finally {
					busy = false;
				}
			}
		}
	}

	// ---------------------------------------------------------------- 各効果

	private static void run(ServerPlayer p, ServerLevel lv, Entry e, LivingEntity target) {
		int pw = e.power();
		Vec3 look = p.getLookAngle();
		switch (Parser.Action.valueOf(e.action())) {
			case SLASH -> slash(p, lv, pw, look);
			case SHOCKWAVE -> {
				double r = Math.min(9, 3 + pw * 0.7);
				for (LivingEntity t : near(lv, p.position(), r, p)) {
					hurt(lv, t, lv.damageSources().playerAttack(p), 2 + pw);
					Vec3 d = t.position().subtract(p.position());
					fling(t, new Vec3(d.x, 0, d.z).normalize().scale(0.6 + 0.1 * pw).add(0, 0.4, 0));
				}
				for (int k = 1; k <= 3; k++) {
					for (int a = 0; a < 360; a += 15) {
						double rad = Math.toRadians(a);
						lv.sendParticles(ParticleTypes.POOF, p.getX() + Math.cos(rad) * r * k / 3, p.getY() + 0.2,
								p.getZ() + Math.sin(rad) * r * k / 3, 1, 0, 0.05, 0, 0.02);
					}
				}
				sound(lv, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.4f);
			}
			case EXPLODE -> {
				Vec3 c = targetPos(p, target);
				double r = Math.min(7, 2 + pw * 0.6);
				for (LivingEntity t : near(lv, c, r, p)) {
					hurt(lv, t, lv.damageSources().explosion(p, p), 4 + pw * 2);
					Vec3 d = t.position().subtract(c);
					fling(t, new Vec3(d.x, 0, d.z).normalize().scale(0.5).add(0, 0.4, 0));
				}
				lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
				lv.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.5, c.z, 20, r / 3, 0.4, r / 3, 0.05);
				sound(lv, c, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.0f);
			}
			case LIGHTNING -> {
				Vec3 c = targetPos(p, target);
				int n = pw >= 4 ? 3 : 1;
				for (int k = 0; k < n; k++) {
					Vec3 pos = k == 0 ? c : c.add((p.getRandom().nextDouble() - 0.5) * 5, 0, (p.getRandom().nextDouble() - 0.5) * 5);
					LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(lv, EntitySpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.setPos(pos);
						bolt.setVisualOnly(true); // 本物の落雷だと自分も巻き込まれ、火事になるので、見た目だけ
						lv.addFreshEntity(bolt);
					}
					for (LivingEntity t : near(lv, pos, 3.5, p)) {
						hurt(lv, t, lv.damageSources().lightningBolt(), 5 + pw * 2);
						t.igniteForSeconds(3);
					}
				}
			}
			case IGNITE -> {
				for (LivingEntity t : frontTargets(p, lv, 4 + pw, target)) {
					t.igniteForSeconds(e.seconds() > 0 ? e.seconds() : 2 + pw * 2);
					lv.sendParticles(ParticleTypes.FLAME, t.getX(), t.getY() + 1, t.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
				}
			}
			case FIREBALL -> {
				int n = Math.min(5, 1 + pw / 2);
				for (int k = 0; k < n; k++) {
					Vec3 dir = look.yRot((float) Math.toRadians((k - (n - 1) / 2.0) * 10));
					SmallFireball fb = new SmallFireball(lv, p, dir);
					fb.setPos(p.getEyePosition().add(dir.scale(1.0)));
					lv.addFreshEntity(fb);
				}
				sound(lv, p.position(), SoundEvents.BLAZE_SHOOT, 1f, 1f);
			}
			case FREEZE -> {
				for (LivingEntity t : frontTargets(p, lv, 4 + pw, target)) {
					t.setTicksFrozen(Math.min(300, 140 + pw * 40));
					int sec = e.seconds() > 0 ? e.seconds() : 2 + pw;
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, sec * 20, Math.min(4, pw / 2 + 1)));
					lv.sendParticles(ParticleTypes.SNOWFLAKE, t.getX(), t.getY() + 1, t.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
				}
			}
			case HEAL -> {
				p.heal(pw * 2f);
				lv.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.5, p.getZ(), 3, 0.3, 0.3, 0.3, 0);
			}
			case LIFESTEAL -> {
				List<LivingEntity> ts = frontTargets(p, lv, 4, target);
				if (!ts.isEmpty()) {
					LivingEntity t = ts.get(0);
					float dmg = 1 + pw * 1.5f;
					hurt(lv, t, lv.damageSources().indirectMagic(p, p), dmg);
					p.heal(dmg * 0.7f);
					lv.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.5, p.getZ(), 3, 0.3, 0.3, 0.3, 0);
				}
			}
			case DASH -> {
				double sp = 0.9 + 0.35 * pw;
				fling(p, new Vec3(look.x * sp, look.y * sp * 0.5 + 0.1, look.z * sp));
				lv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 10, 0.2, 0.1, 0.2, 0.02);
			}
			case LEAP -> {
				fling(p, new Vec3(look.x * 0.3, 0.7 + 0.25 * pw, look.z * 0.3));
				lv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
			}
			case BLINK -> blink(p, lv, Math.min(20, 4 + pw * 2), look);
			case KNOCKBACK -> {
				for (LivingEntity t : frontTargets(p, lv, 4 + pw, target)) {
					Vec3 d = t.position().subtract(p.position());
					fling(t, new Vec3(d.x, 0, d.z).normalize().scale(0.6 + 0.35 * pw).add(0, 0.35, 0));
				}
			}
			case POTION -> potion(p, lv, e, target);
			case PLACE -> place(p, lv, e, target);
		}
	}

	/** 前方に飛ぶ斬撃。通り道の敵にダメージ、ブロックにぶつかると止まる。 */
	private static void slash(ServerPlayer p, ServerLevel lv, int pw, Vec3 look) {
		double len = Math.min(14, 4 + pw * 1.2);
		float dmg = 3 + pw * 1.5f;
		Vec3 eye = p.getEyePosition().add(0, -0.3, 0);
		Set<LivingEntity> hit = new HashSet<>();
		for (double d = 1; d <= len; d += 1) {
			Vec3 c = eye.add(look.scale(d));
			BlockPos bp = BlockPos.containing(c);
			if (!lv.getBlockState(bp).getCollisionShape(lv, bp).isEmpty()) {
				break;
			}
			lv.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			lv.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 3, 0.4, 0.3, 0.4, 0.1);
			hit.addAll(near(lv, c, 1.8, p));
		}
		for (LivingEntity t : hit) {
			hurt(lv, t, lv.damageSources().playerAttack(p), dmg);
		}
		sound(lv, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f);
	}

	private static void blink(ServerPlayer p, ServerLevel lv, double dist, Vec3 look) {
		Vec3 from = p.position();
		Vec3 best = null;
		for (double d = 0.5; d <= dist; d += 0.5) {
			Vec3 off = look.scale(d);
			if (!lv.noCollision(p, p.getBoundingBox().move(off))) {
				break;
			}
			best = from.add(off);
		}
		if (best == null) {
			return;
		}
		lv.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 25, 0.3, 0.6, 0.3, 0.3);
		p.teleportTo(lv, best.x, best.y, best.z, Set.of(), p.getYRot(), p.getXRot(), true);
		lv.sendParticles(ParticleTypes.PORTAL, best.x, best.y + 1, best.z, 25, 0.3, 0.6, 0.3, 0.3);
		sound(lv, best, SoundEvents.ENDERMAN_TELEPORT, 1f, 1f);
	}

	private static void potion(ServerPlayer p, ServerLevel lv, Entry e, LivingEntity target) {
		String[] a = e.arg().split(";");
		Identifier id = Identifier.tryParse("minecraft:" + a[0]);
		if (id == null) {
			return;
		}
		Holder<MobEffect> eff = BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
		if (eff == null) {
			return;
		}
		boolean passive = e.trigger().equals("PASSIVE");
		int ticks = passive ? 80 : (e.seconds() > 0 ? e.seconds() : 5) * 20;
		int amp = Math.max(0, e.power() - 1);
		MobEffectInstance inst = new MobEffectInstance(eff, ticks, amp, passive, !passive);
		if (a.length > 1 && a[1].equals("target")) {
			for (LivingEntity t : frontTargets(p, lv, 5, target)) {
				t.addEffect(new MobEffectInstance(inst));
			}
		} else {
			p.addEffect(inst);
		}
	}

	private static void place(ServerPlayer p, ServerLevel lv, Entry e, LivingEntity target) {
		String[] a = e.arg().split(";");
		Identifier id = Identifier.tryParse(a[0]);
		Block b = id == null ? null : BuiltInRegistries.BLOCK.get(id).map(Holder::value).orElse(null);
		if (b == null) {
			return;
		}
		BlockState state = b.defaultBlockState();
		String where = a.length > 1 ? a[1] : "FEET";
		boolean temp = a.length > 2 && a[2].equals("1");
		int pw = e.power();
		List<BlockPos> ps = new ArrayList<>();
		// 床になるブロックは足の下、クモの巣・松明・液体などは足のいる場所に置く
		boolean solid = !state.getCollisionShape(lv, BlockPos.ZERO).isEmpty();
		switch (where) {
			case "TARGET" -> {
				if (target != null) {
					ps.add(base(target, solid));
				}
			}
			case "AROUND" -> {
				BlockPos c = base(p, solid);
				int r = pw >= 3 ? 2 : 1;
				for (int x = -r; x <= r; x++) {
					for (int z = -r; z <= r; z++) {
						ps.add(c.offset(x, 0, z));
					}
				}
			}
			case "WALL" -> {
				Vec3 h = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
				Vec3 side = new Vec3(-h.z, 0, h.x);
				int half = pw >= 3 ? 2 : 1;
				BlockPos c = p.blockPosition().offset((int) Math.round(h.x * 2), 0, (int) Math.round(h.z * 2));
				for (int w = -half; w <= half; w++) {
					for (int y = 0; y < 3; y++) {
						ps.add(c.offset((int) Math.round(side.x * w), y, (int) Math.round(side.z * w)));
					}
				}
			}
			case "FRONT" -> {
				Vec3 h = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
				for (int k = 1; k <= Math.min(pw, 6); k++) {
					ps.add(p.blockPosition().offset((int) Math.round(h.x * k), 0, (int) Math.round(h.z * k)));
				}
			}
			default -> ps.add(base(p, solid));
		}
		long expire = lv.getGameTime() + (e.seconds() > 0 ? e.seconds() * 20L : 160L);
		for (BlockPos pos : ps) {
			BlockState cur = lv.getBlockState(pos);
			if (!lv.isInWorldBounds(pos) || !(cur.isAir() || cur.canBeReplaced())) {
				continue;
			}
			lv.setBlock(pos, state, 3);
			if (temp) {
				TEMPS.add(new Temp(lv, pos, b, expire));
			}
		}
	}

	private static BlockPos base(LivingEntity en, boolean solid) {
		return solid ? BlockPos.containing(en.getX(), en.getBoundingBox().minY - 0.5, en.getZ()) : en.blockPosition();
	}

	/** 毎tick: 時間で消えるブロックを片付ける。 */
	public static void tickTemps(long unusedTick) {
		Iterator<Temp> it = TEMPS.iterator();
		while (it.hasNext()) {
			Temp t = it.next();
			if (t.level().getGameTime() >= t.expire()) {
				if (t.level().getBlockState(t.pos()).is(t.block())) {
					t.level().setBlock(t.pos(), Blocks.AIR.defaultBlockState(), 3);
				}
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- 補助

	private static void hurt(ServerLevel lv, LivingEntity t, net.minecraft.world.damagesource.DamageSource src, float dmg) {
		t.hurtServer(lv, src, dmg);
	}

	private static void sound(ServerLevel lv, Vec3 at, SoundEvent s, float vol, float pitch) {
		lv.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	private static void fling(net.minecraft.world.entity.Entity en, Vec3 v) {
		en.setDeltaMovement(v);
		en.syncVelocity = true;
	}

	private static List<LivingEntity> near(ServerLevel lv, Vec3 c, double r, LivingEntity except) {
		AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
		return lv.getEntitiesOfClass(LivingEntity.class, box,
				en -> en != except && en.isAlive() && en.position().add(0, en.getBbHeight() / 2, 0).distanceTo(c) <= r + en.getBbWidth() / 2);
	}

	/** 相手がいればその相手、いなければ視線の先(前方)にいる敵を最大5体。 */
	private static List<LivingEntity> frontTargets(ServerPlayer p, ServerLevel lv, double range, LivingEntity target) {
		if (target != null) {
			return List.of(target);
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5);
		List<LivingEntity> l = new ArrayList<>(lv.getEntitiesOfClass(LivingEntity.class, box, en -> {
			if (en == p || !en.isAlive()) {
				return false;
			}
			Vec3 d = en.position().add(0, en.getBbHeight() / 2, 0).subtract(eye);
			return d.length() <= range && d.normalize().dot(look) > 0.8;
		}));
		l.sort(Comparator.comparingDouble(en -> en.distanceToSqr(p)));
		return l.size() > 5 ? l.subList(0, 5) : l;
	}

	/** 相手がいればその足元、いなければ視線の先(最大24ブロック)。 */
	private static Vec3 targetPos(ServerPlayer p, LivingEntity target) {
		return target != null ? target.position() : p.pick(24, 1f, false).getLocation();
	}

	public static void tickPlayer(ServerPlayer p, int tick, Map<java.util.UUID, Vec3> lastPos) {
		if (tick % 20 == 0) {
			fire(p, Parser.Trigger.PASSIVE, ALL, null);
		}
		if (tick % 2 == 0) {
			Vec3 prev = lastPos.put(p.getUUID(), p.position());
			if (prev != null) {
				double dx = p.getX() - prev.x;
				double dz = p.getZ() - prev.z;
				if (dx * dx + dz * dz > 0.0004 || Math.abs(p.getY() - prev.y) > 0.05) {
					fire(p, Parser.Trigger.MOVE, ALL, null);
				}
			}
		}
	}
}
