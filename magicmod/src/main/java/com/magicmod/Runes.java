package com.magicmod;

/** 秘術/刻印(設計書 4.9)。セットするだけで常時効果。効果量は数値バランス表の値。 */
public enum Runes {
	MIGHT("might", 1, "物理"), SWIFT("swift", 2, "中立"), IRON_WALL("iron_wall", 3, "物理"), REGEN("regen", 4, "中立"),
	CLARITY("clarity", 5, "魔法"), FIGHTING_SPIRIT("fighting_spirit", 6, "中立"), GUARD("guard", 7, "中立"),
	HASTE("haste", 8, "魔法"), CRITICAL("critical", 9, "中立"), CONQUEROR("conqueror", 10, "特化");

	public final String id;
	public final int no;
	public final String kind;

	Runes(String id, int no, String kind) {
		this.id = id;
		this.no = no;
		this.kind = kind;
	}

	public Balance.RuneRow row() {
		for (Balance.RuneRow r : Balance.get().runes) {
			if (r.no() == no) {
				return r;
			}
		}
		return new Balance.RuneRow(no, id, "", 0.05f, 0.05f, 0.05f, "");
	}

	public String ja() {
		return row().name();
	}

	/** アーキタイプ補正込みの効果量(設計書 13.4)。 */
	public float value(Archetype a) {
		Balance.RuneRow r = row();
		return switch (a) {
			case PHYSICAL -> r.valuePhysical();
			case MAGIC -> r.valueMagic();
			default -> r.value();
		};
	}

	public static Runes byId(String id) {
		for (Runes r : values()) {
			if (r.id.equals(id)) {
				return r;
			}
		}
		return null;
	}

	/** 刻印の効果量(付けていなければ 0)。覇者の刻印は全ステータスに乗る。 */
	public static float get(PlayerMagic pm, Runes r) {
		float v = 0;
		if (pm.hasRune(r.id)) {
			v += r.value(pm.archetype);
		}
		if (r != CONQUEROR && r != FIGHTING_SPIRIT && pm.hasRune(CONQUEROR.id)) {
			v += CONQUEROR.value(pm.archetype) * 0.6f;
		}
		return v;
	}

	/** オーラ(刻印)スロット数: 基本 2、スキルツリーで最大 4。 */
	public static int slots(PlayerMagic pm) {
		return 2 + pm.skill("c_rune");
	}
}
