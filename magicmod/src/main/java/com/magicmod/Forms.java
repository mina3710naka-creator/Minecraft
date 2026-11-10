package com.magicmod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 変身(8 つの姿)。変身の祭壇で解放し、専用キー [N] で変身/解除、[N] を押しながらホイールで姿を切り替える(変身中でもその場で入れ替わる)。
 * 変身中は魔法・物理わざ・武器は使えず、姿ごとの 2 つのスキル([R]=戦闘、[G]=移動)だけを使う。
 * 姿ごとに、移動力の強化・壁のぼり・飛行・すり抜けなどの能力がある。見た目は、プレイヤーを隠し、本人に重なる「変身体」(専用モデルのエンティティ)で描く。
 */
public final class Forms {

	public enum Form {
		VAMPIRE("vampire", "ドラキュラ", Element.DARK, 0.6, 0.3, 0.4, true, false, 1.0f, "吸血の牙", "霧化(すり抜け)"),
		WOLF("wolf", "ウルフ", Element.WIND, 1.0, 0.5, 0.4, true, false, 1.0f, "爪の連撃", "疾走"),
		KONG("kong", "コング", Element.GRAVITY, 0.2, 0.8, 0.6, true, false, 1.5f, "地割れの叩きつけ", "大ジャンプ"),
		DRAGONOID("dragonoid", "ドラゴン人", Element.FIRE, 0.4, 0.3, 0.4, false, true, 1.1f, "ブレス", "飛翔"),
		ANGEL("angel", "天使", Element.LIGHT, 0.3, 0.3, 0.4, false, true, 1.0f, "聖光", "光の翼(ダッシュ)"),
		SHADOW("shadow", "影", Element.CURSE, 0.8, 0.4, 0.5, true, false, 1.0f, "影の刃", "影渡り(すり抜け)"),
		GOLEM("golem", "ゴーレム", Element.WATER, -0.1, 0.0, 0.6, false, false, 1.4f, "岩の拳", "地鳴り"),
		THUNDER("thunder", "雷神", Element.THUNDER, 1.2, 0.3, 0.4, false, false, 1.0f, "連鎖雷撃", "雷走(瞬間移動)");

		public final String id, ja, skillA, skillB;
		public final Element el;
		final double speed, jump, step;
		public final boolean climb, fly;
		public final float scale;

		Form(String id, String ja, Element el, double speed, double jump, double step, boolean climb, boolean fly, float scale, String a, String b) {
			this.id = id;
			this.ja = ja;
			this.el = el;
			this.speed = speed;
			this.jump = jump;
			this.step = step;
			this.climb = climb;
			this.fly = fly;
			this.scale = scale;
			this.skillA = a;
			this.skillB = b;
		}

		public static @Nullable Form byId(String id) {
			for (Form f : values()) {
				if (f.id.equals(id)) {
					return f;
				}
			}
			return null;
		}

		public String desc() {
			StringBuilder sb = new StringBuilder("移動 +" + Math.round(speed * 100) + "%");
			if (climb) {
				sb.append("・壁のぼり");
			}
			if (fly) {
				sb.append("・飛行");
			}
			if (this == VAMPIRE || this == SHADOW) {
				sb.append("・すり抜け");
			}
			return sb.toString();
		}
	}

	private static final Forms INSTANCE = new Forms();
	/** 変身体のエンティティの UUID(サーバーだけ)。 */
	private static final java.util.Map<UUID, UUID> AVATARS = new java.util.HashMap<>();

	private Forms() {
	}

	// ---- 状態(効果の強さ = 姿の番号 + 1。効果はクライアントにも同期されるので、描画・壁のぼり・すり抜けの判定にも使える) ----

	public static @Nullable Form of(@Nullable LivingEntity e) {
		if (e == null) {
			return null;
		}
		MobEffectInstance i = e.getEffect(ModEffects.TRANSFORM);
		if (i == null) {
			return null;
		}
		Form[] all = Form.values();
		return i.getAmplifier() < all.length ? all[i.getAmplifier()] : null;
	}

