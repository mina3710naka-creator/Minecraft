package com.magicmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

/** 1 つの魔法(通常魔法も合成魔法も同じ形で扱う)。 */
public final class Spell {

	/** 魔法の形(弾・範囲・直線など)。ctx のヘルパーを使って当たり判定を作る。 */
	@FunctionalInterface
	public interface Form {
		void cast(Ctx c);
	}

	/** 命中した相手への追加効果(合成のサブ魔法として受け継がれる)。 */
	@FunctionalInterface
	public interface OnHit {
		void hit(Ctx c, LivingEntity target);
	}

	/** 状態異常 1 つ。 */
	@FunctionalInterface
	public interface St {
		void apply(Ctx c, LivingEntity target, float durMult);

		static St fx(Holder<MobEffect> e, float sec, int amp) {
			return (c, t, d) -> ModEffects.apply(t, e, Math.round(sec * 20 * d), amp + c.ampBonus());
		}

		static St stack(Holder<MobEffect> e, float sec, int max) {
			return (c, t, d) -> ModEffects.stack(t, e, Math.round(sec * 20 * d), max + c.ampBonus());
		}

		static St fire(float sec) {
			return (c, t, d) -> {
				t.igniteForTicks(Math.round(sec * 20 * d));
				ModEffects.apply(t, ModEffects.BURN, Math.round(sec * 20 * d), c.ampBonus());
			};
		}

		static St freeze(float sec, float chance) {
			return (c, t, d) -> {
				if (t.getRandom().nextFloat() < chance * c.chanceMult()) {
					t.setTicksFrozen(Math.max(t.getTicksFrozen(), t.getTicksRequiredToFreeze() + Math.round(sec * 20 * d)));
					ModEffects.apply(t, net.minecraft.world.effect.MobEffects.SLOWNESS, Math.round(sec * 20 * d), 2);
				}
			};
		}

		static St chance(float chance, St inner) {
			return (c, t, d) -> {
				if (t.getRandom().nextFloat() < chance * c.chanceMult()) {
					inner.apply(c, t, d);
				}
			};
		}
	}

	public final String id;
	public final String name;
	public final Element element;
	/** 合成でサブに入った属性(弱点判定に使う)。 */
	public final List<Element> extraElements = new ArrayList<>();
	public final String rank;
	public final String status;
	public final String special;
	public final String tag;
	public final int no;
	public float coef;
	public float mp;
	public float ct;
	public int castTicks;
	public final boolean damage;
	public Form form;
	public final List<St> statuses = new ArrayList<>();
	public final List<OnHit> onHits = new ArrayList<>();
	/** 杖のスロットをいくつ使うか(合成魔法は 2)。 */
	public int slotCost = 1;
	public boolean synthesized;
	/** 最大魔法どうしの融合魔法。 */
	public boolean fusion;
	/** 呪い合成ボーナス(状態異常の時間・確率倍率)。 */
	public float curseBonus = 1f;

	public Spell(String id, String name, Element element, int no, String rank, String tag, String status, String special,
			float coef, float mp, float ct, boolean damage) {
		this.id = id;
		this.name = name;
		this.element = element;
		this.no = no;
		this.rank = rank;
		this.tag = tag;
		this.status = status;
		this.special = special;
		this.coef = coef;
		this.mp = mp;
		this.ct = ct;
		this.damage = damage;
	}

	public String rankName() {
		Balance.RankRow r = Balance.get().spellRanks.get(rank);
		return r == null ? rank : r.name();
	}

	/** この魔法を本に装着できる本 Lv(設計書 2.3)。 */
	public int requiredBookLevel() {
		if (no >= 11) {
			return 3;
		}
		if (no >= 8) {
			return 4;
		}
		if (no >= 5) {
			return 3;
		}
		return 1;
	}

	public boolean isRapid() {
		return "rapid".equals(rank);
	}

	public boolean isMelee() {
		return "melee".equals(rank) || tag.contains("近接") || no == 2 && (element == Element.THUNDER || element == Element.FIRE
				|| element == Element.WATER || element == Element.DARK || element == Element.LIGHT);
	}
}
