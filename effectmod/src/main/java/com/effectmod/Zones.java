package com.effectmod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.Vec3;

/** 置いておく効果(地雷・砲台・結界・炎の領域)と、落ちたら爆発する矢。時間がたつと消える。 */
final class Zones {

	enum Type {
		MINE, SENTRY, HEAL_ZONE, FIRE_ZONE
	}

	record Zone(ServerLevel level, Vec3 pos, long expire, Type type, UUID owner, int power) {
	}

	private static final class Shot {
		final Arrow arrow;
		final ServerLevel level;
		final UUID owner;
		final int power;
		final long expire;
		Vec3 last;
		int still;

		Shot(Arrow arrow, ServerLevel level, UUID owner, int power, long expire) {
			this.arrow = arrow;
			this.level = level;
			this.owner = owner;
			this.power = power;
			this.expire = expire;
		}
	}

	private static final List<Zone> ZONES = new ArrayList<>();
	private static final List<Shot> SHOTS = new ArrayList<>();

	private Zones() {
	}

	static void add(ServerLevel lv, Vec3 pos, Type type, ServerPlayer owner, int power, int seconds) {
		// 1人が置ける数に上限(古いものから消す)
		long mine = ZONES.stream().filter(z -> z.owner().equals(owner.getUUID()) && z.type() == type).count();
		if (mine >= 6) {
			for (Iterator<Zone> it = ZONES.iterator(); it.hasNext();) {
				Zone z = it.next();
				if (z.owner().equals(owner.getUUID()) && z.type() == type) {
					it.remove();
					break;
				}
			}
		}
		ZONES.add(new Zone(lv, pos, lv.getGameTime() + seconds * 20L, type, owner.getUUID(), power));
	}

	static void trackArrow(Arrow a, ServerLevel lv, ServerPlayer owner, int power) {
		SHOTS.add(new Shot(a, lv, owner.getUUID(), power, lv.getGameTime() + 200));
	}

	static int count() {
		return ZONES.size() + SHOTS.size();
	}

	static void tick() {
		for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext();) {
			Shot s = it.next();
			boolean gone = s.arrow.isRemoved();
			Vec3 now = s.arrow.position();
			if (!gone && s.last != null && now.distanceToSqr(s.last) < 1e-4) {
				s.still++;
			} else {
				s.still = 0;
			}
			boolean old = s.level.getGameTime() >= s.expire;
			if (gone || s.still >= 2 || old) {
				Vec3 at = gone && s.last != null ? s.last : now;
				explode(s.level, s.owner, at, 3.0, 6 + s.power * 2);
				s.arrow.discard();
				it.remove();
				continue;
			}
			s.last = now;
		}
		for (Iterator<Zone> it = ZONES.iterator(); it.hasNext();) {
			Zone z = it.next();
			ServerLevel lv = z.level();
			long t = lv.getGameTime();
			if (t >= z.expire()) {
				lv.sendParticles(ParticleTypes.POOF, z.pos().x, z.pos().y + 0.3, z.pos().z, 8, 0.3, 0.1, 0.3, 0.02);
				it.remove();
				continue;
			}
			if (t % 10 != 0) {
				continue;
			}
			Vec3 c = z.pos();
			switch (z.type()) {
				case MINE -> {
					lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.1, c.z, 1, 0.2, 0, 0.2, 0);
					for (LivingEntity e : Runner.near(lv, c, 1.5, null)) {
						if (!e.getUUID().equals(z.owner())) {
							explode(lv, z.owner(), c, 3.0, 6 + z.power() * 2);
							it.remove();
							break;
						}
					}
				}
				case SENTRY -> {
					lv.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1, c.z, 1, 0.1, 0.1, 0.1, 0);
					if (t % 20 != 0) {
						break;
					}
					LivingEntity best = null;
					for (LivingEntity e : Runner.near(lv, c, 8, null)) {
						if (e instanceof Enemy && (best == null || e.distanceToSqr(c) < best.distanceToSqr(c))) {
							best = e;
						}
					}
					if (best != null) {
						Vec3 to = best.position().add(0, best.getBbHeight() / 2, 0);
						Vec3 from = c.add(0, 1, 0);
						Vec3 d = to.subtract(from);
						int n = Math.max(1, (int) (d.length() * 2));
						for (int i = 0; i <= n; i++) {
							Vec3 q = from.add(d.scale(i / (double) n));
							lv.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
						}
						Runner.hurt(lv, best, lv.damageSources().magic(), 2 + z.power());
					}
				}
				case HEAL_ZONE -> {
					for (int a = 0; a < 360; a += 30) {
						double rad = Math.toRadians(a);
						lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x + Math.cos(rad) * 4, c.y + 0.2, c.z + Math.sin(rad) * 4, 1, 0, 0, 0, 0);
					}
					if (t % 20 == 0) {
						for (ServerPlayer p : lv.players()) {
							if (p.isAlive() && p.position().distanceToSqr(c) <= 16) {
								p.heal(1 + z.power() / 2f);
								lv.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.8, p.getZ(), 2, 0.3, 0.2, 0.3, 0);
							}
						}
					}
				}
				case FIRE_ZONE -> {
					lv.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.2, c.z, 20, 2.5, 0.1, 2.5, 0.02);
					for (LivingEntity e : Runner.near(lv, c, 4, null)) {
						if (!e.getUUID().equals(z.owner())) {
							e.igniteForSeconds(3);
							Runner.hurt(lv, e, lv.damageSources().magic(), 1 + z.power() / 3f);
						}
					}
				}
			}
		}
	}

	private static void explode(ServerLevel lv, UUID owner, Vec3 at, double r, float dmg) {
		for (LivingEntity e : Runner.near(lv, at, r, null)) {
			if (!e.getUUID().equals(owner)) {
				Runner.hurt(lv, e, lv.damageSources().explosion(null, lv.getEntity(owner)), dmg);
				Vec3 d = e.position().subtract(at);
				Runner.fling(e, new Vec3(d.x, 0, d.z).normalize().scale(0.5).add(0, 0.4, 0));
			}
		}
		lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
		lv.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
	}
}
