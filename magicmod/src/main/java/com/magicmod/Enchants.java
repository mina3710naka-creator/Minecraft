package com.magicmod;

import java.util.List;
import java.util.Map;

/** 杖エンチャント(設計書 3.3)と武器エンチャント(12.4、杖と対称)。 */
public final class Enchants {

	public static final List<String> WAND_IDS = List.of("power", "cost", "duration", "cast", "slot", "cooldown");
	public static final List<String> WEAPON_IDS = List.of("attack", "stamina", "bleed", "combo", "tslot", "tcooldown");

	private static final Map<String, String> NAMES = Map.ofEntries(
			Map.entry("power", "魔力強化"), Map.entry("cost", "消費魔力軽減"), Map.entry("duration", "効果時間延長"),
			Map.entry("cast", "詠唱短縮"), Map.entry("slot", "スロット拡張"), Map.entry("cooldown", "クールタイム短縮"),
			Map.entry("attack", "攻撃力強化"), Map.entry("stamina", "スタミナ消費軽減"), Map.entry("bleed", "出血蓄積上昇"),
			Map.entry("combo", "コンボ時間延長"), Map.entry("tslot", "スロットアップ"), Map.entry("tcooldown", "クールタイム短縮"));

	private static final Map<String, String> DESC = Map.ofEntries(
			Map.entry("power", "魔法ダメージ/効果量 +5%/Lv"), Map.entry("cost", "必要MP -5%/Lv"),
			Map.entry("duration", "状態異常・バフ継続時間 +10%/Lv"), Map.entry("cast", "詠唱時間 -10%/Lv"),
			Map.entry("slot", "杖の本装着上限 +1"), Map.entry("cooldown", "再使用時間 -10%/Lv"),
			Map.entry("attack", "物理わざ威力 +5%/Lv"), Map.entry("stamina", "スタミナ消費 -5%/Lv"),
			Map.entry("bleed", "出血の時間 +20%/Lv"), Map.entry("combo", "スタミナ回復 +10%/Lv"),
			Map.entry("tslot", "わざスロット +1"), Map.entry("tcooldown", "わざの再使用時間 -10%/Lv"));

	private Enchants() {
	}

	public static String name(String id) {
		return NAMES.getOrDefault(id, id);
	}

	public static String desc(String id) {
		return DESC.getOrDefault(id, "");
	}

	public static int max(String id) {
		return switch (id) {
			case "power", "cost", "attack", "stamina" -> 5;
			case "duration", "cast", "bleed", "combo" -> 3;
			case "cooldown", "tcooldown" -> 4;
			default -> 1;
		};
	}

	public static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(n);
		};
	}
}
