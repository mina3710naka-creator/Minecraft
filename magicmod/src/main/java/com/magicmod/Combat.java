package com.magicmod;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** 戦闘のルール(敵味方の判定・属性相性・MP 回復・被ダメージ補正・死亡時)。 */
public final class Combat {

	/** いま当てている魔法/わざ(ダメージ処理の中で参照する)。 */
	public static final ThreadLocal<Ctx> CURRENT = new ThreadLocal<>();

	private static final Identifier SWIFT_ID = Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "rune_swift");
	private static final Identifier MIGHT_ID = Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "rune_might");
	private static final Identifier HASTE_ID = Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "rune_haste");

	private Combat() {
	}

	// ---------------------------------------------------------------------------------------------
	// 敵味方

	public static @Nullable LivingEntity owner(LivingEntity e) {
		LivingEntity f = Familiars.ownerOf(e);
		if (f != null) {
			return f;
		}
		if (e instanceof OwnableEntity o && o.getOwner() instanceof LivingEntity le) {
			return le;
		}
		return null;
	}

	public static boolean isEnemy(LivingEntity caster, LivingEntity t) {
		return isEnemy(caster, t, true);
	}

	/** direct = 直接狙った相手(false なら範囲の巻き込みで、敵対していないものは除く)。 */
	public static boolean isEnemy(LivingEntity caster, LivingEntity t, boolean direct) {
		if (t == caster || !t.isAlive() || t instanceof ArmorStand || t.isSpectator()) {
			return false;
		}
		LivingEntity side = Familiars.ownerOf(caster);
		if (side != null) {
			caster = side;
		}
		if (t == caster) {
			return false;
		}
		if (caster instanceof Player cp) {
			if (t instanceof Player tp) {
				return tp != cp && !tp.isCreative() && Settings.get().pvp && ((ServerLevel) cp.level()).isPvpAllowed();
			}
			LivingEntity o = owner(t);
			if (o instanceof Player) {
				return false;
			}
			// 守る相手(村人・行商人・プレイヤーが作ったゴーレム・雪だるま・アレイ)以外は、範囲攻撃でも狙った攻撃でも当たる。
			// (以前は「敵対していない動物は一度殴らないと範囲に入らない」ルールがあり、殴るまで攻撃が当たらない不具合になっていた)
			if (t instanceof AbstractVillager || t instanceof IronGolem g && g.isPlayerCreated()
					|| t instanceof net.minecraft.world.entity.animal.golem.SnowGolem
					|| t instanceof net.minecraft.world.entity.animal.allay.Allay && !Enemies.isModEnemy(t)) {
				return false;
			}
			return true;
		}
		// MOD の敵・その他のモンスター側: プレイヤーと、プレイヤーの使い魔が敵
		if (t instanceof Player tp) {
			return !tp.isCreative();
		}
		LivingEntity o = owner(t);
		if (o instanceof Player) {
			return true;
		}
		if (caster instanceof Mob m && m.getTarget() == t) {
			return true;
		}
		return false;
	}

	public static boolean isAlly(LivingEntity caster, LivingEntity t) {
		if (t == caster) {
			return true;
		}
		LivingEntity side = Familiars.ownerOf(caster);
		if (side != null) {
			caster = side;
		}
		if (caster instanceof Player) {
			if (t instanceof Player) {
				return true;
			}
			return owner(t) instanceof Player;
		}
		return Enemies.isModEnemy(t) && Enemies.isModEnemy(caster);
	}

	// ---------------------------------------------------------------------------------------------
	// 属性相性(設計書 11.1・14 章)

	public static float targetMult(Ctx c, LivingEntity t) {
		if (c.physical) {
			return 1f;
		}
		float best = 0;
		for (Element e : c.elements) {
			best = Math.max(best, elementMult(c, e, t));
		}
		return best <= 0 ? 1f : best;
	}

	private static float elementMult(Ctx c, Element e, LivingEntity t) {
		float m = 1f;
		Enemies.Def d = Enemies.defOf(t);
		Balance b = Balance.get();
		if (d != null) {
			Element dEl = d.element(), dWeak = d.weak();
			if (d.id().equals(Omnis.ID)) {
				Element a = Omnis.attunement(t);
				dEl = a;
				dWeak = a == null ? null : Omnis.weakOf(a);
			}
			if (dWeak == e) {
				m *= b.res("weak_mult", 1.5f);
			}
			if (dEl == e) {
				m *= b.res("resist_mult", 0.5f);
			}
		}
		boolean undead = t.is(EntityTypeTags.UNDEAD);
		switch (e) {
			case FIRE -> {
				if (t.isFullyFrozen() || t.getTicksFrozen() > 0) {
					m *= 1.5f;
				}
				if (t.isInWater() || d != null && d.element() == Element.WATER) {
					m *= 0.7f;
				}
				if (t.hasEffect(ModEffects.AQUA_SHIELD)) {
					m *= 0.4f;
				}
			}
			case WATER -> {
				if (t.isOnFire() || t.hasEffect(ModEffects.BURN)) {
					t.clearFire();
					t.removeEffect(ModEffects.BURN);
					m *= 1.5f;
				}
				if (t.hasEffect(ModEffects.HEAT_HAZE)) {
					m *= 0f;
				}
				if (d != null && d.element() == Element.THUNDER) {
					m *= 0.8f;
				}
			}
			case THUNDER -> {
				if (t.isInWaterOrRain() || t.hasEffect(ModEffects.WET)) {
					m *= 1.5f;
				}
				if (t.hasEffect(ModEffects.KNOCKDOWN) || t.hasEffect(ModEffects.BIND)) {
					m *= 0.8f;
				}
			}
			case WIND -> {
				if (t.hasEffect(net.minecraft.world.effect.MobEffects.POISON) || t.hasEffect(ModEffects.BLEED) || t.hasEffect(ModEffects.BURN)) {
					m *= 1.3f;
				}
				if (t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.5) {
					m *= 0.7f;
				}
			}
			case GRAVITY -> {
				if (!t.onGround() && (t.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION) || t.isNoGravity()
						|| t.getDeltaMovement().y > 0.05 || t.fallDistance > 1)) {
					m *= 1.5f;
					t.setDeltaMovement(t.getDeltaMovement().add(0, -0.8, 0));
					t.syncVelocity = true;
				}
				if (t.isInWater()) {
					m *= 0.7f;
				}
			}
			case DARK -> {
				if (d != null && d.element() == Element.LIGHT) {
					m *= 1.0f;
				}
				if (!undead && t.getMaxHealth() > 60) {
					m *= 1.2f;
				}
			}
			case LIGHT -> {
				if (undead) {
					m *= c.spell != null && c.spell.no == 10 ? 3f : 2f;
				}
				if (t.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) || d != null && d.element() == Element.DARK) {
					m *= 1.2f;
				}
				if (t.getDeltaMovement().horizontalDistanceSqr() > 0.15) {
					m *= 0.8f;
				}
			}
			case CURSE -> {
				boolean anyDebuff = t.getActiveEffects().stream()
						.anyMatch(i -> i.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL);
				if (!anyDebuff) {
					m *= 1.3f;
				}
				if (Enemies.tierOf(t) == Enemies.Tier.BOSS) {
					m *= 0.7f;
				}
			}
		}
		return m;
	}

	/** 状態異常の時間倍率(耐性)。ボスは長い足止めが効きにくい。 */
	public static float statusResist(LivingEntity t) {
		float r = 1f;
		if (t instanceof Player p) {
			PlayerMagic pm = PlayerMagic.of(p);
			r *= 1 - Math.min(0.8f, Gear.stat(pm, Gear.Stat.STATUS_RESIST) + Runes.get(pm, Runes.CLARITY));
		}
		Enemies.Tier tier = Enemies.tierOf(t);
		if (tier == Enemies.Tier.BOSS) {
			r *= 0.35f;
		} else if (tier == Enemies.Tier.MIDBOSS) {
			r *= 0.6f;
		}
		return r;
	}

	/** 継続ダメージは相手の HP 規模に合わせる(HP 100 超の MOD の敵でも効くように)。 */
	public static float dotScale(LivingEntity t) {
		if (t instanceof Player) {
			return 1f;
		}
		return Math.max(1f, Math.min(6f, t.getMaxHealth() / 20f));
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * 無敵時間を気にせずダメージを与え、その後に無敵時間を残さない。
	 * (26.3 では被弾後の無敵は damageCooldownTime。これが残ると、直後の攻撃が吸われて「当たらない」ように見える)
	 */
	public static boolean hurtClean(LivingEntity t, ServerLevel level, DamageSource src, float dmg) {
		t.setInvulnerableTime(0);
		t.damageCooldownTime = 0;
		boolean ok = t.hurtServer(level, src, dmg);
		if (!(t instanceof Player)) {
			t.damageCooldownTime = 0;
		}
		return ok;
	}

	public static void addMp(LivingEntity e, float amount) {
		if (e instanceof Player p && !p.hasEffect(ModEffects.MANA_SEAL)) {
			PlayerMagic pm = PlayerMagic.of(p);
			pm.mp = Math.min(Calc.maxMp(pm), pm.mp + amount);
			pm.dirty = true;
		}
	}

	static void afterSpellHit(Ctx c, LivingEntity t, float dmg) {
		if (c.caster instanceof Player p) {
			PlayerMagic pm = PlayerMagic.of(p);
			pm.lastCombat = c.level.getGameTime();
			if (c.depth == 0 && c.comboAdded < 3 && p instanceof ServerPlayer sp && dmg > 0) {
				c.comboAdded++;
				Combo.hit(sp, c.physical, 1);
			}
			if (c.seal && c.depth == 0) {
				addMp(p, Balance.get().res("mp_on_hit", 3) * Balance.get().res("seal_mp_ratio", 0.5f));
			}
			if (!c.physical && c.element() != null && dmg > 0) {
				pm.pendingBookExp.merge(c.element(), 0.2f * dmg, Float::sum);
			}
		}
	}

	static void blowAwayPlants(Ctx c, int r) {
		if (!c.level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING) && !(c.caster instanceof Player)) {
			return;
		}
		BlockPos center = c.caster.blockPosition();
		for (BlockPos p : Spells.around(center, r)) {
			BlockState s = c.level.getBlockState(p);
			if (Spells.isLightPlant(s)) {
				c.level.destroyBlock(p, c.level.getRandom().nextFloat() < 0.3f, c.caster);
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// 被ダメージ補正(LivingEntityMixin から呼ばれる)

	public static float modifyIncoming(LivingEntity t, DamageSource src, float amount) {
		if (amount <= 0) {
			return amount;
		}
		Entity attacker = src.getEntity();
		// ボスの HP スケール(表の HP が 1024 を超えるので、被ダメージで割って再現する)と防御係数
		Enemies.Def d = Enemies.defOf(t);
		if (d != null) {
			amount *= Enemies.damageScale(t, d);
		}
		if (t instanceof Player p) {
			PlayerMagic pm = PlayerMagic.of(p);
			if (src.is(DamageTypeTags.IS_FIRE) && p.hasEffect(ModEffects.AQUA_SHIELD)) {
				amount *= 0.4f;
			}
			float red = Runes.get(pm, Runes.IRON_WALL) + partyRune(p, Runes.GUARD);
			if (CURRENT.get() != null || src.is(Ctx.SPELL_DAMAGE)) {
				red += Gear.stat(pm, Gear.Stat.ELEM_RESIST);
			}
			amount *= Math.max(0.2f, 1 - red);
		}
		if (attacker instanceof Player ap && t != ap) {
			float crit = partyRune(ap, Runes.CRITICAL);
			if (crit > 0 && ap.getRandom().nextFloat() < crit) {
				amount *= 1.5f;
				((ServerLevel) t.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 10,
						0.3, 0.3, 0.3, 0.2);
			}
		}
		return amount;
	}

	/** パーティ範囲(8 ブロック以内の味方プレイヤー)の刻印の効果量のうち一番高いもの。 */
	static float partyRune(Player p, Runes r) {
		float best = Runes.get(PlayerMagic.of(p), r);
		for (Player o : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(8))) {
			best = Math.max(best, Runes.get(PlayerMagic.of(o), r));
		}
		return best;
	}

	// ---------------------------------------------------------------------------------------------

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Combat::allowDamage);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			// ディスクから読み込まれた古い演出物だけ消す(いま出したものは FRESH に入っている)
			if ((e.entityTags().contains(DamageNumbers.TAG) || e.entityTags().contains(FxDisplay.TAG)) && !FxDisplay.FRESH.remove(e.getUUID())) {
				e.discard();
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Combat::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(Combat::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(Combat::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				tickPlayer(p);
			}
		});
	}

	private static boolean allowDamage(LivingEntity t, DamageSource src, float amount) {
		if (t instanceof ServerPlayer dp && Dodge.tryAvoid(dp, src)) {
			return false;
		}
		Entity attacker = src.getEntity();
		ServerLevel level = (ServerLevel) t.level();
		// 命中率低下: 攻撃が外れることがある
		if (attacker instanceof LivingEntity la && la != t && la.hasEffect(ModEffects.INACCURACY) && CURRENT.get() == null) {
			MobEffectInstance i = la.getEffect(ModEffects.INACCURACY);
			if (la.getRandom().nextFloat() < 0.3f + 0.1f * i.getAmplifier()) {
				level.sendParticles(ParticleTypes.POOF, t.getX(), t.getY() + t.getBbHeight(), t.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
				return false;
			}
		}
		// ウィンドシールド: 飛来物を確率で無効化
		if (src.getDirectEntity() instanceof Projectile && t.hasEffect(ModEffects.WIND_SHIELD) && t.getRandom().nextFloat() < 0.6f) {
			level.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY() + 1, t.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
			return false;
		}
		// ヒートヘイズ: 水属性無効
		if (t.hasEffect(ModEffects.HEAT_HAZE) && CURRENT.get() != null && CURRENT.get().element() == Element.WATER) {
			return false;
		}
		// シャドウクローン: 攻撃を肩代わり
		MobEffectInstance clone = t.getEffect(ModEffects.CLONE);
		if (clone != null && attacker != null && attacker != t) {
			level.sendParticles(ParticleTypes.SQUID_INK, t.getX(), t.getY() + 1, t.getZ(), 15, 0.3, 0.6, 0.3, 0.05);
			t.removeEffect(ModEffects.CLONE);
			if (clone.getAmplifier() > 0) {
				t.addEffect(new MobEffectInstance(ModEffects.CLONE, clone.getDuration(), clone.getAmplifier() - 1));
			}
			return false;
		}
		// カウンタースタンス: 構え中に殴られたら反撃
		if (t.hasEffect(ModEffects.COUNTER) && attacker instanceof LivingEntity la && la != t && t instanceof ServerPlayer sp) {
			t.removeEffect(ModEffects.COUNTER);
			Techniques.counter(sp, la);
			return false;
		}
		return true;
	}

	private static void afterDamage(LivingEntity t, DamageSource src, float base, float taken, boolean blocked) {
		Entity attacker = src.getEntity();
		if (attacker instanceof ServerPlayer p && src.getDirectEntity() == p && CURRENT.get() == null && taken > 0
				&& (src.is(DamageTypes.PLAYER_ATTACK) || src.is(DamageTypes.MACE_SMASH)) && t != p) {
			onMelee(p, t, taken);
		}
		if (t instanceof ServerPlayer p) {
			if (taken > 0) {
				Combo.reset(p);
			}
			PlayerMagic.of(p).lastCombat = p.level().getGameTime();
			if (!PlayerMagic.of(p).castingSpell.isEmpty() && taken >= 4) {
				PlayerMagic.of(p).castTicks += 4;
			}
		}
		Enemies.onHurt(t, src, taken);
		showNumber(t, src, taken);
	}

	/** プレイヤー(と使い魔)が与えたダメージを数字で出す。MOD の敵は表の HP 基準の値で出す。 */
	private static void showNumber(LivingEntity t, DamageSource src, float taken) {
		Entity attacker = src.getEntity();
		if (attacker instanceof LivingEntity la && Familiars.ownerOf(la) != null) {
			attacker = Familiars.ownerOf(la);
		}
		if (!(attacker instanceof ServerPlayer p) || t == p || taken <= 0 || !PlayerMagic.of(p).showNumbers) {
			return;
		}
		Enemies.Def d = Enemies.defOf(t);
		float shown = d == null ? taken : taken / Math.max(0.01f, Enemies.damageScale(t, d));
		Ctx c = CURRENT.get();
		int rgb = 0xFFFFFF;
		boolean weak = false, resist = false;
		if (c != null && !c.physical && c.element() != null) {
			rgb = Vfx.palette(c.element())[c.element() == Element.DARK ? 2 : 0];
			weak = c.lastTargetMult >= 1.45f;
			resist = c.lastTargetMult <= 0.55f;
		} else if (c != null && c.physical) {
			rgb = 0xFFD27A;
		}
		DamageNumbers.damage(t, shown, rgb, weak, resist);
	}

	/** 近接攻撃が当たった: MP 回復(戦闘回復がメイン、設計書 10.1)・付与魔法・熟練度。 */
	static void onMelee(ServerPlayer p, LivingEntity t, float taken) {
		PlayerMagic pm = PlayerMagic.of(p);
		ItemStack held = p.getMainHandItem();
		boolean fused = Books.isFused(held);
		addMp(p, Calc.mpOnHit(pm, fused));
		Combo.hit(p, true, 1);
		long now = p.level().getGameTime();
		pm.lastCombat = now;
		if (pm.hasRune(Runes.FIGHTING_SPIRIT.id)) {
			pm.fightingSpiritUntil = now + 100;
		}
		// 魔法剣: 杖の近接付与系の魔法が通常攻撃に乗る(設計書 9.2)
		Spell touch = null;
		if (fused) {
			for (Books.Entry e : Books.entries(held)) {
				Spell s = Books.resolve(p, e.id());
				if (s != null && s.isMelee()) {
					touch = s;
					break;
				}
			}
		}
		if (touch == null && pm.imbue != null && now < pm.imbueUntil) {
			touch = Spells.get(pm.imbue, 2);
		}
		if (touch != null) {
			Ctx c = new Ctx(p, touch, 0);
			c.durMult = 0.7f;
			c.applyStatuses(t);
			((ServerLevel) p.level()).sendParticles(Fx.of(touch.element), t.getX(), t.getY() + t.getBbHeight() / 2, t.getZ(), 6, 0.3, 0.3,
					0.3, 0.05);
		}
		String cat = ModItems.category(held);
		if (cat != null) {
			Techniques.addMastery(p, cat, 1);
			if (Moves.supports(cat)) {
				Moves.onHit(p, t, taken, cat);
			}
		}
	}

	private static boolean allowDeath(LivingEntity t, DamageSource src, float amount) {
		if (t.hasEffect(ModEffects.REBIRTH)) {
			t.removeEffect(ModEffects.REBIRTH);
			revive(t);
			return false;
		}
		if (t instanceof ServerPlayer p && Familiars.consumePhoenix(p)) {
			revive(t);
			p.sendSystemMessage(net.minecraft.network.chat.Component.literal("フェニックスが身代わりになり、あなたを蘇らせた!(再契約が必要)")
					.withStyle(ChatFormatting.GOLD));
			return false;
		}
		return true;
	}

	private static void revive(LivingEntity t) {
		t.setHealth(t.getMaxHealth() * 0.5f);
		t.clearFire();
		Ctx.cleanse(t);
		ServerLevel level = (ServerLevel) t.level();
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, t.getX(), t.getY() + 1, t.getZ(), 60, 0.6, 1, 0.6, 0.4);
		level.playSound(null, t.blockPosition(), net.minecraft.sounds.SoundEvents.TOTEM_USE, t.getSoundSource(), 1f, 1f);
	}

	private static void afterDeath(LivingEntity t, DamageSource src) {
		Enemies.Def dd = Enemies.defOf(t);
		if (dd != null && dd.tier() != Enemies.Tier.MINION) {
			BossFx.death(t, (ServerLevel) t.level(), dd);
		}
		Entity killer = src.getEntity();
		LivingEntity o = killer instanceof LivingEntity le ? Familiars.ownerOf(le) : null;
		if (o != null) {
			Familiars.addExp(killer, 5 + (int) (t.getMaxHealth() / 10));
			killer = o;
		}
		if (killer instanceof ServerPlayer p) {
			Enemies.onKilled(p, t);
		}
		if (t instanceof ServerPlayer p) {
			PlayerMagic pm = PlayerMagic.of(p);
			pm.castingSpell = "";
			pm.overheat.clear();
			boolean out = pm.familiarsOut;
			Familiars.dismissAll(p);
			pm.familiarsOut = out;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// プレイヤーの毎 tick 処理

	private static void tickPlayer(ServerPlayer p) {
		PlayerMagic pm = PlayerMagic.of(p);
		long now = p.level().getGameTime();
		float maxMp = Calc.maxMp(pm), maxSt = Calc.maxStamina(pm);
		if (!p.hasEffect(ModEffects.MANA_SEAL)) {
			pm.mp = Math.min(maxMp, pm.mp + Calc.mpRegen(pm) / 20f);
		}
		pm.mp = Math.min(pm.mp, maxMp);
		pm.stamina = Math.min(maxSt, pm.stamina + Calc.staminaRegen(pm, p.getMainHandItem()) / 20f);
		for (Element e : Element.values()) {
			float o = pm.overheat(e);
			if (o > 0 && now - pm.lastCast.getOrDefault(e, 0L) > 30) {
				pm.overheat.put(e, Math.max(0, o - 0.05f));
			}
		}
		Casting.tickCast(p, pm);
		if (p.onGround()) {
			pm.doubleJumpUsed = false;
		}
		if (!pm.guideGiven) {
			pm.guideGiven = true;
			Status.give(p, new ItemStack(ModItems.MAGIC_GUIDE));
		}
		if (now % 20 == 0) {
			flushBookExp(p, pm);
			float regen = Gear.stat(pm, Gear.Stat.HP_REGEN) + Runes.get(pm, Runes.REGEN) * 2;
			if (regen > 0 && p.getHealth() < p.getMaxHealth() && !p.isDeadOrDying()) {
				p.heal(regen);
			}
			applyRuneAttributes(p, pm);
			int lv = p.experienceLevel / 10;
			if (lv > pm.levelMilestone) {
				pm.skillPoints += lv - pm.levelMilestone;
				pm.levelMilestone = lv;
				p.sendSystemMessage(net.minecraft.network.chat.Component.literal("スキルポイント +1(経験値レベル到達)").withStyle(ChatFormatting.AQUA));
			}
			if (pm.imbue != null && now > pm.imbueUntil) {
				pm.imbue = null;
			}
		}
		Familiars.tickOwner(p, pm);
		Forms.tick(p, pm);
		Net.sync(p, pm);
	}

	private static void applyRuneAttributes(ServerPlayer p, PlayerMagic pm) {
		set(p.getAttribute(Attributes.MOVEMENT_SPEED), SWIFT_ID, Runes.get(pm, Runes.SWIFT));
		set(p.getAttribute(Attributes.ATTACK_DAMAGE), MIGHT_ID, Runes.get(pm, Runes.MIGHT));
		set(p.getAttribute(Attributes.ATTACK_SPEED), HASTE_ID, partyRune(p, Runes.HASTE));
	}

	private static void set(@Nullable AttributeInstance a, Identifier id, float v) {
		if (a == null) {
			return;
		}
		if (v <= 0) {
			a.removeModifier(id);
		} else {
			a.addOrUpdateTransientModifier(new AttributeModifier(id, v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	private static void flushBookExp(ServerPlayer p, PlayerMagic pm) {
		if (pm.pendingBookExp.isEmpty()) {
			return;
		}
		float mult = 1 + 0.2f * pm.skill("c_exp");
		for (var en : List.copyOf(pm.pendingBookExp.entrySet())) {
			int amount = Math.round(en.getValue() * mult);
			if (amount <= 0) {
				continue;
			}
			ItemStack wand = Casting.heldCaster(p);
			int up = wand.isEmpty() ? 0 : Books.giveExp(wand, en.getKey(), amount);
			if (up > 0) {
				pm.skillPoints++;
				p.sendSystemMessage(net.minecraft.network.chat.Component.literal(en.getKey().ja + "の魔法書が Lv" + up + " になった!(スキルポイント +1)")
						.withStyle(ChatFormatting.GOLD));
				p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, p.getSoundSource(), 0.8f, 1.2f);
			}
		}
		pm.pendingBookExp.clear();
	}

	static AABB box(LivingEntity e, double r) {
		return e.getBoundingBox().inflate(r);
	}
}
