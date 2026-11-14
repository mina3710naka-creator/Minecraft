package com.effectmod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 追加の効果(どのアイテムにも付くもの + アイテム専用のもの)の中身。 */
final class Skills {

	private Skills() {
	}

	// ------------------------------------------------------------ 本体

	static void run(ServerPlayer p, ServerLevel lv, Entry e, LivingEntity target, ItemStack stack, Parser.Action a) {
		int pw = e.power();
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
		int secs = e.seconds();
		switch (a) {
			// ===================== どのアイテムにも
			case METEOR -> {
				Vec3 c = Runner.targetPos(p, target);
				double r = Math.min(8, 3 + pw * 0.4);
				for (int y = 12; y >= 0; y--) {
					lv.sendParticles(ParticleTypes.FLAME, c.x, c.y + y, c.z, 3, 0.3, 0.3, 0.3, 0.02);
					lv.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + y, c.z, 1, 0.2, 0.2, 0.2, 0.01);
				}
				for (LivingEntity t : Runner.near(lv, c, r, p)) {
					Runner.hurt(lv, t, lv.damageSources().explosion(p, p), 6 + pw * 2.5f);
					t.igniteForSeconds(4);
					Runner.fling(t, new Vec3(0, 0.7, 0));
				}
				lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
				Runner.sound(lv, c, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.7f);
			}
			case TORNADO -> {
				Vec3 c = Runner.targetPos(p, target);
				double r = 3.5 + pw * 0.4;
				for (LivingEntity t : Runner.near(lv, c, r, p)) {
					Runner.fling(t, new Vec3(rnd(p) * 0.6, 0.9 + 0.1 * pw, rnd(p) * 0.6));
					Runner.hurt(lv, t, lv.damageSources().indirectMagic(p, p), 1 + pw);
				}
				for (int i = 0; i < 40; i++) {
					double ang = i * 0.7;
					double h = i * 0.15;
					lv.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(ang) * (1 + h * 0.4), c.y + h, c.z + Math.sin(ang) * (1 + h * 0.4), 1, 0, 0, 0, 0);
				}
			}
			case VACUUM -> {
				for (LivingEntity t : Runner.near(lv, p.position(), 6 + pw, p)) {
					Vec3 d = p.position().subtract(t.position());
					Runner.fling(t, new Vec3(d.x, 0, d.z).normalize().scale(0.6 + 0.12 * pw).add(0, 0.2, 0));
				}
				lv.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 2, 1, 2, 0.5);
			}
			case CHAIN -> {
				List<LivingEntity> first = Runner.frontTargets(p, lv, 10, target);
				if (first.isEmpty()) {
					return;
				}
				LivingEntity cur = first.get(0);
				Set<LivingEntity> seen = new HashSet<>();
				Vec3 prev = p.getEyePosition();
				for (int i = 0; i < 2 + pw && cur != null; i++) {
					seen.add(cur);
					Vec3 at = cur.position().add(0, cur.getBbHeight() / 2, 0);
					line(lv, ParticleTypes.ELECTRIC_SPARK, prev, at);
					Runner.hurt(lv, cur, lv.damageSources().lightningBolt(), 4 + pw * 1.5f);
					prev = at;
					LivingEntity best = null;
					for (LivingEntity n : Runner.near(lv, at, 6, p)) {
						if (!seen.contains(n) && (best == null || n.distanceToSqr(cur) < best.distanceToSqr(cur))) {
							best = n;
						}
					}
					cur = best;
				}
				Runner.sound(lv, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
			}
			case COUNTER -> {
				if (target != null) {
					Runner.hurt(lv, target, lv.damageSources().indirectMagic(p, p), pw * 2);
					Vec3 d = target.position().subtract(p.position());
					Runner.fling(target, new Vec3(d.x, 0, d.z).normalize().scale(0.5).add(0, 0.3, 0));
				}
			}
			case BARRIER -> {
				absorb(p, pw * 2, (secs > 0 ? secs : 30) * 20);
				lv.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 20, 0.5, 0.8, 0.5, 0.05);
			}
			case INVULN -> {
				p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (secs > 0 ? secs : 3) * 20, 4));
				p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, (secs > 0 ? secs : 3) * 20, 0));
				lv.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.6, 1, 0.6, 0.1);
			}
			case TIMESTOP -> {
				int ticks = (secs > 0 ? secs : 3) * 20;
				for (LivingEntity t : Runner.near(lv, p.position(), 8, p)) {
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6));
					t.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
				}
				lv.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 60, 4, 1, 4, 0.02);
			}
			case XP -> ExperienceOrb.award(lv, target != null ? target.position() : p.position(), pw * 3);
			case LOOT -> {
				Vec3 at = target != null ? target.position() : p.position();
				for (int i = 0; i < pw; i++) {
					int r = p.getRandom().nextInt(100);
					Item it = r < 3 ? Items.DIAMOND : r < 12 ? Items.EMERALD : r < 50 ? Items.GOLD_NUGGET : Items.IRON_NUGGET;
					drop(lv, at, new ItemStack(it, 1 + p.getRandom().nextInt(3)));
				}
			}
			case FEED -> p.getFoodData().eat(pw * 2, 0.6f);
			case CLEANSE -> {
				List<Holder<MobEffect>> bad = new ArrayList<>();
				for (MobEffectInstance i : p.getActiveEffects()) {
					if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
						bad.add(i.getEffect());
					}
				}
				bad.forEach(p::removeEffect);
				p.clearFire();
				lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 15, 0.5, 0.8, 0.5, 0);
			}
			case FLAME_CONE -> {
				double range = 3 + pw;
				for (double d = 1; d <= range; d += 0.7) {
					Vec3 c = p.getEyePosition().add(look.scale(d));
					lv.sendParticles(ParticleTypes.FLAME, c.x, c.y - 0.2, c.z, 3, d * 0.08, d * 0.08, d * 0.08, 0.01);
				}
				for (LivingEntity t : cone(p, lv, range, 0.75)) {
					t.igniteForSeconds(3 + pw);
					Runner.hurt(lv, t, lv.damageSources().indirectMagic(p, p), 1 + pw / 2f);
				}
				Runner.sound(lv, p.position(), SoundEvents.BLAZE_SHOOT, 0.8f, 0.8f);
			}
			case BLIZZARD -> {
				double r = 5 + pw * 0.5;
				for (LivingEntity t : Runner.near(lv, p.position(), r, p)) {
					t.setTicksFrozen(220);
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
					Runner.hurt(lv, t, lv.damageSources().freeze(), 1 + pw / 2f);
				}
				lv.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.5, p.getZ(), 120, r / 2, 1, r / 2, 0.05);
			}
			case POISON_CLOUD -> {
				Vec3 c = target != null ? target.position() : p.position();
				for (LivingEntity t : Runner.near(lv, c, 4, p)) {
					t.addEffect(new MobEffectInstance(MobEffects.POISON, (secs > 0 ? secs : 5) * 20, pw / 3));
				}
				lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.5, c.z, 50, 2, 0.5, 2, 0.01);
			}
			case HEAL_AURA -> {
				List<ServerPlayer> who = new ArrayList<>(lv.players());
				who.remove(p);
				who.add(p);
				for (ServerPlayer o : who) {
					if (o.distanceToSqr(p) <= 64 && o.isAlive()) {
						o.heal(pw);
						lv.sendParticles(ParticleTypes.HEART, o.getX(), o.getY() + 1.8, o.getZ(), 3, 0.3, 0.2, 0.3, 0);
					}
				}
			}
			case SCOUT -> {
				for (LivingEntity t : Runner.near(lv, p.position(), 16 + pw * 2, p)) {
					t.addEffect(new MobEffectInstance(MobEffects.GLOWING, (secs > 0 ? secs : 10) * 20, 0));
				}
			}
			case BACKSTAB -> {
				List<LivingEntity> ts = Runner.frontTargets(p, lv, 12, target);
				if (ts.isEmpty()) {
					return;
				}
				LivingEntity t = ts.get(0);
				Vec3 tl = new Vec3(t.getLookAngle().x, 0, t.getLookAngle().z).normalize();
				Vec3 at = t.position().subtract(tl.scale(1.5));
				Vec3 d = t.position().subtract(at);
				float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
				lv.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.3);
				p.teleportTo(lv, at.x, t.getY(), at.z, Set.of(), yaw, 0, true);
				Runner.hurt(lv, t, lv.damageSources().playerAttack(p), 2 + pw * 2);
				Runner.sound(lv, at, SoundEvents.ENDERMAN_TELEPORT, 1f, 1.3f);
			}
			case STUN -> {
				for (LivingEntity t : Runner.frontTargets(p, lv, 5, target)) {
					int ticks = (secs > 0 ? secs : 2) * 20;
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6));
					t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2));
					lv.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() + 0.3, t.getZ(), 10, 0.3, 0.1, 0.3, 0.1);
				}
			}
			case EXECUTE -> {
				List<LivingEntity> ts = Runner.frontTargets(p, lv, 5, target);
				if (!ts.isEmpty() && ts.get(0).getHealth() <= ts.get(0).getMaxHealth() * 0.25f) {
					LivingEntity t = ts.get(0);
					Runner.hurt(lv, t, lv.damageSources().playerAttack(p), t.getMaxHealth() * 10);
					lv.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1, t.getZ(), 25, 0.4, 0.5, 0.4, 0.3);
				}
			}
			case SELF_REPAIR -> {
				if (stack.isDamaged()) {
					stack.setDamageValue(Math.max(0, stack.getDamageValue() - pw));
				}
			}
			case MAGNET -> {
				double r = 6 + pw;
				for (ItemEntity it : lv.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(r), i -> true)) {
					Vec3 d = p.position().add(0, 0.5, 0).subtract(it.position());
					it.setDeltaMovement(d.scale(0.25));
					it.syncVelocity = true;
				}
			}
			case BONUS_DAMAGE -> {
				for (LivingEntity t : Runner.frontTargets(p, lv, 5, target)) {
					Runner.hurt(lv, t, lv.damageSources().indirectMagic(p, p), pw * 2);
				}
			}
			case BONEMEAL -> {
				BlockPos c = p.blockPosition().below();
				HitResult h = p.pick(5, 1f, false);
				if (h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK) {
					c = b.getBlockPos();
				}
				int r = pw >= 3 ? 1 : 0;
				for (int x = -r; x <= r; x++) {
					for (int z = -r; z <= r; z++) {
						BlockPos q = c.offset(x, 0, z);
						// 作物そのもの、または畑の土を指したときはその上の作物
						if (!BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), lv, q)) {
							BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), lv, q.above());
						}
					}
				}
			}

			// ===================== 剣
			case IAI -> {
				double len = Math.min(14, 4 + pw * 1.5);
				Vec3 end = advance(p, lv, len, flat);
				if (end == null) {
					return;
				}
				Vec3 from = p.position();
				double dist = end.subtract(from).length();
				Set<LivingEntity> hit = new HashSet<>();
				for (double d = 0; d <= dist; d += 1) {
					Vec3 c = from.add(flat.scale(d)).add(0, 1, 0);
					hit.addAll(Runner.near(lv, c, 1.6, p));
					lv.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				}
				p.teleportTo(lv, end.x, end.y, end.z, Set.of(), p.getYRot(), p.getXRot(), true);
				for (LivingEntity t : hit) {
					Runner.hurt(lv, t, lv.damageSources().playerAttack(p), 4 + pw * 2);
				}
				Runner.sound(lv, end, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
			}
			case WHIRLWIND -> {
				double r = 3 + pw * 0.4;
				for (LivingEntity t : Runner.near(lv, p.position(), r, p)) {
					Runner.hurt(lv, t, lv.damageSources().playerAttack(p), 3 + pw * 1.5f);
					Vec3 d = t.position().subtract(p.position());
					Runner.fling(t, new Vec3(d.x, 0, d.z).normalize().scale(0.5).add(0, 0.25, 0));
				}
				for (int k = 0; k < 12; k++) {
					double ang = k * Math.PI / 6;
					lv.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(ang) * r * 0.8, p.getY() + 1, p.getZ() + Math.sin(ang) * r * 0.8, 1, 0, 0, 0, 0);
				}
				Runner.sound(lv, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.7f);
			}

			// ===================== 斧
			case CLEAVE -> {
				double len = 6 + pw;
				Set<LivingEntity> hit = new HashSet<>();
				for (double d = 1; d <= len; d += 1) {
					Vec3 c = p.position().add(flat.scale(d));
					lv.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.2, c.z, 6, 0.3, 0.1, 0.3, 0.2);
					lv.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.1, c.z, 3, 0.2, 0.05, 0.2, 0.02);
					hit.addAll(Runner.near(lv, c.add(0, 0.5, 0), 1.5, p));
				}
				for (LivingEntity t : hit) {
					Runner.hurt(lv, t, lv.damageSources().playerAttack(p), 5 + pw * 2);
					Runner.fling(t, new Vec3(0, 0.7, 0));
				}
				Runner.sound(lv, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
			}
			case TIMBER -> {
				if (Runner.ctxState != null && Runner.ctxState.is(BlockTags.LOGS)) {
					for (BlockPos bp : flood(lv, Runner.ctxPos, s -> s.is(BlockTags.LOGS), 24 + pw * 12)) {
						breakBlock(lv, p, bp);
					}
				}
			}

			// ===================== ツルハシ
			case VEIN -> {
				if (isOre(Runner.ctxState)) {
					Block b = Runner.ctxState.getBlock();
					for (BlockPos bp : flood(lv, Runner.ctxPos, s -> s.is(b), 12 + pw * 6)) {
						breakBlock(lv, p, bp);
					}
				}
			}
			case BIGMINE -> {
				if (Runner.ctxPos == null || Runner.ctxState == null) {
					return;
				}
				int r = pw >= 4 ? 2 : 1;
				float base = Runner.ctxState.getDestroySpeed(lv, Runner.ctxPos) + 1.5f;
				for (int x = -r; x <= r; x++) {
					for (int y = -r; y <= r; y++) {
						for (int z = -r; z <= r; z++) {
							BlockPos bp = Runner.ctxPos.offset(x, y, z);
							if (lv.getBlockState(bp).getDestroySpeed(lv, bp) <= base) {
								breakBlock(lv, p, bp);
							}
						}
					}
				}
			}

			// ===================== シャベル
			case SANDSTORM -> {
				double r = 6 + pw / 2.0;
				for (LivingEntity t : Runner.near(lv, p.position(), r, p)) {
					t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
					Runner.hurt(lv, t, lv.damageSources().indirectMagic(p, p), 1);
				}
				lv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 100, r / 2, 1, r / 2, 0.1);
			}
			case PITFALL -> {
				List<LivingEntity> ts = Runner.frontTargets(p, lv, 6, target);
				if (ts.isEmpty()) {
					return;
				}
				BlockPos b = ts.get(0).blockPosition().below();
				int r = pw >= 3 ? 1 : 0;
				long expire = lv.getGameTime() + (secs > 0 ? secs : 8) * 20L;
				for (int x = -r; x <= r; x++) {
					for (int z = -r; z <= r; z++) {
						for (int dy = 0; dy < 3; dy++) {
							BlockPos bp = b.offset(x, -dy, z);
							BlockState st = lv.getBlockState(bp);
							if (st.isAir() || st.hasBlockEntity() || st.getDestroySpeed(lv, bp) < 0 || st.getDestroySpeed(lv, bp) > 5) {
								continue;
							}
							Runner.TEMPS.add(new Runner.Temp(lv, bp, Blocks.AIR, expire, st));
							lv.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
						}
					}
				}
			}

			// ===================== クワ
			case HARVEST -> {
				int r = Math.min(4, 2 + pw / 2);
				BlockPos c = p.blockPosition();
				int n = 0;
				for (int x = -r; x <= r; x++) {
					for (int y = -1; y <= 1; y++) {
						for (int z = -r; z <= r; z++) {
							BlockPos bp = c.offset(x, y, z);
							if (lv.getBlockState(bp).getBlock() instanceof BonemealableBlock
									&& BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), lv, bp)) {
								n++;
							}
						}
					}
				}
				if (n > 0) {
					lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 0.5, p.getZ(), 30, r, 0.5, r, 0);
				}
			}
			case REAPER -> {
				double range = 3 + pw * 0.5;
				for (LivingEntity t : cone(p, lv, range, 0.3)) {
					Runner.hurt(lv, t, lv.damageSources().playerAttack(p), 3 + pw);
					p.heal(1);
				}
				for (int k = -4; k <= 4; k++) {
					Vec3 d = flat.yRot((float) Math.toRadians(k * 15));
					lv.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + d.x * range * 0.7, p.getY() + 1, p.getZ() + d.z * range * 0.7, 1, 0, 0, 0, 0);
				}
				Runner.sound(lv, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.9f);
			}

			// ===================== 弓・クロスボウ
			case MULTISHOT -> {
				int n = Math.min(9, 3 + pw);
				for (int i = 0; i < n; i++) {
					arrow(lv, p, stack, look.yRot((float) Math.toRadians((i - (n - 1) / 2.0) * 5)), 3f, 0f, 2 + pw * 0.5);
				}
				Runner.sound(lv, p.position(), SoundEvents.ARROW_SHOOT, 1f, 1f);
			}
			case ARROW_RAIN -> {
				Vec3 c = Runner.targetPos(p, target);
				int n = Math.min(30, 6 + pw * 3);
				for (int i = 0; i < n; i++) {
					Vec3 from = c.add(rnd(p) * 3, 12, rnd(p) * 3);
					arrowAt(lv, p, stack, from, new Vec3(0, -1, 0), 2.5f, 2f, 2 + pw * 0.5);
				}
			}
			case SHOTGUN -> {
				int n = 8 + pw;
				for (int i = 0; i < n; i++) {
					arrow(lv, p, stack, look, 2.5f, 14f, 2);
				}
				Runner.sound(lv, p.position(), SoundEvents.ARROW_SHOOT, 1f, 0.6f);
			}
			case RAILGUN -> {
				Set<LivingEntity> hit = new HashSet<>();
				Vec3 eye = p.getEyePosition();
				for (double d = 1; d <= 30; d += 1) {
					Vec3 c = eye.add(look.scale(d));
					BlockPos bp = BlockPos.containing(c);
					if (!lv.getBlockState(bp).getCollisionShape(lv, bp).isEmpty()) {
						break;
					}
					lv.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0, 0, 0, 0);
					hit.addAll(Runner.near(lv, c, 1.0, p));
				}
				for (LivingEntity t : hit) {
					Runner.hurt(lv, t, lv.damageSources().playerAttack(p), 8 + pw * 3);
				}
				Runner.sound(lv, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4f, 2f);
			}

			// ===================== トライデント
			case THUNDER_SPEAR -> {
				for (int d = 4; d <= 12; d += 4) {
					Vec3 c = p.position().add(flat.scale(d));
					strike(lv, p, new Vec3(c.x, p.getY(), c.z), pw);
				}
			}
			case WHIRLPOOL -> {
				Vec3 c = Runner.targetPos(p, target);
				for (LivingEntity t : Runner.near(lv, c, 6, p)) {
					Vec3 d = c.subtract(t.position());
					Vec3 tan = new Vec3(-d.z, 0, d.x).normalize().scale(0.5);
					Runner.fling(t, tan.add(d.x * 0.1, 0.12, d.z * 0.1));
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
					Runner.hurt(lv, t, lv.damageSources().indirectMagic(p, p), 1 + pw / 2f);
				}
				for (int i = 0; i < 40; i++) {
					double ang = i * 0.5;
					double rr = 1 + i * 0.12;
					lv.sendParticles(ParticleTypes.BUBBLE, c.x + Math.cos(ang) * rr, c.y + 0.3, c.z + Math.sin(ang) * rr, 1, 0, 0, 0, 0);
					lv.sendParticles(ParticleTypes.SPLASH, c.x + Math.cos(ang) * rr, c.y + 0.5, c.z + Math.sin(ang) * rr, 2, 0.1, 0.1, 0.1, 0);
				}
			}

			// ===================== メイス
			case GROUND_SLAM -> {
				double r = 4 + pw * 0.6;
				for (LivingEntity t : Runner.near(lv, p.position(), r, p)) {
					Runner.hurt(lv, t, lv.damageSources().playerAttack(p), 5 + pw * 2.5f);
					Runner.fling(t, new Vec3(0, 0.9, 0));
				}
				ring(lv, ParticleTypes.POOF, p.position(), r);
				lv.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.3, p.getZ(), 3, 1, 0.1, 1, 0);
				Runner.sound(lv, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.6f);
			}
			case GRAVITY_WELL -> {
				Vec3 c = Runner.targetPos(p, target);
				for (LivingEntity t : Runner.near(lv, c, 7, p)) {
					Vec3 d = c.subtract(t.position());
					Runner.fling(t, d.normalize().scale(0.8));
					t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
					Runner.hurt(lv, t, lv.damageSources().indirectMagic(p, p), 2 + pw);
				}
				ring(lv, ParticleTypes.PORTAL, c, 3);
				lv.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 1, c.z, 40, 0.5, 0.5, 0.5, 0.8);
			}

			// ===================== 盾
			case REFLECT -> {
				if (target != null) {
					Runner.hurt(lv, target, lv.damageSources().indirectMagic(p, p), Math.max(1, Runner.ctxAmount * (0.5f + 0.1f * pw)));
					Vec3 d = target.position().subtract(p.position());
					Runner.fling(target, new Vec3(d.x, 0, d.z).normalize().scale(0.7).add(0, 0.3, 0));
					lv.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
				}
			}
			case SANCTUARY -> {
				for (LivingEntity t : Runner.near(lv, p.position(), 4, p)) {
					Vec3 d = t.position().subtract(p.position());
					Runner.fling(t, new Vec3(d.x, 0, d.z).normalize().scale(0.8).add(0, 0.3, 0));
				}
				p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1));
				absorb(p, pw * 2, 600);
				ring(lv, ParticleTypes.END_ROD, p.position(), 3);
			}

			// ===================== 兜・胸当て・レギンス・ブーツ
			case CLAIRVOYANCE -> {
				for (LivingEntity t : Runner.near(lv, p.position(), 24, p)) {
					if (t instanceof Enemy) {
						t.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
					}
				}
			}
			case EVADE -> {
				p.heal(Runner.ctxAmount * Math.min(1f, 0.3f + 0.1f * pw));
				p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1));
				lv.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
			}
			case ENDURE -> {
				if (p.getHealth() < p.getMaxHealth() * 0.3f) {
					p.heal(pw * 2f);
					p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1));
					lv.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.5, p.getZ(), 6, 0.4, 0.4, 0.4, 0);
				}
			}
			case THORNS -> {
				if (target != null) {
					Runner.hurt(lv, target, lv.damageSources().thorns(p), Math.max(1, Runner.ctxAmount * 0.2f * pw));
				}
			}
			case STEALTH -> p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, (secs > 0 ? secs : 4) * 20, 0, false, false));
			case SPRINT_BURST -> {
				if (p.isSprinting()) {
					p.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, Math.max(0, pw / 2), false, false));
				}
			}
			case WATER_WALK -> {
				if (p.isShiftKeyDown()) {
					return;
				}
				BlockPos bp = BlockPos.containing(p.getX(), p.getBoundingBox().minY - 0.5, p.getZ());
				BlockState st = lv.getBlockState(bp);
				Block put = st.is(Blocks.WATER) ? Blocks.ICE : st.is(Blocks.LAVA) ? Blocks.OBSIDIAN : null;
				if (put != null) {
					lv.setBlock(bp, put.defaultBlockState(), 3);
					Runner.TEMPS.add(new Runner.Temp(lv, bp, put, lv.getGameTime() + 60, null));
				}
			}
			case LAND_QUAKE -> {
				double r = 3 + pw * 0.5;
				float dmg = (float) Math.min(30, Runner.ctxAmount * 0.7 + pw);
				for (LivingEntity t : Runner.near(lv, p.position(), r, p)) {
					Runner.hurt(lv, t, lv.damageSources().playerAttack(p), dmg);
					Runner.fling(t, new Vec3(0, 0.7, 0));
				}
				ring(lv, ParticleTypes.POOF, p.position(), r);
				Runner.sound(lv, p.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.8f);
			}

			// ===================== エリトラ・釣り竿・ハサミ
			case TURBO -> {
				if (p.isFallFlying()) {
					Runner.fling(p, p.getDeltaMovement().add(look.scale(0.5 + 0.15 * pw)));
					lv.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY(), p.getZ(), 15, 0.2, 0.2, 0.2, 0.05);
				}
			}
			case SONIC -> {
				if (p.isFallFlying()) {
					for (LivingEntity t : Runner.near(lv, p.position(), 3, p)) {
						Runner.hurt(lv, t, lv.damageSources().playerAttack(p), 3 + pw * 1.5f);
						Vec3 d = t.position().subtract(p.position());
						Runner.fling(t, new Vec3(d.x, 0, d.z).normalize().scale(0.8).add(0, 0.3, 0));
					}
					lv.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
				}
			}
			case HOOK_PULL -> {
				for (LivingEntity t : Runner.frontTargets(p, lv, 20, target).stream().limit(1).toList()) {
					Vec3 d = p.position().subtract(t.position());
					Runner.fling(t, new Vec3(d.x, 0, d.z).normalize().scale(1.0 + 0.1 * pw).add(0, 0.35, 0));
					line(lv, ParticleTypes.CRIT, p.getEyePosition(), t.position().add(0, 1, 0));
				}
			}
			case FISH_LOOT -> {
				for (int i = 0; i < pw; i++) {
					int r = p.getRandom().nextInt(100);
					Item it = r < 2 ? Items.NAUTILUS_SHELL : r < 10 ? Items.EMERALD : r < 30 ? Items.GOLD_NUGGET
							: r < 55 ? Items.COD : r < 80 ? Items.SALMON : Items.TROPICAL_FISH;
					drop(lv, p.position(), new ItemStack(it));
				}
			}
			case SHEAR_AREA -> {
				for (Sheep sh : lv.getEntitiesOfClass(Sheep.class, p.getBoundingBox().inflate(6 + pw), s -> s.readyForShearing())) {
					sh.shear(lv, net.minecraft.sounds.SoundSource.PLAYERS, new ItemStack(Items.SHEARS));
				}
			}
			case LEAF_CUT -> {
				if (Runner.ctxState != null && Runner.ctxState.is(BlockTags.LEAVES)) {
					for (BlockPos bp : flood(lv, Runner.ctxPos, s -> s.is(BlockTags.LEAVES), 30 + pw * 10)) {
						breakBlock(lv, p, bp);
					}
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------ 補助

	/** 吸収ハート(体力 hp 分)。この版では上限属性があるので、吸収の効果として付ける。 */
	private static void absorb(ServerPlayer p, int hp, int ticks) {
		int amp = Math.max(0, (hp + 3) / 4 - 1);
		p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, amp));
	}

	private static double rnd(ServerPlayer p) {
		return p.getRandom().nextDouble() - 0.5;
	}

	private static void drop(ServerLevel lv, Vec3 at, ItemStack s) {
		ItemEntity it = new ItemEntity(lv, at.x, at.y + 0.5, at.z, s);
		it.setDeltaMovement((lv.getRandom().nextDouble() - 0.5) * 0.2, 0.25, (lv.getRandom().nextDouble() - 0.5) * 0.2);
		lv.addFreshEntity(it);
	}

	private static void line(ServerLevel lv, ParticleOptions pt, Vec3 a, Vec3 b) {
		Vec3 d = b.subtract(a);
		int n = Math.max(1, (int) (d.length() * 2));
		for (int i = 0; i <= n; i++) {
			Vec3 c = a.add(d.scale(i / (double) n));
			lv.sendParticles(pt, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		}
	}

	private static void ring(ServerLevel lv, ParticleOptions pt, Vec3 c, double r) {
		for (int k = 1; k <= 3; k++) {
			for (int a = 0; a < 360; a += 20) {
				double rad = Math.toRadians(a);
				lv.sendParticles(pt, c.x + Math.cos(rad) * r * k / 3, c.y + 0.2, c.z + Math.sin(rad) * r * k / 3, 1, 0, 0.05, 0, 0.02);
			}
		}
	}

	/** 前方の扇形(minDot = 視線との内積の下限。大きいほど狭い)にいる相手。 */
	private static List<LivingEntity> cone(ServerPlayer p, ServerLevel lv, double range, double minDot) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		return lv.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), en -> {
			if (en == p || !en.isAlive()) {
				return false;
			}
			Vec3 d = en.position().add(0, en.getBbHeight() / 2, 0).subtract(eye);
			return d.length() <= range && d.normalize().dot(look) > minDot;
		});
	}

	/** 壁にぶつからず進める最も遠い位置(なければ null)。 */
	private static Vec3 advance(ServerPlayer p, ServerLevel lv, double dist, Vec3 dir) {
		Vec3 best = null;
		for (double d = 0.5; d <= dist; d += 0.5) {
			if (!lv.noCollision(p, p.getBoundingBox().move(dir.scale(d)))) {
				break;
			}
			best = p.position().add(dir.scale(d));
		}
		return best;
	}

	private static void strike(ServerLevel lv, ServerPlayer p, Vec3 pos, int pw) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(lv, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setPos(pos);
			bolt.setVisualOnly(true);
			lv.addFreshEntity(bolt);
		}
		for (LivingEntity t : Runner.near(lv, pos, 3.5, p)) {
			Runner.hurt(lv, t, lv.damageSources().lightningBolt(), 5 + pw * 2);
			t.igniteForSeconds(3);
		}
	}

	private static void arrow(ServerLevel lv, ServerPlayer p, ItemStack weapon, Vec3 dir, float speed, float inaccuracy, double dmg) {
		Arrow a = new Arrow(lv, p, new ItemStack(Items.ARROW), weapon);
		a.shoot(dir.x, dir.y, dir.z, speed, inaccuracy);
		a.pickup = AbstractArrow.Pickup.DISALLOWED;
		a.setBaseDamage(dmg);
		lv.addFreshEntity(a);
	}

	private static void arrowAt(ServerLevel lv, ServerPlayer p, ItemStack weapon, Vec3 from, Vec3 dir, float speed, float inaccuracy, double dmg) {
		Arrow a = new Arrow(lv, p, new ItemStack(Items.ARROW), weapon);
		a.setPos(from);
		a.shoot(dir.x, dir.y, dir.z, speed, inaccuracy);
		a.pickup = AbstractArrow.Pickup.DISALLOWED;
		a.setBaseDamage(dmg);
		lv.addFreshEntity(a);
	}

	private static boolean isOre(BlockState s) {
		return s != null && BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("_ore");
	}

	/** 壊してよいブロックなら壊して、ドロップさせる。 */
	private static void breakBlock(ServerLevel lv, ServerPlayer p, BlockPos pos) {
		BlockState s = lv.getBlockState(pos);
		if (s.isAir() || s.hasBlockEntity() || s.getDestroySpeed(lv, pos) < 0 || !s.getFluidState().isEmpty()) {
			return;
		}
		lv.destroyBlock(pos, true, p);
	}

	/** start の隣(斜めも含む)から、条件に合うブロックのつながりを最大 limit 個さがす(start は含めない)。 */
	private static List<BlockPos> flood(ServerLevel lv, BlockPos start, Predicate<BlockState> ok, int limit) {
		List<BlockPos> out = new ArrayList<>();
		if (start == null) {
			return out;
		}
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> q = new ArrayDeque<>();
		seen.add(start);
		q.add(start);
		while (!q.isEmpty() && out.size() < limit) {
			BlockPos c = q.poll();
			for (int x = -1; x <= 1; x++) {
				for (int y = -1; y <= 1; y++) {
					for (int z = -1; z <= 1; z++) {
						BlockPos n = c.offset(x, y, z);
						if (out.size() >= limit || !seen.add(n)) {
							continue;
						}
						if (ok.test(lv.getBlockState(n))) {
							out.add(n);
							q.add(n);
						}
					}
				}
			}
		}
		return out;
	}
}
