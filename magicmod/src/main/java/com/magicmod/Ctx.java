package com.magicmod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 魔法・物理わざ 1 回分の発動状況。当たり判定や状態異常付与のヘルパーをまとめてある。
 * 魔法(Spell)の Form はここのメソッドを組み合わせて書く。
 */
public final class Ctx {

	public static final ResourceKey<DamageType> SPELL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
			Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "spell"));

	public final LivingEntity caster;
	public final ServerLevel level;
	public final Spell spell;
	/** 1 ヒットの基本ダメージ(属性基準値×係数×各種補正)。 */
	public float power;
	/** 状態異常の時間倍率。 */
	public float durMult = 1f;
	private float chanceMult = 1f;
	private int ampBonus;
	/** 物理わざ(物理ダメージ扱い、魔法の弱点倍率なし)。 */
	public boolean physical;
	/** 防御力無視(兜割りなど)。 */
	public boolean pierceArmor;
	/** 魔封じの型(MP/CT 消費なしの基本攻撃)。 */
	public boolean seal;
	public final Vec3 origin;
	public final Vec3 dir;
	public final List<Element> elements = new ArrayList<>();
	public float totalDamage;
	/** 最後に当てたときの相手側倍率(弱点表示用)。 */
	public float lastTargetMult = 1f;
	/** 連鎖・爆発などの追加ヒット中(無限に連鎖しないため)。 */
	public int depth;
	public int hits;
	/** 1 回の発動で同じ相手に何度も状態異常を付けすぎないため。 */
	private final Set<UUID> statusApplied = new HashSet<>();

	public Ctx(LivingEntity caster, Spell spell, float power) {
		this.caster = caster;
		this.level = (ServerLevel) caster.level();
		this.spell = spell;
		this.power = power;
		this.origin = caster.getEyePosition();
		this.dir = aimDir(caster);
		if (spell != null) {
			if (spell.element != null) {
				elements.add(spell.element);
			}
			elements.addAll(spell.extraElements);
			this.chanceMult = spell.curseBonus;
			this.durMult = spell.curseBonus;
		}
	}

	private static Vec3 aimDir(LivingEntity caster) {
		if (caster instanceof Mob m && !(caster instanceof Player) && m.getTarget() != null) {
			LivingEntity t = m.getTarget();
			Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(caster.getEyePosition());
			if (to.lengthSqr() > 1.0E-4) {
				return to.normalize();
			}
		}
		return caster.getLookAngle();
	}

	public float chanceMult() {
		return chanceMult;
	}

	public int ampBonus() {
		return ampBonus;
	}

	public void setAmpBonus(int a) {
		ampBonus = a;
	}

	public void setChanceMult(float c) {
		chanceMult = c;
	}

	public Element element() {
		return spell == null ? null : spell.element;
	}

	// ---------------------------------------------------------------------------------------------
	// 対象の判定

	/** 直接狙った相手(弾・近接・視線)なら動物にも当たる。 */
	public boolean isEnemy(LivingEntity t) {
		return Combat.isEnemy(caster, t, true);
	}

	/** 範囲攻撃の巻き込み対象(敵対しているものだけ。動物・村人は巻き込まない)。 */
	public boolean isAreaEnemy(LivingEntity t) {
		return Combat.isEnemy(caster, t, false);
	}

	public boolean isAlly(LivingEntity t) {
		return Combat.isAlly(caster, t);
	}

	public List<LivingEntity> enemiesIn(AABB box) {
		return level.getEntitiesOfClass(LivingEntity.class, box, this::isAreaEnemy);
	}

	public List<LivingEntity> enemiesNear(Vec3 c, double r) {
		List<LivingEntity> l = enemiesIn(new AABB(c, c).inflate(r));
		l.removeIf(e -> e.position().add(0, e.getBbHeight() / 2, 0).distanceToSqr(c) > (r + e.getBbWidth()) * (r + e.getBbWidth()));
		return l;
	}

	public List<LivingEntity> alliesNear(Vec3 c, double r) {
		List<LivingEntity> l = level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), this::isAlly);
		if (!l.contains(caster) && caster.position().distanceToSqr(c) <= r * r) {
			l.add(caster);
		}
		return l;
	}

	/** 視線の先(または狙っている相手)の敵。 */
	public @Nullable LivingEntity rayTarget(double range) {
		if (caster instanceof Mob m && !(caster instanceof Player)) {
			LivingEntity t = m.getTarget();
			return t != null && t.distanceTo(caster) <= range + t.getBbWidth() ? t : null;
		}
		Vec3 to = origin.add(dir.scale(range));
		BlockHitResult bh = level.clip(new ClipContext(origin, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		if (bh.getType() != HitResult.Type.MISS) {
			to = bh.getLocation();
		}
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(caster, origin, to,
				caster.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.5),
				e -> e instanceof LivingEntity le && isEnemy(le), range * range);
		return eh != null && eh.getEntity() instanceof LivingEntity le ? le : null;
	}

	/** 狙った地点(ブロックか敵)。 */
	public Vec3 aimPoint(double range) {
		if (caster instanceof Mob m && !(caster instanceof Player) && m.getTarget() != null) {
			return m.getTarget().position();
		}
		LivingEntity t = rayTarget(range);
		if (t != null) {
			return t.position();
		}
		Vec3 to = origin.add(dir.scale(range));
		BlockHitResult bh = level.clip(new ClipContext(origin, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		if (bh.getType() != HitResult.Type.MISS) {
			return bh.getLocation();
		}
		// 空中なら真下の地面
		BlockHitResult down = level.clip(new ClipContext(to, to.add(0, -16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		return down.getType() != HitResult.Type.MISS ? down.getLocation() : to;
	}

	// ---------------------------------------------------------------------------------------------
	// 命中

	public DamageSource source(@Nullable Entity direct) {
		if (physical && pierceArmor) {
			return level.damageSources().indirectMagic(caster, caster);
		}
		if (physical) {
			if (caster instanceof Player p) {
				return level.damageSources().playerAttack(p);
			}
			return level.damageSources().mobAttack(caster);
		}
		Holder<DamageType> h = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SPELL_DAMAGE);
		return new DamageSource(h, direct == null ? caster : direct, caster);
	}

	public boolean hit(LivingEntity t, float mult) {
		return hit(t, mult, null);
	}

	/** 敵に当てる: ダメージ + 状態異常 + 追加効果。 */
	public boolean hit(LivingEntity t, float mult, @Nullable Entity direct) {
		if (t == null || !t.isAlive() || !isEnemy(t)) {
			return false;
		}
		float dmg = 0;
		boolean dealsDamage = spell == null || spell.damage;
		if (dealsDamage && mult > 0) {
			float tm = Combat.targetMult(this, t);
			lastTargetMult = tm;
			dmg = power * mult * tm;
			t.setInvulnerableTime(0);
			Combat.CURRENT.set(this);
			try {
				t.hurtServer(level, source(direct), dmg);
			} finally {
				Combat.CURRENT.remove();
			}
		}
		if (spell != null && statusApplied.add(t.getUUID()) || mult >= 0.99f) {
			applyStatuses(t);
		}
		if (spell != null) {
			for (Spell.OnHit h : spell.onHits) {
				h.hit(this, t);
			}
		}
		if (depth == 0 || hits < 6) {
			Vfx.impact(level, t.position().add(0, t.getBbHeight() * 0.55, 0), physical ? null : element(), physical ? 0.5f : 0.6f);
		}
		totalDamage += dmg;
		hits++;
		Combat.afterSpellHit(this, t, dmg);
		return true;
	}

	public void applyStatuses(LivingEntity t) {
		if (spell == null) {
			return;
		}
		float d = durMult * Combat.statusResist(t);
		for (Spell.St st : spell.statuses) {
			st.apply(this, t, d);
		}
	}

	/** 目の前の敵 1 体に近接で当てる(いなければ扇状に探す)。 */
	public @Nullable LivingEntity melee(double range, float mult) {
		LivingEntity t = rayTarget(range);
		if (t == null) {
			List<LivingEntity> l = coneTargets(range, 50);
			t = l.isEmpty() ? null : l.get(0);
		}
		if (t != null) {
			hit(t, mult);
		}
		Vfx.slash(caster, dir, Math.min(range, 4.5), 30, physical ? null : element(), 0.9);
		swing();
		return t;
	}

	public List<LivingEntity> coneTargets(double range, double halfAngleDeg) {
		Vec3 c = caster.position();
		List<LivingEntity> l = enemiesNear(c, range);
		double cos = Math.cos(Math.toRadians(halfAngleDeg));
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		boolean useFlat = flat.lengthSqr() > 1.0E-4;
		Vec3 d = useFlat ? flat.normalize() : dir;
		l.removeIf(e -> {
			Vec3 to = e.position().subtract(c);
			Vec3 tf = useFlat ? new Vec3(to.x, 0, to.z) : to;
			if (tf.lengthSqr() < 1.0) {
				return false;
			}
			return tf.normalize().dot(d) < cos;
		});
		l.sort(Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
		return l;
	}

	/** 扇状範囲(鞭など)。 */
	public List<LivingEntity> cone(double range, double halfAngleDeg, float mult) {
		List<LivingEntity> l = coneTargets(range, halfAngleDeg);
		for (LivingEntity e : l) {
			hit(e, mult);
		}
		Vfx.slash(caster, dir, range, halfAngleDeg, physical ? null : element(), 0.35);
		swing();
		return l;
	}

	public static Vec3 rotateY(Vec3 v, double rad) {
		double c = Math.cos(rad), s = Math.sin(rad);
		return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
	}

	/** 自分の周り。 */
	public List<LivingEntity> areaSelf(double r, float mult) {
		return areaAt(caster.position().add(0, 0.5, 0), r, mult);
	}

	public List<LivingEntity> areaAt(Vec3 c, double r, float mult) {
		List<LivingEntity> l = enemiesNear(c, r);
		for (LivingEntity e : l) {
			hit(e, mult);
		}
		Vfx.shockwave(level, c, r, physical ? null : element());
		if (r >= 4) {
			Vfx.shake(level, c, r * 4, (float) Math.min(1.2, r * 0.12), 8);
		}
		Terrain.react(this, c, r);
		return l;
	}

	public void ring(Vec3 c, double r, ParticleOptions p) {
		int n = (int) Math.max(12, r * 10);
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			particles(p, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0.01);
		}
	}

	/** 直線貫通(光線・雷槍など)。 */
	public List<LivingEntity> beam(double range, double width, float mult) {
		Vec3 to = origin.add(dir.scale(range));
		BlockHitResult bh = level.clip(new ClipContext(origin, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		if (bh.getType() != HitResult.Type.MISS) {
			to = bh.getLocation();
		}
		Vec3 a = origin, b = to;
		List<LivingEntity> l = enemiesIn(new AABB(a, b).inflate(width + 1));
		l.removeIf(e -> !e.getBoundingBox().inflate(width).clip(a, b).isPresent() && !e.getBoundingBox().inflate(width).contains(a));
		for (LivingEntity e : l) {
			hit(e, mult);
		}
		Vfx.beam(level, Vfx.hand(caster), b, physical ? null : element());
		return l;
	}

	/** 魔法弾を撃つ。 */
	public MagicBolt bolt(float speed, float gravity) {
		MagicBolt b = MagicBolt.create(this, speed, gravity);
		level.addFreshEntity(b);
		return b;
	}

	/** その場に残る範囲(竜巻・燃焼床・回復フィールドなど)。 */
	public void field(Vec3 c, double r, int seconds, int everyTicks, BiConsumer<Ctx, List<LivingEntity>> onTick) {
		int times = Math.max(1, seconds * 20 / everyTicks);
		Element e = physical ? null : element();
		Tasks.repeat(0, 2, seconds * 10, i -> Vfx.field(level, c, r, e, level.getGameTime()));
		Tasks.repeat(0, everyTicks, times, i -> {
			if (!caster.isAlive() && !(caster instanceof Player)) {
				return;
			}
			onTick.accept(this, enemiesNear(c, r));
		});
	}

	public void later(int ticks, Runnable r) {
		Tasks.later(ticks, r);
	}

	// ---------------------------------------------------------------------------------------------
	// 移動

	public static void push(LivingEntity t, Vec3 from, double strength, double up) {
		Vec3 d = t.position().subtract(from);
		d = new Vec3(d.x, 0, d.z);
		if (d.lengthSqr() < 1.0E-4) {
			d = new Vec3(0, 0, 1);
		}
		double kbRes = t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
		double s = strength * (1 - Mth.clamp(kbRes, 0, 0.9));
		Vec3 v = d.normalize().scale(s);
		t.setDeltaMovement(t.getDeltaMovement().add(v.x, up * (1 - Mth.clamp(kbRes, 0, 0.9)), v.z));
		t.syncVelocity = true;
	}

	public static void pull(LivingEntity t, Vec3 to, double strength) {
		Vec3 d = to.subtract(t.position());
		if (d.lengthSqr() < 0.5) {
			return;
		}
		double kbRes = t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
		Vec3 v = d.normalize().scale(Math.min(strength, d.length() * 0.5) * (1 - Mth.clamp(kbRes, 0, 0.8)));
		t.setDeltaMovement(v.x, Math.max(t.getDeltaMovement().y, v.y * 0.5 + 0.1), v.z);
		t.syncVelocity = true;
	}

	/** 術者を向いている方向へ移動させる。 */
	public void dash(double strength, double up) {
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = caster.getLookAngle();
		}
		Vec3 v = flat.normalize().scale(strength);
		caster.setDeltaMovement(v.x, up, v.z);
		caster.syncVelocity = true;
		caster.fallDistance = 0;
	}

	public void self(Holder<MobEffect> e, float sec, int amp) {
		caster.addEffect(new MobEffectInstance(e, Math.round(sec * 20), amp));
	}

	/** 味方の状態異常を消す。 */
	public static void cleanse(LivingEntity t) {
		List<Holder<MobEffect>> bad = new ArrayList<>();
		for (MobEffectInstance i : t.getActiveEffects()) {
			if (i.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
				bad.add(i.getEffect());
			}
		}
		bad.forEach(t::removeEffect);
		t.clearFire();
	}

	public float healAmount(float base) {
		return Math.max(1, power * base);
	}

	public void heal(LivingEntity t, float amount) {
		float before = t.getHealth();
		t.heal(amount);
		particles(net.minecraft.core.particles.ParticleTypes.HEART, t.getX(), t.getY() + t.getBbHeight() + 0.2, t.getZ(), 2, 0.3, 0.1, 0.3, 0);
		Vfx.burstOut(level, t.position().add(0, 1, 0), Vfx.dust(0x80FF90, 0.8f), 8, 0.12);
		DamageNumbers.heal(t, t.getHealth() - before);
	}

	// ---------------------------------------------------------------------------------------------
	// 見た目

	public void particles(ParticleOptions p, double x, double y, double z, int n, double dx, double dy, double dz, double speed) {
		level.sendParticles(p, x, y, z, n, dx, dy, dz, speed);
	}

	public void sound(SoundEvent s, float vol, float pitch) {
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	public void soundAt(Vec3 p, SoundEvent s, float vol, float pitch) {
		level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	public void swing() {
		caster.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
	}

	/** 見た目だけの雷。 */
	public void lightning(Vec3 p) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setPos(p);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		Vfx.lightningLine(level, p.add(level.getRandom().nextGaussian() * 2, 18, level.getRandom().nextGaussian() * 2), p, Vfx.palette(Element.THUNDER)[1], 3);
		Vfx.flash(level, p, 40, 0xE8F4FF, 4);
		Vfx.shake(level, p, 24, 0.7f, 6);
	}

	public static DustParticleOptions dust(int rgb, float scale) {
		return new DustParticleOptions(rgb, scale);
	}

	public @Nullable ServerPlayer player() {
		return caster instanceof ServerPlayer p ? p : null;
	}

	public BlockPos blockAt(Vec3 v) {
		return BlockPos.containing(v);
	}
}
