package com.magicmod;

import java.util.ArrayList;
import java.util.List;

/** スキルツリー(設計書 第7章・12.4)。属性別ツリー×8、共通ツリー、物理ツリー。 */
public final class SkillTree {

	public record Node(String id, String group, String name, String desc, int max, int cost) {
	}

	private static final List<Node> NODES = new ArrayList<>();

	static {
		for (Element e : Element.values()) {
			String g = "el:" + e.id;
			NODES.add(new Node("e_" + e.id + "_power", g, e.ja + "・火力特化", e.ja + "魔法のダメージ +5%/Lv", 5, 1));
			NODES.add(new Node("e_" + e.id + "_support", g, e.ja + "・支援特化", e.ja + "魔法の状態異常の確率 +10%/Lv", 3, 1));
			NODES.add(new Node("e_" + e.id + "_sustain", g, e.ja + "・持続特化", e.ja + "魔法の状態異常の時間 +10%/Lv、その属性の敵のドロップ率 +5%/Lv", 3, 1));
		}
		NODES.add(new Node("c_mp", "common", "MP最大値強化", "MP最大値 +5%/Lv(ソフトキャップあり)", 5, 1));
		NODES.add(new Node("c_cast", "common", "詠唱速度強化", "詠唱時間 -10%/Lv", 3, 1));
		NODES.add(new Node("c_synth", "common", "合成の熟達", "魔法合成の素材 -10%/Lv(失敗は無いので代わりにコストが下がる)", 3, 1));
		NODES.add(new Node("c_ct", "common", "クールタイム短縮", "魔法のクールタイム -5%/Lv", 3, 1));
		NODES.add(new Node("c_exp", "common", "本のEXP獲得量上昇", "本のEXP +20%/Lv", 3, 1));
		NODES.add(new Node("c_rune", "common", "オーラスロット拡張", "刻印スロット +1/Lv(最大4)", 2, 2));
		NODES.add(new Node("c_familiar", "common", "契約スロット拡張", "使い魔の契約スロット +1", 1, 2));
		for (String c : Techniques.CATEGORIES) {
			NODES.add(new Node("p_" + c, "physical", Techniques.categoryName(c) + "特化", Techniques.categoryName(c) + "の物理わざ威力 +5%/Lv", 5, 1));
		}
		NODES.add(new Node("p_stamina", "physical", "スタミナ上限", "スタミナ最大値 +10/Lv", 5, 1));
		NODES.add(new Node("p_combo", "physical", "コンボ継続", "スタミナ回復 +10%/Lv", 3, 1));
		NODES.add(new Node("p_slot", "physical", "わざスロット拡張", "全カテゴリのわざスロット +1", 1, 2));
	}

	private SkillTree() {
	}

	public static List<Node> group(String g) {
		return NODES.stream().filter(n -> n.group().equals(g)).toList();
	}

	public static Node get(String id) {
		for (Node n : NODES) {
			if (n.id().equals(id)) {
				return n;
			}
		}
		return null;
	}

	public static String learn(PlayerMagic pm, String id) {
		Node n = get(id);
		if (n == null) {
			return "そのスキルはありません";
		}
		int lv = pm.skill(id);
		if (lv >= n.max()) {
			return "最大レベルです";
		}
		if (pm.skillPoints < n.cost()) {
			return "スキルポイントが足りません";
		}
		pm.skillPoints -= n.cost();
		pm.skills.put(id, lv + 1);
		return null;
	}
}
