package com.magicmod;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 使い魔と敵のエンティティ。見た目はバニラのモデルを流用し(大きさは SCALE 属性で変える)、
 * 中身(HP・能力・弱点)は Familiars / Enemies が決める。
 */
public final class MobClasses {

	private MobClasses() {
	}

	/** 使い魔の共通部分。 */
	public interface Fam {
		@Nullable
		UUID mmOwner();

		void mmSet(UUID owner, Familiars.Kind kind);

		Familiars.Kind mmKind();
	}

	/** 変身した姿の見た目だけを担う、触れられない・倒せないエンティティ(持ち主の位置に重なる)。 */
	public static class FormAvatar extends net.minecraft.world.entity.PathfinderMob {
		public FormAvatar(EntityType<? extends net.minecraft.world.entity.PathfinderMob> t, Level l) {
			super(t, l);
			this.noPhysics = true;
			this.setNoGravity(true);
		}

		@Override
		protected void registerGoals() {
		}

		@Override
		public boolean isPickable() {
			return false;
		}

		@Override
		public boolean isPushable() {
			return false;
		}

		@Override
		public boolean canBeCollidedWith(net.minecraft.world.entity.Entity e) {
			return false;
		}

		@Override
		public boolean hurtServer(net.minecraft.server.level.ServerLevel l, net.minecraft.world.damagesource.DamageSource s, float a) {
			return false;
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}

		@Override
		public void aiStep() {
			super.aiStep();
			if (!level().isClientSide()) {
				Forms.avatarTick(this);
			}
		}
	}

	// ---- 使い魔 ----

	public static class FamWolf extends Wolf implements Fam {
		private UUID owner;
		private Familiars.Kind kind = Familiars.Kind.WOLF;

