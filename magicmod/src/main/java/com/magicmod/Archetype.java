package com.magicmod;

/** 物理/魔法ビルド分岐(設計書 第13章)。NONE はまだ選んでいない状態(均衡型と同じ扱い)。 */
public enum Archetype {
	NONE("none", "未選択"),
	PHYSICAL("physical", "物理型"),
	MAGIC("magic", "魔法型"),
	BALANCED("balanced", "均衡型");

	public final String id;
	public final String ja;

	Archetype(String id, String ja) {
		this.id = id;
		this.ja = ja;
	}

	public static Archetype byId(String id) {
		for (Archetype a : values()) {
			if (a.id.equals(id)) {
				return a;
			}
		}
		return NONE;
	}
}
