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
		WOLF("wolf", 1, Archetype.PHYSICAL, 1, 1.0f),
		MUD_SLIME("mud_slime", 2, null, 1, 1.0f),
		FAIRY("fairy", 3, Archetype.MAGIC, 1, 0.9f),
		HAWK("hawk", 4, null, 1, 1.0f),
		GOLEM("golem", 5, Archetype.PHYSICAL, 1, 0.8f),
		SPIRIT_WOLVES("spirit_wolves", 6, Archetype.PHYSICAL, 1, 1.0f),
		ELEMENTAL("elemental", 7, Archetype.MAGIC, 1, 0.8f),
		GUARDIAN("guardian", 8, Archetype.PHYSICAL, 1, 1.1f),
		PHOENIX("phoenix", 9, Archetype.MAGIC, 1, 1.2f),
		ANCIENT_DRAGON("ancient_dragon", 10, null, 1, 0.55f);

		public final String id;
		public final int no;
		/** この型だと性能+10%・維持コスト-10%(設計書 13.4)。 */
		public final Archetype bonus;
		public final int count;
		public final float scale;

		Kind(String id, int no, Archetype bonus, int count, float scale) {
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
			return new Balance.FamiliarRow(no, id, "", "", 30, 4, 1);
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
		return k == Kind.SPIRIT_WOLVES || k == Kind.GOLEM || k == Kind.PHOENIX || k == Kind.ANCIENT_DRAGON;
	}

	public static boolean flies(Kind k) {
		return k == Kind.PHOENIX || k == Kind.ANCIENT_DRAGON;
	}

	static double rideSpeed(Kind k) {
		return switch (k) {
			case SPIRIT_WOLVES -> 0.42;
			case GOLEM -> 0.26;
			case PHOENIX -> 0.55;
			default -> 0.7;
		};
	}

	public static int level(PlayerMagic pm, Kind k) {
		return Math.min(10, 1 + pm.familiarExp.getOrDefault(k.id, 0) / 100);
	}

	static float perf(PlayerMagic pm, Kind k) {
		float m = 1 + 0.1f * (level(pm, k) - 1);
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
			if (pm.contracts.size() > 1) {
				pm.familiarPick++;
				Kind next = Kind.byId(pm.contracts.get(Math.floorMod(pm.familiarPick, pm.contracts.size())));
				Casting.msg(p, "使い魔を送還しました(次に呼ぶのは " + (next == null ? "?" : next.ja()) + ")", ChatFormatting.GRAY);
			} else {
				Casting.msg(p, "使い魔を送還しました", ChatFormatting.GRAY);
			}
			return;
		}
		summonAll(p, pm);
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
		m.setHealth(m.getMaxHealth());
		if (m instanceof Slime s) {
			s.setSize(2, false);
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1024, hp));
			m.setHealth(m.getMaxHealth());
		}
		if (m instanceof Parrot par) {
			par.setComponent(net.minecraft.core.component.DataComponents.PARROT_VARIANT, k == Kind.PHOENIX ? Parrot.Variant.RED_BLUE : Parrot.Variant.GRAY);
		}
		Glow.apply(m, k == Kind.PHOENIX ? Element.FIRE : k == Kind.ANCIENT_DRAGON ? Element.GRAVITY : k == Kind.FAIRY || k == Kind.GUARDIAN
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

	/** しゃがみ+右クリックで、自分の使い魔(騎乗できる種類)に乗る。 */
	static void initRide() {
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((pl, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND || !pl.isShiftKeyDown()
					|| !(entity instanceof MobClasses.Fam f) || !(pl instanceof ServerPlayer sp) || ownerOf((LivingEntity) entity) != sp) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			return mount(sp, entity, f.mmKind());
		});
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
		// 飛ぶ使い魔・スライムは自分で動かす
		if (m instanceof MobClasses.FamAllay || m instanceof MobClasses.FamVex || m instanceof MobClasses.FamGhast) {
			Vec3 goal;
			if (target != null && k != Kind.FAIRY) {
				goal = target.position().add(0, k == Kind.ANCIENT_DRAGON ? 4 : 1, 0);
			} else {
				goal = p.position().add(Math.sin(t * 0.05) * 2, 2.2, Math.cos(t * 0.05) * 2);
			}
			m.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, k == Kind.ANCIENT_DRAGON ? 1.0 : 1.4);
			if (target != null && k == Kind.GUARDIAN && m.distanceToSqr(target) < 4 && t % 20 == 0) {
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
					ItemStackHolder.elementalCast(m, p, target, k.row().attack() * perf);
				}
			}
			case PHOENIX -> {
				if (t % 10 == 0) {
					level.sendParticles(ParticleTypes.FLAME, m.getX(), m.getY() + 0.5, m.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
				}
			}
			case ANCIENT_DRAGON -> {
				if (target != null && t % 50 == 0 && m.distanceToSqr(target) < 600) {
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
