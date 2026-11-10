package com.magicmod;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** MOD 独自の状態異常・補助効果。 */
public final class ModEffects {

	/** 名前 → 日本語名(言語ファイル生成用にも使う)。 */
	public static final Map<String, String> NAMES = new LinkedHashMap<>();

	public static final Holder<MobEffect> BLEED = reg("bleed", "出血", new Dot(MobEffectCategory.HARMFUL, 0xB0202A, 40, 0.75f));
	public static final Holder<MobEffect> BURN = reg("burn", "火傷", new Dot(MobEffectCategory.HARMFUL, 0xFF7A1A, 20, 0.5f));
	public static final Holder<MobEffect> SHOCK = reg("shock", "感電", new Shock());
	public static final Holder<MobEffect> PARALYSIS = reg("paralysis", "麻痺", root("paralysis", new Root(0xF0E060, false)));
	public static final Holder<MobEffect> STUN = reg("stun", "気絶", stun("stun", new Root(0xE8E8E8, true)));
	public static final Holder<MobEffect> BIND = reg("bind", "拘束", root("bind", new Root(0x6A4CC0, false)));
	public static final Holder<MobEffect> KNOCKDOWN = reg("knockdown", "転倒", stun("knockdown", new Root(0x8C6A3C, true)));
	public static final Holder<MobEffect> DROWN = reg("drown", "溺れ", new Drown());
	public static final Holder<MobEffect> FEAR = reg("fear", "恐怖", new Fear());
	public static final Holder<MobEffect> CONFUSION = reg("confusion", "混乱", new Marker(MobEffectCategory.HARMFUL, 0xC060C0));
	public static final Holder<MobEffect> INACCURACY = reg("inaccuracy", "命中率低下", new Marker(MobEffectCategory.HARMFUL, 0x909090));
	public static final Holder<MobEffect> ARMOR_BREAK = reg("armor_break", "防御力低下",
			new Marker(MobEffectCategory.HARMFUL, 0x705030).addAttributeModifier(Attributes.ARMOR, id("armor_break"), -0.3,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	public static final Holder<MobEffect> SILENCE = reg("silence", "詠唱不可", new Marker(MobEffectCategory.HARMFUL, 0x404060));
	public static final Holder<MobEffect> MANA_SEAL = reg("mana_seal", "MP回復不可", new Marker(MobEffectCategory.HARMFUL, 0x2040A0));
	public static final Holder<MobEffect> DOOM = reg("doom", "ドゥームカース", new Doom());
	public static final Holder<MobEffect> TRANSFORM = reg("transform", "変身", new Marker(MobEffectCategory.NEUTRAL, 0xFF40A0));
	public static final Holder<MobEffect> PHASING = reg("phasing", "すり抜け", new Marker(MobEffectCategory.NEUTRAL, 0x6040A0));
	public static final Holder<MobEffect> WET = reg("wet", "濡れ", new Marker(MobEffectCategory.NEUTRAL, 0x4080FF));
	public static final Holder<MobEffect> STAGGER = reg("stagger", "ふらつき",
			new Marker(MobEffectCategory.HARMFUL, 0xA0A080).addAttributeModifier(Attributes.MOVEMENT_SPEED, id("stagger"), -0.35,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	public static final Holder<MobEffect> REBIRTH = reg("rebirth", "蘇生の加護", new Marker(MobEffectCategory.BENEFICIAL, 0xFFD040));
	public static final Holder<MobEffect> WIND_SHIELD = reg("wind_shield", "ウィンドシールド", new Marker(MobEffectCategory.BENEFICIAL, 0xA0FFC0));
	public static final Holder<MobEffect> AQUA_SHIELD = reg("aqua_shield", "アクアシールド", new Marker(MobEffectCategory.BENEFICIAL, 0x40A0FF));
	public static final Holder<MobEffect> DOUBLE_JUMP = reg("double_jump", "エアステップ", new Marker(MobEffectCategory.BENEFICIAL, 0xC0FFE0));
	public static final Holder<MobEffect> JET_WING = reg("jet_wing", "ジェットウィング", new JetWing());
	public static final Holder<MobEffect> HEAT_HAZE = reg("heat_haze", "ヒートヘイズ", new Marker(MobEffectCategory.BENEFICIAL, 0xFFA040));
	public static final Holder<MobEffect> COUNTER = reg("counter", "カウンタースタンス", new Marker(MobEffectCategory.BENEFICIAL, 0xD0D0FF));
	public static final Holder<MobEffect> CLONE = reg("shadow_clone", "シャドウクローン", new Marker(MobEffectCategory.BENEFICIAL, 0x302040));
	public static final Holder<MobEffect> OVERCHARGE = reg("overcharge", "感電蓄積", new Marker(MobEffectCategory.HARMFUL, 0xFFFF80));

	private ModEffects() {
	}

	private static Identifier id(String s) {
		return Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, s);
	}

	private static Holder<MobEffect> reg(String name, String ja, MobEffect effect) {
		NAMES.put(name, ja);
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id(name), effect);
	}

	private static MobEffect root(String n, MobEffect e) {
		return e.addAttributeModifier(Attributes.MOVEMENT_SPEED, id(n + "_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
				.addAttributeModifier(Attributes.JUMP_STRENGTH, id(n + "_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
				.addAttributeModifier(Attributes.FLYING_SPEED, id(n + "_fly"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static MobEffect stun(String n, MobEffect e) {
		return root(n, e).addAttributeModifier(Attributes.ATTACK_DAMAGE, id(n + "_attack"), -1.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	public static void init() {
	}

	/** 付与(重ねがけは長い方・強い方を残す)。 */
	public static void apply(LivingEntity target, Holder<MobEffect> effect, int ticks, int amp) {
		if (ticks <= 0) {
			return;
		}
		target.addEffect(new MobEffectInstance(effect, ticks, Math.max(0, amp)));
	}

	/** 蓄積型: すでに掛かっていれば強さを 1 段上げる(上限 max)。 */
	public static void stack(LivingEntity target, Holder<MobEffect> effect, int ticks, int max) {
		MobEffectInstance cur = target.getEffect(effect);
		int amp = cur == null ? 0 : Math.min(max, cur.getAmplifier() + 1);
		target.addEffect(new MobEffectInstance(effect, Math.max(ticks, cur == null ? 0 : cur.getDuration()), amp));
	}

	/** 何もしない目印用(判定は他の場所で has を見る)。 */
	public static class Marker extends MobEffect {
		public Marker(MobEffectCategory c, int color) {
			super(c, color);
		}
	}

	/** 継続ダメージ。 */
	static class Dot extends MobEffect {
		private final int interval;
		private final float perLevel;

		Dot(MobEffectCategory c, int color, int interval, float perLevel) {
			super(c, color);
			this.interval = interval;
			this.perLevel = perLevel;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amp) {
			return tickCount % interval == 0;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
			Combat.hurtClean(mob, level, mob.damageSources().magic(), perLevel * (amp + 1) * Combat.dotScale(mob));
			return true;
		}
	}

	/** 感電: 小さな継続ダメージ、たまに一瞬動けなくなる。濡れていると痛い。 */
	static class Shock extends MobEffect {
		Shock() {
			super(MobEffectCategory.HARMFUL, 0xF5E050);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amp) {
			return tickCount % 20 == 0;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
			float d = 0.4f * (amp + 1) * (mob.hasEffect(WET) || mob.isInWaterOrRain() ? 2f : 1f);
			Combat.hurtClean(mob, level, mob.damageSources().magic(), d * Combat.dotScale(mob));
			if (mob.getRandom().nextFloat() < 0.12f + 0.05f * amp) {
				apply(mob, PARALYSIS, 10, 0);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 4, 0.3, 0.4, 0.3, 0.05);
			return true;
		}
	}

	/** 足止め系(麻痺・気絶・拘束・転倒)。飛ぶ敵も止める。 */
	static class Root extends MobEffect {
		private final boolean stun;

		Root(int color, boolean stun) {
			super(MobEffectCategory.HARMFUL, color);
			this.stun = stun;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amp) {
			return true;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
			if (!(mob instanceof Player)) {
				Vec3 v = mob.getDeltaMovement();
				mob.setDeltaMovement(0, Math.min(v.y, 0), 0);
				if (mob instanceof Mob m) {
					m.getNavigation().stop();
					if (stun) {
						m.setTarget(null);
					}
				}
			}
			return true;
		}
	}

	/** 溺れ: 息が減っていき、なくなるとダメージ。 */
	static class Drown extends MobEffect {
		Drown() {
			super(MobEffectCategory.HARMFUL, 0x2050C0);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amp) {
			return tickCount % 10 == 0;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
			mob.setAirSupply(Math.max(-20, mob.getAirSupply() - 30 * (amp + 1)));
			if (mob.getAirSupply() <= 0) {
				Combat.hurtClean(mob, level, mob.damageSources().drown(), 1.0f * (amp + 1) * Combat.dotScale(mob));
			}
			level.sendParticles(ParticleTypes.BUBBLE, mob.getX(), mob.getEyeY(), mob.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
			return true;
		}
	}

	/** 恐怖: 近くのプレイヤー(または敵)から逃げる。 */
	static class Fear extends MobEffect {
		Fear() {
			super(MobEffectCategory.HARMFUL, 0x5A2A7A);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amp) {
			return tickCount % 10 == 0;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
			if (mob instanceof Mob m && !(mob instanceof Player)) {
				Player p = level.getNearestPlayer(m, 24);
				m.setTarget(null);
				if (p != null) {
					Vec3 away = m.position().subtract(p.position()).normalize().scale(8);
					m.getNavigation().moveTo(m.getX() + away.x, m.getY(), m.getZ() + away.z, 1.3);
				}
			}
			return true;
		}
	}

	/** 時限の呪い: 切れた瞬間に大ダメージ(解呪の聖水・浄化で回避可)。 */
	static class Doom extends MobEffect {
		Doom() {
			super(MobEffectCategory.HARMFUL, 0x200010);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amp) {
			return true;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
			MobEffectInstance inst = mob.getEffect(DOOM);
			if (inst != null && inst.getDuration() <= 1) {
				float dmg;
				if (mob instanceof Player) {
					dmg = mob.getMaxHealth() * 0.8f;
				} else if (Enemies.tierOf(mob) == Enemies.Tier.BOSS || Enemies.tierOf(mob) == Enemies.Tier.MIDBOSS) {
					dmg = mob.getMaxHealth() * (0.12f + 0.03f * amp);
				} else {
					dmg = mob.getMaxHealth() * 3f;
				}
				level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.5, 1, 0.5, 0.05);
				Combat.hurtClean(mob, level, mob.damageSources().magic(), dmg);
			} else if (inst != null && inst.getDuration() % 20 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 3, 0.1, 0.1, 0.1, 0);
			}
			return true;
		}
	}

	/** ジェットウィング: 効果中は飛べる(プレイヤーのみ)。 */
	static class JetWing extends MobEffect {
		JetWing() {
			super(MobEffectCategory.BENEFICIAL, 0xC0FFE0);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amp) {
			return true;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
			if (mob instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()) {
				MobEffectInstance inst = p.getEffect(JET_WING);
				boolean ending = inst == null || inst.getDuration() <= 2;
				if (!ending && !p.getAbilities().mayfly) {
					p.getAbilities().mayfly = true;
					p.onUpdateAbilities();
				} else if (ending && p.getAbilities().mayfly) {
					p.getAbilities().mayfly = false;
					p.getAbilities().flying = false;
					p.onUpdateAbilities();
					p.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 100));
				}
			}
			return true;
		}
	}
}
