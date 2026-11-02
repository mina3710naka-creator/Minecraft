package com.magicmod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** 魔法弾・投擲物(見た目は属性の結晶や投げた武器)。発動状況(Ctx)を持ったまま飛ぶ。 */
public class MagicBolt extends ThrowableItemProjectile {

	@FunctionalInterface
	public interface Impact {
		/** target は当たった敵(地面なら null)。 */
		void impact(Ctx c, MagicBolt bolt, Vec3 pos, @Nullable LivingEntity target);
	}

	private @Nullable Ctx ctx;
	private float grav;
	private int life = 60;
	private int age;
	public int pierce;
	public boolean homing;
	public boolean boomerang;
	private boolean returning;
	public float hitMult = 1f;
	public Impact impact;
	public @Nullable Impact onExpire;
	/** 毎 tick の追加演出(隕石の尾など)。 */
	public java.util.function.@Nullable Consumer<MagicBolt> onTick;
	private final Set<UUID> hit = new HashSet<>();

	public MagicBolt(EntityType<? extends MagicBolt> type, Level level) {
		super(type, level);
	}

	public static MagicBolt create(Ctx c, float speed, float gravity) {
		MagicBolt b = new MagicBolt(ModEntities.MAGIC_BOLT, c.level);
		b.ctx = c;
		b.grav = gravity;
		b.setOwner(c.caster);
		Vec3 o = c.origin.add(c.dir.scale(0.6)).add(0, -0.15, 0);
		b.setPos(o.x, o.y, o.z);
		b.setItem(new ItemStack(c.physical ? ModItems.weaponVisual(c.caster) : ModItems.crystal(c.element())));
		b.shoot(c.dir.x, c.dir.y, c.dir.z, speed, c.physical ? 1f : 0.2f);
		b.impact = (cc, bolt, pos, t) -> {
			if (t != null) {
				cc.hit(t, bolt.hitMult, bolt);
			}
		};
		return b;
	}

	public MagicBolt life(int ticks) {
		this.life = ticks;
		return this;
	}

	public MagicBolt dir(Vec3 d, float speed) {
		this.shoot(d.x, d.y, d.z, speed, 0.2f);
		return this;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.crystal(Element.FIRE);
	}

	@Override
	protected double getDefaultGravity() {
		return grav;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		if (ctx == null) {
			discard();
			return;
		}
		age++;
		Vfx.trail(ctx.level, position(), new Vec3(xo, yo, zo), ctx.element(), ctx.physical, age);
		if (onTick != null) {
			onTick.accept(this);
		}
		if (homing && age > 3) {
			List<LivingEntity> near = ctx.enemiesNear(position(), 10);
			near.removeIf(e -> hit.contains(e.getUUID()));
			if (!near.isEmpty()) {
				LivingEntity t = near.stream().min((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this))).get();
				Vec3 want = t.position().add(0, t.getBbHeight() / 2, 0).subtract(position()).normalize();
				Vec3 v = getDeltaMovement();
				double sp = v.length();
				setDeltaMovement(v.normalize().scale(0.75).add(want.scale(0.25)).normalize().scale(sp));
			}
		}
		if (boomerang && !returning && age >= life / 2) {
			returning = true;
			hit.clear();
		}
		if (returning) {
			Vec3 back = ctx.caster.getEyePosition().subtract(position());
			if (back.length() < 1.5) {
				discard();
				return;
			}
			setDeltaMovement(back.normalize().scale(Math.max(0.8, getDeltaMovement().length())));
			setNoGravity(true);
		}
		if (age > life) {
			if (onExpire != null) {
				onExpire.impact(ctx, this, position(), null);
			}
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return super.canHitEntity(e) && ctx != null && e instanceof LivingEntity le && ctx.isEnemy(le) && !hit.contains(e.getUUID());
	}

	@Override
	protected void onHitEntity(EntityHitResult r) {
		if (level().isClientSide() || ctx == null || !(r.getEntity() instanceof LivingEntity t)) {
			return;
		}
		hit.add(t.getUUID());
		Vfx.impact(ctx.level, r.getLocation(), ctx.physical ? null : ctx.element(), ctx.physical ? 0.4f : 0.9f);
		impact.impact(ctx, this, r.getLocation(), t);
		if (pierce > 0 || boomerang) {
			pierce--;
			return;
		}
		discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult r) {
		super.onHitBlock(r);
		if (level().isClientSide() || ctx == null) {
			return;
		}
		if (boomerang && !returning) {
			returning = true;
			hit.clear();
			return;
		}
		if (returning) {
			return;
		}
		Vfx.impact(ctx.level, r.getLocation(), ctx.physical ? null : ctx.element(), ctx.physical ? 0.4f : 0.9f);
		if (!ctx.physical) {
			Vfx.debris(ctx.level, r.getLocation(), 0.6, 6);
		}
		impact.impact(ctx, this, r.getLocation(), null);
		discard();
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	public @Nullable Ctx ctx() {
		return ctx;
	}
}