	public static boolean active(@Nullable LivingEntity e) {
		return of(e) != null;
	}

	public static boolean climbs(LivingEntity e) {
		Form f = of(e);
		return f != null && f.climb && e.horizontalCollision && !e.isShiftKeyDown();
	}

	public static boolean phasing(LivingEntity e) {
		return e.hasEffect(ModEffects.PHASING);
	}

	private static Identifier mid(String s) {
		return Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "form_" + s);
	}

	private static void mod(LivingEntity e, Holder<Attribute> a, String name, double v, AttributeModifier.Operation op) {
		var inst = e.getAttribute(a);
		if (inst == null) {
			return;
		}
		if (v == 0) {
			inst.removeModifier(mid(name));
		} else {
			inst.addOrReplacePermanentModifier(new AttributeModifier(mid(name), v, op));
		}
	}

	private static void applyAttrs(ServerPlayer p, @Nullable Form f) {
		var mul = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
		var add = AttributeModifier.Operation.ADD_VALUE;
		mod(p, Attributes.MOVEMENT_SPEED, "speed", f == null ? 0 : f.speed, mul);
		mod(p, Attributes.JUMP_STRENGTH, "jump", f == null ? 0 : f.jump, mul);
		mod(p, Attributes.STEP_HEIGHT, "step", f == null ? 0 : f.step, add);
		mod(p, Attributes.SAFE_FALL_DISTANCE, "fall", f == null ? 0 : (f == Form.KONG ? 12 : f.fly ? 30 : 4), add);
		mod(p, Attributes.KNOCKBACK_RESISTANCE, "kb", f == Form.KONG || f == Form.GOLEM ? 1.0 : 0, add);
		mod(p, Attributes.ARMOR, "armor", f == Form.GOLEM ? 12 : f == Form.KONG ? 4 : 0, add);
		mod(p, Attributes.SCALE, "scale", f == null ? 0 : 0, mul);
		boolean fly = f != null && f.fly;
		boolean phase = phasing(p);
		if (!p.isCreative() && !p.isSpectator()) {
			p.getAbilities().mayfly = fly || phase;
			if (phase) {
				p.getAbilities().flying = true;
			} else if (!p.getAbilities().mayfly) {
				p.getAbilities().flying = false;
			}
			p.onUpdateAbilities();
		}
	}

	// ---- 変身・解除・切り替え ----

	private static int unlockedCount(PlayerMagic pm) {
		return pm.forms.size();
	}

	public static List<Form> unlocked(PlayerMagic pm) {
		List<Form> l = new ArrayList<>();
		for (Form f : Form.values()) {
			if (pm.forms.contains(f.id)) {
				l.add(f);
			}
		}
		return l;
	}

	public static void toggle(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		long now = p.level().getGameTime();
		if (now < pm.cooldownEnd("form:switch")) {
			return;
		}
		if (active(p)) {
			off(p, true);
			return;
		}
		List<Form> un = unlocked(pm);
		if (un.isEmpty()) {
			Casting.msg(p, "変身を解放していません(変身の祭壇で解放)", ChatFormatting.GRAY);
			return;
		}
		Form f = Form.byId(selected(pm));
		on(p, f != null && pm.forms.contains(f.id) ? f : un.get(0));
	}

	public static String selected(PlayerMagic pm) {
		List<Form> un = unlocked(pm);
		if (un.isEmpty()) {
			return "";
		}
		return un.get(Math.floorMod(pm.formSel, un.size())).id;
	}

	/** [N]+ホイール: 姿を選ぶ。変身中なら、その場で次の姿に切り替わる(解除しなくていい)。 */
	public static void pick(ServerPlayer p, int dir) {
		PlayerMagic pm = PlayerMagic.of(p);
		List<Form> un = unlocked(pm);
		if (un.isEmpty()) {
			Casting.msg(p, "変身を解放していません(変身の祭壇で解放)", ChatFormatting.GRAY);
			return;
		}
		pm.formSel = Math.floorMod(pm.formSel + dir, un.size());
		pm.dirty = true;
		Form f = un.get(pm.formSel);
		StringBuilder sb = new StringBuilder("変身: ");
		for (Form u : un) {
			sb.append(u == f ? "§e▶ " : "§7").append(u.ja).append(u == f ? "§r  " : "  ");
		}
		Casting.msg(p, sb.toString(), ChatFormatting.WHITE);
		p.level().playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), p.getSoundSource(), 0.3f, 1.6f);
		if (active(p) && p.level().getGameTime() >= pm.cooldownEnd("form:switch")) {
			on(p, f);
		}
	}

	static void on(ServerPlayer p, Form f) {
		PlayerMagic pm = PlayerMagic.of(p);
		ServerLevel level = (ServerLevel) p.level();
		boolean was = active(p);
		pm.cooldowns.put("form:switch", level.getGameTime() + 12);
		p.removeEffect(ModEffects.TRANSFORM);
		p.addEffect(new MobEffectInstance(ModEffects.TRANSFORM, 60, f.ordinal(), false, false, false));
		applyAttrs(p, f);
		removeAvatar(p);
		spawnAvatar(p, f);
		Vec3 at = p.position();
		FxDisplay.circle(level, at.add(0, 0.05, 0), f.el, 2.5, 20, 6f);
		Vfx.burstOut(level, at.add(0, 1, 0), Fx.of(f.el), was ? 18 : 40, 0.35);
		Vfx.sound(level, at, was ? ModSounds.SKILL_USE : ModSounds.EVOLVE, 1f, was ? 1.4f : 1.1f);
		Casting.msg(p, f.ja + (was ? " に切り替え" : " に変身!") + "  [R] " + f.skillA + " / [G] " + f.skillB, f.el.fmt);
	}

	public static void off(ServerPlayer p, boolean fx) {
		Form f = of(p);
		p.removeEffect(ModEffects.TRANSFORM);
		p.removeEffect(ModEffects.PHASING);
		if (!p.isSpectator()) {
			p.setNoGravity(false);
		}
		applyAttrs(p, null);
		removeAvatar(p);
		if (f != null && fx && p.level() instanceof ServerLevel level) {
			Vfx.burstOut(level, p.position().add(0, 1, 0), Fx.of(f.el), 24, 0.3);
			Vfx.sound(level, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.8f, 1.5f);
			Casting.msg(p, "変身を解除", ChatFormatting.GRAY);
		}
		PlayerMagic.of(p).cooldowns.put("form:switch", p.level().getGameTime() + 12);
	}

	// ---- 変身体(見た目) ----

	private static void spawnAvatar(ServerPlayer p, Form f) {
		ServerLevel level = (ServerLevel) p.level();
		var type = ModEntities.TYPES.get("form_" + f.id);
		if (type == null) {
			return;
		}
		var e = type.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
		if (!(e instanceof Mob m)) {
			return;
		}
		m.snapTo(p.getX(), p.getY(), p.getZ(), p.yBodyRot, p.getXRot());
		if (m.getAttribute(Attributes.SCALE) != null) {
			m.getAttribute(Attributes.SCALE).setBaseValue(f.scale);
		}
		m.setCustomName(Component.literal("avatar:" + p.getUUID()));
		m.setCustomNameVisible(false);
		m.setSilent(true);
		m.setPersistenceRequired();
		level.addFreshEntity(m);
		AVATARS.put(p.getUUID(), m.getUUID());
	}

	private static void removeAvatar(ServerPlayer p) {
		UUID id = AVATARS.remove(p.getUUID());
		if (id != null && p.level() instanceof ServerLevel level) {
			var e = level.getEntity(id);
			if (e != null) {
				e.discard();
			}
		}
	}

	/** 変身体が持ち主の位置・向きに重なるようにする(持ち主の tick の最後に呼ぶ)。 */
	private static void syncAvatar(ServerPlayer p, Form f) {
		ServerLevel level = (ServerLevel) p.level();
		UUID id = AVATARS.get(p.getUUID());
		var e = id == null ? null : level.getEntity(id);
		if (!(e instanceof Mob m) || !m.isAlive()) {
			AVATARS.remove(p.getUUID());
			spawnAvatar(p, f);
			return;
		}
		m.setPos(p.getX(), p.getY(), p.getZ());
		m.setYRot(p.yBodyRot);
		m.yRotO = p.yBodyRotO;
		m.setYBodyRot(p.yBodyRot);
		m.setYHeadRot(p.getYHeadRot());
		m.setXRot(p.getXRot());
		m.setDeltaMovement(p.getDeltaMovement());
		m.setPose(p.isShiftKeyDown() ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
	}

	// ---- 毎 tick ----

	static void tick(ServerPlayer p, PlayerMagic pm) {
		Form f = of(p);
		if (f == null) {
			if (AVATARS.containsKey(p.getUUID())) {
				removeAvatar(p);
				applyAttrs(p, null);
			}
			return;
		}
		long now = p.level().getGameTime();
		if (now % 20 == 0) {
			p.addEffect(new MobEffectInstance(ModEffects.TRANSFORM, 60, f.ordinal(), false, false, false));
			if (!p.isCreative()) {
				pm.mp -= 1.5f;
				if (pm.mp <= 0) {
					pm.mp = 0;
					off(p, true);
					Casting.msg(p, "MP が尽きて変身が解けた", ChatFormatting.BLUE);
					return;
				}
			}
			applyAttrs(p, f);
		}
		// すり抜けの終わりで壁の中に残らないように、出られるまで延長する
		if (!phasing(p) && !p.isSpectator() && p.level().getBlockCollisions(p, p.getBoundingBox().deflate(0.05)).iterator().hasNext()
				&& pm.cooldownEnd("form:phaseend") < now) {
			// 壁の中に残ったら、真上の空いている場所へ押し出す。上が埋まっていれば出られるまで延長する
			boolean freed = false;
			for (int i = 1; i <= 48 && !freed; i++) {
				var bb = p.getBoundingBox().move(0, i * 0.25, 0);
				if (!p.level().getBlockCollisions(p, bb).iterator().hasNext()) {
					p.teleportTo(p.getX(), p.getY() + i * 0.25, p.getZ());
					freed = true;
				}
			}
			if (!freed) {
				p.addEffect(new MobEffectInstance(ModEffects.PHASING, 20, 0, false, false, false));
				pm.cooldowns.put("form:phaseend", now + 5);
			}
		}
		// すり抜け中は毎 tick 飛行状態と無重力を保つ(落下して世界の外へ出ないように)
		boolean ph = phasing(p);
		if (ph && !p.isCreative() && !p.isSpectator()) {
			var ab = p.getAbilities();
			if (!ab.mayfly || !ab.flying) {
				ab.mayfly = true;
				ab.flying = true;
				p.onUpdateAbilities();
			}
			p.setNoGravity(true);
			p.fallDistance = 0;
		} else if (p.isNoGravity() && !p.isSpectator()) {
			p.setNoGravity(false);
			if (!p.isCreative() && !f.fly && p.getAbilities().flying) {
				applyAttrs(p, f);
			}
		}
		syncAvatar(p, f);
		if (now % 4 == 0) {
			Vfx.near((ServerLevel) p.level(), Fx.of(f.el), p.getX(), p.getY() + 1, p.getZ(), 1, 0.4, 0.6, 0.4, 0.01);
		}
	}

	/** 変身体のエンティティから呼ぶ: 持ち主がいなくなったら消える。 */
	public static void avatarTick(Mob m) {
		var n = m.getCustomName();
		if (n == null || !n.getString().startsWith("avatar:")) {
			return;
		}
		try {
			UUID owner = UUID.fromString(n.getString().substring(7));
			var p = m.level().getPlayerByUUID(owner);
			if (p == null || !p.isAlive() || !active(p) || AVATARS.get(owner) == null || !AVATARS.get(owner).equals(m.getUUID())) {
				m.discard();
			}
		} catch (IllegalArgumentException e) {
			m.discard();
		}
	}

	// ---- スキル ----

	private static float power(ServerPlayer p, Form f, float k) {
		return Math.max(6f, Calc.spellPower(p, Spells.get(f.el, 1), 3, ItemStack.EMPTY)) * k;
	}

	public static void skill(ServerPlayer p, int which) {
		Form f = of(p);
		if (f == null) {
			return;
		}
		PlayerMagic pm = PlayerMagic.of(p);
		long now = p.level().getGameTime();
		String key = "form:" + (which == 1 ? "a" : "b");
		if (now < pm.cooldownEnd(key)) {
			Casting.msg(p, (which == 1 ? f.skillA : f.skillB) + ": 回復中(" + (pm.cooldownEnd(key) - now) / 20 + "秒)", ChatFormatting.GRAY);
			return;
		}
		pm.cooldowns.put(key, now + (which == 1 ? 90 : 140));
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.component.SwingAnimation(net.minecraft.world.item.SwingAnimationType.WHACK, 8), true);
		UUID aid = AVATARS.get(p.getUUID());
		if (aid != null && ((ServerLevel) p.level()).getEntity(aid) instanceof Mob am) {
			Familiars.swing(am);
		}
		if (which == 1) {
			combat(p, f);
		} else {
			movement(p, f);
		}
		Vfx.sound((ServerLevel) p.level(), p.position(), ModSounds.SKILL_USE, 1f, 0.9f);
	}

	private static Vec3 flat(ServerPlayer p) {
		Vec3 d = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
		return d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : d.normalize();
	}

	private static void combat(ServerPlayer p, Form f) {
		ServerLevel level = (ServerLevel) p.level();
		Ctx c = new Ctx(p, Spells.get(f.el, 1), power(p, f, 1.5f));
		Vec3 d = flat(p);
		switch (f) {
			case VAMPIRE -> {
				List<LivingEntity> near = c.enemiesNear(p.position().add(d.scale(2)), 4);
				for (LivingEntity e : near) {
					float before = c.totalDamage;
					c.hit(e, 3.2f);
					p.heal((c.totalDamage - before) * 0.4f);
					ModEffects.stack(e, ModEffects.BLEED, 120, 4);
					Vfx.burstOut(level, e.position().add(0, 1, 0), ParticleTypes.DAMAGE_INDICATOR, 8, 0.2);
					break;
				}
				Vfx.slash(p, d, 3.6, 60, Element.DARK, 0.5);
			}
			case WOLF -> {
				for (int i = 0; i < 3; i++) {
					int k = i;
					c.later(i * 3, () -> {
						Vfx.slash(p, d, 3.8, 75, Element.WIND, k % 2 == 0 ? 0.9 : -0.9);
						c.areaAt(p.position().add(d.scale(1.8)), 3.4, 0.9f).forEach(e -> ModEffects.stack(e, ModEffects.BLEED, 100, 4));
					});
				}
			}
			case KONG -> {
				Vec3 at = p.position().add(d.scale(4));
				FxDisplay.dangerCircle(level, at, 6, 6);
				c.later(6, () -> {
					c.areaAt(at, 6, 2.6f).forEach(e -> ModEffects.apply(e, ModEffects.KNOCKDOWN, 40, 0));
					Vfx.debris(level, at, 5, 50);
					Vfx.shake(level, at, 28, 1.0f, 12);
					Vfx.sound(level, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.4f, 0.7f);
				});
			}
			case DRAGONOID -> {
				Vfx.sound(level, p.position(), ModSounds.BOSS_ROAR, 1f, 1.3f);
				for (int i = 0; i < 14; i++) {
					int k = i;
					c.later(k * 2, () -> {
						if (p.isAlive()) {
							Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.8)), dd = p.getLookAngle();
							for (int q = 0; q < 10; q++) {
								Vfx.move(level, q % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, from,
										dd.scale(0.9 + level.getRandom().nextDouble() * 0.6).add(level.getRandom().nextGaussian() * 0.1, level.getRandom().nextGaussian() * 0.08,
												level.getRandom().nextGaussian() * 0.1));
							}
							c.cone(14, 24, 0.35f).forEach(e -> ModEffects.apply(e, ModEffects.BURN, 80, 2));
						}
					});
				}
			}
			case ANGEL -> {
				Vfx.pillar(level, p.position(), 8, 1.5, ParticleTypes.END_ROD, 8);
				FxDisplay.ring(level, p.position(), Element.LIGHT, 8, 12);
				c.areaSelf(8, 2.0f);
				p.heal(p.getMaxHealth() * 0.3f);
				for (LivingEntity a : c.alliesNear(p.position(), 8)) {
					c.heal(a, c.healAmount(0.6f));
				}
			}
			case SHADOW -> {
				Vec3 from = p.position();
				p.setDeltaMovement(d.x * 2.2, 0.15, d.z * 2.2);
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
				java.util.Set<UUID> hit = new java.util.HashSet<>();
				Tasks.repeat(0, 1, 8, i -> {
					for (LivingEntity e : c.enemiesNear(p.position(), 2.6)) {
						if (hit.add(e.getUUID())) {
							c.hit(e, 2.4f);
							ModEffects.apply(e, MobEffects.BLINDNESS, 60, 0);
						}
					}
					Vfx.near(level, ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
					Vfx.slash(p, d, 3.0, 50, Element.CURSE, 0.4);
				});
				p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 4, false, false, false));
			}
			case GOLEM -> {
				Vfx.slash(p, d, 3.4, 40, Element.GRAVITY, 0.2);
				c.cone(3.6, 45, 4.5f).forEach(e -> {
					Ctx.push(e, p.position(), 1.8, 0.5);
					ModEffects.apply(e, ModEffects.STUN, 40, 0);
				});
				Vfx.debris(level, p.position().add(d.scale(2.5)), 3, 30);
				Vfx.shake(level, p.position(), 16, 0.6f, 6);
			}
			case THUNDER -> {
				List<LivingEntity> near = c.enemiesNear(p.position(), 14);
				near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
				Vec3[] prev = { p.getEyePosition() };
				for (int i = 0; i < Math.min(6, near.size()); i++) {
					LivingEntity e = near.get(i);
					c.later(i * 3, () -> {
						if (e.isAlive()) {
							Vec3 q = e.position().add(0, e.getBbHeight() / 2, 0);
							Vfx.lightningLine(level, prev[0], q, Vfx.palette(Element.THUNDER)[1], 2);
							prev[0] = q;
							c.hit(e, 2.0f);
							ModEffects.apply(e, ModEffects.SHOCK, 80, 1);
						}
					});
				}
				if (near.isEmpty()) {
					c.lightning(p.position().add(d.scale(6)));
				}
			}
		}
	}

	private static void movement(ServerPlayer p, Form f) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 look = p.getLookAngle(), d = flat(p);
		switch (f) {
			case VAMPIRE -> {
				p.addEffect(new MobEffectInstance(ModEffects.PHASING, 60, 0, false, false, false));
				applyAttrs(p, f);
				Vfx.burstOut(level, p.position().add(0, 1, 0), ParticleTypes.SQUID_INK, 30, 0.4);
			}
			case SHADOW -> {
				p.addEffect(new MobEffectInstance(ModEffects.PHASING, 100, 0, false, false, false));
				applyAttrs(p, f);
				Vfx.burstOut(level, p.position().add(0, 1, 0), ParticleTypes.SQUID_INK, 30, 0.4);
			}
			case WOLF -> {
				p.addEffect(new MobEffectInstance(MobEffects.SPEED, 80, 4, false, false, false));
				Vfx.burstOut(level, p.position().add(0, 0.5, 0), ParticleTypes.CLOUD, 20, 0.3);
			}
			case KONG -> {
				p.setDeltaMovement(d.x * 1.2, 1.5, d.z * 1.2);
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
				Ctx c = new Ctx(p, Spells.get(f.el, 1), power(p, f, 1.2f));
				Tasks.repeat(8, 2, 20, i -> {
					if (p.onGround() && i > 0 && p.getDeltaMovement().y < 0.1 && p.fallDistance <= 0.5) {
						return;
					}
				});
				c.later(24, () -> {
					c.areaAt(p.position(), 5, 1.5f).forEach(e -> ModEffects.apply(e, ModEffects.KNOCKDOWN, 30, 0));
					Vfx.shake(level, p.position(), 20, 0.7f, 8);
					Vfx.debris(level, p.position(), 3, 30);
				});
			}
			case DRAGONOID -> {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				p.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 2, false, false, false));
				p.setDeltaMovement(look.x * 1.0, 0.8, look.z * 1.0);
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
			}
			case ANGEL -> {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				p.setDeltaMovement(look.x * 2.0, look.y * 1.2 + 0.2, look.z * 2.0);
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
				p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 3, false, false, false));
				Vfx.pillar(level, p.position(), 3, 0.8, ParticleTypes.END_ROD, 4);
			}
			case GOLEM -> {
				Ctx c = new Ctx(p, Spells.get(f.el, 1), power(p, f, 1.0f));
				c.areaSelf(8, 1.2f).forEach(e -> {
					ModEffects.apply(e, MobEffects.SLOWNESS, 100, 3);
					if (e instanceof Mob m) {
						m.setTarget(p);
					}
				});
				p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 120, 3, false, false, false));
				Vfx.debris(level, p.position(), 5, 40);
				Vfx.shake(level, p.position(), 24, 0.9f, 10);
			}
			case THUNDER -> {
				Vec3 from = p.position();
				Vec3 to = from;
				for (int i = 1; i <= 24; i++) {
					Vec3 q = from.add(d.scale(i * 0.5));
					var bp = net.minecraft.core.BlockPos.containing(q.x, q.y + 0.5, q.z);
					if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty() || !level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty()) {
						break;
					}
					to = q;
				}
				Vfx.lightningLine(level, from.add(0, 1, 0), to.add(0, 1, 0), Vfx.palette(Element.THUNDER)[1], 2);
				p.teleportTo(to.x, to.y, to.z);
				Vfx.flash(level, to, 20, 0xFFFFFF, 3);
				Ctx c = new Ctx(p, Spells.get(f.el, 1), power(p, f, 1.0f));
				c.areaAt(to, 3.5, 1.0f);
			}
		}
	}

	// ---- 祭壇 ----

	public static Cost cost(Form f) {
		return Cost.of().add(ModItems.core(f.el), 1).add(ModItems.essence(f.el), 2).add(net.minecraft.world.item.Items.EMERALD, 8);
	}

	public static @Nullable String unlock(ServerPlayer p, Form f) {
		PlayerMagic pm = PlayerMagic.of(p);
		if (pm.forms.contains(f.id)) {
			return null;
		}
		if (!cost(f).take(p)) {
			return "素材が足りません";
		}
		pm.forms.add(f.id);
		pm.dirty = true;
		p.sendSystemMessage(Component.literal("変身「" + f.ja + "」を解放した! [N] で変身、[N]+ホイールで切り替え").withStyle(f.el.fmt));
		p.level().playSound(null, p.blockPosition(), ModSounds.EVOLVE, p.getSoundSource(), 1f, 1f);
		return null;
	}

	// ---- 登録(攻撃・アイテム使用の制限、後始末) ----

	public static void init() {
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((pl, world, hand, entity, hit) -> active(pl)
				? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((pl, world, hand) -> {
			ItemStack s = pl.getItemInHand(hand);
			if (active(pl) && (s.getItem() instanceof WandItem || s.getItem() instanceof WeaponItem || Books.isCaster(s))) {
				return net.minecraft.world.InteractionResult.FAIL;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
			if (e instanceof ServerPlayer p) {
				off(p, false);
			}
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> removeAvatar(h.player));
	}
}
