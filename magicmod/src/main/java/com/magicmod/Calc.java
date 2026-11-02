package com.magicmod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 数値の計算をここに一元化する(設計書 第11.5節・開発メモ「計算順序の一元管理」)。
 *
 * 魔法ダメージ = 属性基準値 × ランク係数 × アーキタイプ倍率 × (1 + 加算ボーナスの合計) × 相手側の倍率(弱点/耐性/属性相性)
 *   加算ボーナス = 杖[魔力強化] + 本Lv5 + 魔法の強化 + 装備 + スキルツリー + 刻印(覇者)
 * MP消費 = 表のMP × (1 - 杖[消費魔力軽減]) × (本Lv5: 0.9) × (1 + オーバーヒート) × (闘志の刻印: 1 - 値)
 * クールタイム = 表のCT × アーキタイプCT倍率 × (1 - 杖[CT短縮] - 装備 - スキル) × (1 + オーバーヒート/2)
 */
public final class Calc {

	private Calc() {
	}

	public static Balance.ArchRow arch(PlayerMagic pm) {
		return Balance.get().arch(pm.archetype);
	}

	/** ソフトキャップ: 150 を超えた分は半分、200 を超えた分は 1/4 しか伸びない(設計書 10.1)。 */
	public static float softCap(float v) {
		if (v <= 150) {
			return v;
		}
		if (v <= 200) {
			return 150 + (v - 150) * 0.5f;
		}
		return 175 + (v - 200) * 0.25f;
	}

	public static float maxMp(PlayerMagic pm) {
		float base = Balance.get().res("mp_max", 100);
		float v = base * arch(pm).mpMax() * (1 + 0.05f * pm.skill("c_mp")) + Gear.stat(pm, Gear.Stat.MP_MAX);
		v *= 1 + Runes.get(pm, Runes.CONQUEROR) * 0.5f;
		return softCap(v);
	}

	public static float maxStamina(PlayerMagic pm) {
		float base = Balance.get().res("stamina_max", 100);
		float v = base * arch(pm).staminaMax() + 10 * pm.skill("p_stamina") + Gear.stat(pm, Gear.Stat.STAMINA_MAX);
		return softCap(v);
	}

	public static float mpRegen(PlayerMagic pm) {
		return Balance.get().res("mp_regen", 1) * (1 + Gear.stat(pm, Gear.Stat.MP_REGEN));
	}

	public static float staminaRegen(PlayerMagic pm, ItemStack held) {
		return Balance.get().res("stamina_regen", 10)
				* (1 + 0.1f * pm.skill("p_combo") + 0.1f * weaponEnch(held, "combo") + Runes.get(pm, Runes.SWIFT));
	}

	public static float mpOnHit(PlayerMagic pm, boolean fused) {
		float v = Balance.get().res("mp_on_hit", 3) + Gear.stat(pm, Gear.Stat.MP_ON_HIT);
		return fused ? v * 2 : v;
	}

	// ---------------------------------------------------------------------------------------------
	// 魔法

	public static float spellPower(LivingEntity caster, Spell s, int bookLevel, ItemStack wand) {
		float base = s.element.base() * s.coef;
		if (!(caster instanceof Player p)) {
			return base * Enemies.powerMult(caster);
		}
		PlayerMagic pm = PlayerMagic.of(p);
		float bonus = 0;
		bonus += 0.05f * Books.wandEnch(wand, "power");
		if (bookLevel >= 5) {
			bonus += 0.10f;
		}
		bonus += 0.06f * pm.enhance.getOrDefault(s.id, 0);
		bonus += Gear.stat(pm, Gear.Stat.SPELL_POWER) + Gear.stat(pm, Gear.Stat.ofElement(s.element));
		bonus += 0.05f * pm.skill("e_" + s.element.id + "_power");
		bonus += Runes.get(pm, Runes.CONQUEROR) * 0.5f;
		if (s.isMelee()) {
			bonus += Gear.stat(pm, Gear.Stat.MELEE_MAGIC);
		}
		return base * arch(pm).spellPower() * (1 + bonus);
	}

	public static float spellMp(Player p, Spell s, int bookLevel, ItemStack wand) {
		PlayerMagic pm = PlayerMagic.of(p);
		float v = s.mp;
		v *= 1 - 0.05f * Books.wandEnch(wand, "cost");
		if (bookLevel >= 5) {
			v *= 0.9f;
		}
		v *= 1 + 0.1f * pm.overheat(s.element);
		if (p.level().getGameTime() < pm.fightingSpiritUntil) {
			v *= 1 - Runes.FIGHTING_SPIRIT.value(pm.archetype);
		}
		return Math.max(1, v);
	}

	public static float spellCt(Player p, Spell s, ItemStack wand) {
		PlayerMagic pm = PlayerMagic.of(p);
		float red = 0.1f * Books.wandEnch(wand, "cooldown") + Gear.stat(pm, Gear.Stat.CT_REDUCE) + 0.05f * pm.skill("c_ct");
		float v = s.ct * arch(pm).spellCt() * Math.max(0.3f, 1 - red);
		v *= 1 + 0.05f * pm.overheat(s.element);
		return v;
	}

	public static int castTicks(Player p, Spell s, ItemStack wand) {
		PlayerMagic pm = PlayerMagic.of(p);
		float red = 0.1f * Books.wandEnch(wand, "cast") + Gear.stat(pm, Gear.Stat.CAST_SPEED) + 0.1f * pm.skill("c_cast")
				+ Runes.get(pm, Runes.HASTE);
		return Math.round(s.castTicks * Math.max(0.2f, 1 - red));
	}

	public static float durationMult(Player p, Spell s, ItemStack wand) {
		PlayerMagic pm = PlayerMagic.of(p);
		return (1 + 0.1f * Books.wandEnch(wand, "duration") + 0.1f * pm.skill("e_" + s.element.id + "_sustain")) * s.curseBonus;
	}

	public static float chanceMult(Player p, Spell s) {
		PlayerMagic pm = PlayerMagic.of(p);
		return (1 + 0.1f * pm.skill("e_" + s.element.id + "_support")) * s.curseBonus;
	}

	// ---------------------------------------------------------------------------------------------
	// 物理わざ

	public static int weaponEnch(ItemStack s, String id) {
		return Books.data(s).getCompoundOrEmpty("mm_wench").getIntOr(id, 0);
	}

	public static float techPower(Player p, Techniques.Tech t, ItemStack weapon) {
		PlayerMagic pm = PlayerMagic.of(p);
		float base = Balance.get().categoryBase.getOrDefault(t.category, 8f) * t.coef;
		float bonus = 0.05f * weaponEnch(weapon, "attack") + Gear.stat(pm, Gear.Stat.TECH_POWER) + 0.05f * pm.skill("p_" + t.category)
				+ Runes.get(pm, Runes.MIGHT) + 0.04f * (pm.masteryLevel(t.category) - 1);
		return base * arch(pm).techPower() * (1 + bonus);
	}

	public static float techStamina(Player p, Techniques.Tech t, ItemStack weapon) {
		return Math.max(1, t.stamina * (1 - 0.05f * weaponEnch(weapon, "stamina")));
	}

	public static float techCt(Player p, Techniques.Tech t, ItemStack weapon) {
		PlayerMagic pm = PlayerMagic.of(p);
		return t.ct * arch(pm).techCt() * Math.max(0.3f, 1 - 0.1f * weaponEnch(weapon, "tcooldown") - Runes.get(pm, Runes.HASTE) * 0.5f);
	}
}