		public FamWolf(EntityType<? extends Wolf> t, Level l) {
			super(t, l);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(1, new FloatGoal(this));
			goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.3, true));
			goalSelector.addGoal(5, new Familiars.FollowGoal(this));
		}

		@Override
		public boolean doHurtTarget(net.minecraft.server.level.ServerLevel l, net.minecraft.world.entity.Entity t) {
			boolean r = super.doHurtTarget(l, t);
			if (r && t instanceof net.minecraft.world.entity.LivingEntity le) {
				Familiars.onBite(this, le);
			}
			return r;
		}

		@Override
		public void aiStep() {
			super.aiStep();
			if (!level().isClientSide()) {
				Familiars.mobTick(this);
			}
		}

		@Override
		public UUID mmOwner() {
			return owner;
		}

		@Override
		public void mmSet(UUID o, Familiars.Kind k) {
			owner = o;
			kind = k;
		}

		@Override
		public Familiars.Kind mmKind() {
			return kind;
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}

		@Override
		public boolean isAngry() {
			return getTarget() != null;
		}
	}

	public static class FamSlime extends Slime implements Fam {
		private UUID owner;
		private Familiars.Kind kind = Familiars.Kind.MUD_SLIME;

		public FamSlime(EntityType<? extends Slime> t, Level l) {
			super(t, l);
		}

		@Override
		protected void registerGoals() {
		}

		@Override
		public void aiStep() {
			super.aiStep();
			if (!level().isClientSide()) {
				Familiars.mobTick(this);
			}
		}

		@Override
		public void playerTouch(Player p) {
		}

		@Override
		public UUID mmOwner() {
			return owner;
		}

		@Override
		public void mmSet(UUID o, Familiars.Kind k) {
			owner = o;
			kind = k;
		}

		@Override
		public Familiars.Kind mmKind() {
			return kind;
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}
	}

	public static class FamAllay extends Allay implements Fam {
		private UUID owner;
		private Familiars.Kind kind = Familiars.Kind.FAIRY;

		public FamAllay(EntityType<? extends Allay> t, Level l) {
			super(t, l);
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			Familiars.mobTick(this);
		}

		@Override
		public UUID mmOwner() {
			return owner;
		}

		@Override
		public void mmSet(UUID o, Familiars.Kind k) {
			owner = o;
			kind = k;
		}

		@Override
		public Familiars.Kind mmKind() {
			return kind;
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}
	}

	public static class FamParrot extends Parrot implements Fam {
		private UUID owner;
		private Familiars.Kind kind = Familiars.Kind.HAWK;

		public FamParrot(EntityType<? extends Parrot> t, Level l) {
			super(t, l);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(1, new FloatGoal(this));
			goalSelector.addGoal(5, new Familiars.FollowGoal(this));
		}

		@Override
		public void aiStep() {
			super.aiStep();
			if (!level().isClientSide()) {
				Familiars.mobTick(this);
			}
		}

		@Override
		public UUID mmOwner() {
			return owner;
		}

		@Override
		public void mmSet(UUID o, Familiars.Kind k) {
			owner = o;
			kind = k;
		}

		@Override
		public Familiars.Kind mmKind() {
			return kind;
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}
	}

	public static class FamGolem extends IronGolem implements Fam {
		private UUID owner;
		private Familiars.Kind kind = Familiars.Kind.GOLEM;

		public FamGolem(EntityType<? extends IronGolem> t, Level l) {
			super(t, l);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(1, new FloatGoal(this));
			goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, true));
			goalSelector.addGoal(5, new Familiars.FollowGoal(this));
		}

		@Override
		public void aiStep() {
			super.aiStep();
			if (!level().isClientSide()) {
				Familiars.mobTick(this);
			}
		}

		@Override
		public UUID mmOwner() {
			return owner;
		}

		@Override
		public void mmSet(UUID o, Familiars.Kind k) {
			owner = o;
			kind = k;
		}

		@Override
		public Familiars.Kind mmKind() {
			return kind;
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}
	}

	public static class FamBlaze extends Blaze implements Fam {
		private UUID owner;
		private Familiars.Kind kind = Familiars.Kind.ELEMENTAL;

		public FamBlaze(EntityType<? extends Blaze> t, Level l) {
			super(t, l);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(5, new Familiars.FollowGoal(this));
		}

		@Override
		public void aiStep() {
			super.aiStep();
			if (!level().isClientSide()) {
				Familiars.mobTick(this);
			}
		}

		@Override
		public UUID mmOwner() {
			return owner;
		}

		@Override
		public void mmSet(UUID o, Familiars.Kind k) {
			owner = o;
			kind = k;
		}

		@Override
		public Familiars.Kind mmKind() {
			return kind;
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}

		@Override
		public boolean isSensitiveToWater() {
			return false;
		}
	}

	public static class FamVex extends Vex implements Fam {
		private UUID owner;
		private Familiars.Kind kind = Familiars.Kind.GUARDIAN;

		public FamVex(EntityType<? extends Vex> t, Level l) {
			super(t, l);
		}

		@Override
		protected void registerGoals() {
		}

		@Override
		public void aiStep() {
			super.aiStep();
			if (!level().isClientSide()) {
				Familiars.mobTick(this);
			}
		}

		@Override
		public UUID mmOwner() {
			return owner;
		}

		@Override
		public void mmSet(UUID o, Familiars.Kind k) {
			owner = o;
			kind = k;
		}

		@Override
		public Familiars.Kind mmKind() {
			return kind;
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}
	}

	public static class FamGhast extends Ghast implements Fam {
		private UUID owner;
		private Familiars.Kind kind = Familiars.Kind.ANCIENT_DRAGON;

		public FamGhast(EntityType<? extends Ghast> t, Level l) {
			super(t, l);
		}

		@Override
		protected void registerGoals() {
		}

		@Override
		public void aiStep() {
			super.aiStep();
			if (!level().isClientSide()) {
				Familiars.mobTick(this);
			}
		}

		@Override
		public UUID mmOwner() {
			return owner;
		}

		@Override
		public void mmSet(UUID o, Familiars.Kind k) {
			owner = o;
			kind = k;
		}

		@Override
		public Familiars.Kind mmKind() {
			return kind;
		}

		@Override
		public boolean shouldBeSaved() {
			return false;
		}
	}

	// ---- 敵 ----

	public static class EVex extends Vex {
		public EVex(EntityType<? extends Vex> t, Level l) {
			super(t, l);
		}
	}

	public static class EPhantom extends Phantom {
		public EPhantom(EntityType<? extends Phantom> t, Level l) {
			super(t, l);
		}
	}

	public static class ESlime extends Slime {
		public ESlime(EntityType<? extends Slime> t, Level l) {
			super(t, l);
		}

		@Override
		public void die(DamageSource src) {
			setSize(1, false);
			super.die(src);
		}
	}

	public static class ESilverfish extends Silverfish {
		public ESilverfish(EntityType<? extends Silverfish> t, Level l) {
			super(t, l);
		}
	}

	public static class EBlaze extends Blaze {
		public EBlaze(EntityType<? extends Blaze> t, Level l) {
			super(t, l);
		}
	}

	public static class ERavager extends Ravager {
		public ERavager(EntityType<? extends Ravager> t, Level l) {
			super(t, l);
		}
	}

	public static class EDrowned extends Drowned {
		public EDrowned(EntityType<? extends Drowned> t, Level l) {
			super(t, l);
		}
	}

	public static class EGuardian extends Guardian {
		public EGuardian(EntityType<? extends Guardian> t, Level l) {
			super(t, l);
		}
	}

	public static class EShulker extends Shulker {
		public EShulker(EntityType<? extends Shulker> t, Level l) {
			super(t, l);
		}
	}

	public static class EEnderman extends Enderman {
		public EEnderman(EntityType<? extends Enderman> t, Level l) {
			super(t, l);
		}

		@Override
		protected void registerGoals() {
			super.registerGoals();
			targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
		}
	}

	public static class EGolem extends IronGolem {
		public EGolem(EntityType<? extends IronGolem> t, Level l) {
			super(t, l);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
			goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
			goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
			goalSelector.addGoal(8, new RandomLookAroundGoal(this));
			targetSelector.addGoal(1, new HurtByTargetGoal(this));
			targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		}
	}

	public static class ESkeleton extends Skeleton {
		public ESkeleton(EntityType<? extends Skeleton> t, Level l) {
			super(t, l);
		}
	}

	public static class EBreeze extends Breeze {
		public EBreeze(EntityType<? extends Breeze> t, Level l) {
			super(t, l);
		}
	}

	public static class EMagmaCube extends MagmaCube {
		public EMagmaCube(EntityType<? extends MagmaCube> t, Level l) {
			super(t, l);
		}

		@Override
		public void die(DamageSource src) {
			setSize(1, false);
			super.die(src);
		}
	}

	public static class EElderGuardian extends ElderGuardian {
		public EElderGuardian(EntityType<? extends ElderGuardian> t, Level l) {
			super(t, l);
		}
	}

	public static class EWitherSkeleton extends WitherSkeleton {
		public EWitherSkeleton(EntityType<? extends WitherSkeleton> t, Level l) {
			super(t, l);
		}
	}

	public static class EVindicator extends Vindicator {
		public EVindicator(EntityType<? extends Vindicator> t, Level l) {
			super(t, l);
		}
	}

	public static class EWitch extends Witch {
		public EWitch(EntityType<? extends Witch> t, Level l) {
			super(t, l);
		}
	}

	public static class EGhast extends Ghast {
		public EGhast(EntityType<? extends Ghast> t, Level l) {
			super(t, l);
		}
	}

	public static class EEvoker extends Evoker {
		public EEvoker(EntityType<? extends Evoker> t, Level l) {
			super(t, l);
		}
	}
}
