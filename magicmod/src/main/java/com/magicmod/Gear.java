package com.magicmod;

import java.util.EnumMap;
import java.util.Map;

/** 装備スロット拡張(設計書 第6章)の装備品。帽子・ペンダント・手袋・腕輪の 4 部位。 */
public final class Gear {

	public enum Slot {
		HAT("帽子"), PENDANT("ペンダント"), GLOVES("手袋"), BRACELET("腕輪");

		public final String ja;

		Slot(String ja) {
			this.ja = ja;
		}
	}

	public enum Stat {
		MP_MAX("MP最大値", false), SPELL_POWER("魔法威力", true), CAST_SPEED("詠唱時間短縮", true), ELEM_RESIST("属性耐性", true),
		STATUS_RESIST("状態異常耐性", true), HP_REGEN("HP自動回復(/秒)", false), MELEE_MAGIC("近接魔法付与威力", true),
		CT_REDUCE("クールタイム短縮", true), MP_REGEN("MP自然回復", true), MP_ON_HIT("打撃時MP回復", false),
		FAMILIAR_SLOT("契約スロット", false), WAND_CAP("杖の本装着数", false), TECH_POWER("物理わざ威力", true),
		STAMINA_MAX("スタミナ最大値", false), WIND("風属性威力", true), THUNDER("電気属性威力", true), FIRE("炎属性威力", true),
		WATER("水属性威力", true), GRAVITY("重力属性威力", true), DARK("闇属性威力", true), LIGHT("光属性威力", true),
		CURSE("呪い属性威力", true);

		public final String ja;
		public final boolean percent;

		Stat(String ja, boolean percent) {
			this.ja = ja;
			this.percent = percent;
		}

		public static Stat ofElement(Element e) {
			return valueOf(e.name());
		}
	}

	public enum Kind {
		APPRENTICE_HAT("apprentice_hat", "見習いの帽子", Slot.HAT, null, Stat.MP_MAX, 15),
		SAGE_HAT("sage_hat", "賢者の帽子", Slot.HAT, null, Stat.CAST_SPEED, 0.15f, Stat.MP_MAX, 10),
		WARDING_HAT("warding_hat", "耐魔の帽子", Slot.HAT, null, Stat.ELEM_RESIST, 0.15f),
		WIND_PENDANT("wind_pendant", "風のペンダント", Slot.PENDANT, null, Stat.WIND, 0.15f),
		THUNDER_PENDANT("thunder_pendant", "雷のペンダント", Slot.PENDANT, null, Stat.THUNDER, 0.15f),
		FIRE_PENDANT("fire_pendant", "炎のペンダント", Slot.PENDANT, null, Stat.FIRE, 0.15f),
		WATER_PENDANT("water_pendant", "水のペンダント", Slot.PENDANT, null, Stat.WATER, 0.15f),
		GRAVITY_PENDANT("gravity_pendant", "重力のペンダント", Slot.PENDANT, null, Stat.GRAVITY, 0.15f),
		DARK_PENDANT("dark_pendant", "闇のペンダント", Slot.PENDANT, null, Stat.DARK, 0.15f),
		LIGHT_PENDANT("light_pendant", "光のペンダント", Slot.PENDANT, null, Stat.LIGHT, 0.15f),
		CURSE_PENDANT("curse_pendant", "呪いのペンダント", Slot.PENDANT, null, Stat.CURSE, 0.15f),
		CLARITY_PENDANT("clarity_pendant", "明鏡のペンダント", Slot.PENDANT, null, Stat.STATUS_RESIST, 0.2f),
		LIFE_PENDANT("life_pendant", "生命のペンダント", Slot.PENDANT, null, Stat.HP_REGEN, 0.25f),
		MARTIAL_AMULET("martial_amulet", "武神の護符", Slot.PENDANT, Archetype.PHYSICAL, Stat.TECH_POWER, 0.15f, Stat.STAMINA_MAX, 20),
		ARCANE_AMULET("arcane_amulet", "魔導の護符", Slot.PENDANT, Archetype.MAGIC, Stat.SPELL_POWER, 0.15f, Stat.MP_MAX, 15),
		FIGHTER_GLOVES("fighter_gloves", "闘士の手袋", Slot.GLOVES, null, Stat.MELEE_MAGIC, 0.2f, Stat.MP_ON_HIT, 1),
		SWIFT_GLOVES("swift_gloves", "迅雷の手袋", Slot.GLOVES, null, Stat.CT_REDUCE, 0.1f),
		IRON_GAUNTLET("iron_gauntlet", "剛拳の手甲", Slot.GLOVES, Archetype.PHYSICAL, Stat.TECH_POWER, 0.1f, Stat.MP_ON_HIT, 1),
		CASTING_GLOVES("casting_gloves", "詠唱の手袋", Slot.GLOVES, Archetype.MAGIC, Stat.CAST_SPEED, 0.2f, Stat.CT_REDUCE, 0.05f),
		MANA_BRACELET("mana_bracelet", "魔力の腕輪", Slot.BRACELET, null, Stat.MP_ON_HIT, 2, Stat.MP_REGEN, 0.25f),
		CONTRACT_BRACELET("contract_bracelet", "契約の腕輪", Slot.BRACELET, null, Stat.FAMILIAR_SLOT, 1),
		EXPANSION_BRACELET("expansion_bracelet", "拡張の腕輪", Slot.BRACELET, null, Stat.WAND_CAP, 1);

		public final String id;
		public final String ja;
		public final Slot slot;
		/** この型しか付けられない(null なら誰でも)。均衡型は両方付けられる。 */
		public final Archetype only;
		public final Map<Stat, Float> stats = new EnumMap<>(Stat.class);

		Kind(String id, String ja, Slot slot, Archetype only, Object... kv) {
			this.id = id;
			this.ja = ja;
			this.slot = slot;
			this.only = only;
			for (int i = 0; i + 1 < kv.length; i += 2) {
				stats.put((Stat) kv[i], ((Number) kv[i + 1]).floatValue());
			}
		}

		public boolean canEquip(Archetype a) {
			if (only == null) {
				return true;
			}
			return a == only || a == Archetype.BALANCED;
		}
	}

	private Gear() {
	}

	public static Kind byId(String id) {
		for (Kind k : Kind.values()) {
			if (k.id.equals(id)) {
				return k;
			}
		}
		return null;
	}

	/** 装備 4 部位 + 装備エンチャント(魔力強化など)の合計。 */
	public static float stat(PlayerMagic pm, Stat s) {
		float v = 0;
		for (int i = 0; i < 4; i++) {
			String id = pm.accessories[i];
			if (id == null) {
				continue;
			}
			Kind k = byId(id.replace(MagicMod.MOD_ID + ":", ""));
			if (k == null || !k.canEquip(pm.archetype)) {
				continue;
			}
			v += k.stats.getOrDefault(s, 0f);
			if (pm.accessoryData[i] != null) {
				int power = pm.accessoryData[i].getIntOr("power", 0);
				if (s == Stat.SPELL_POWER) {
					v += 0.05f * power;
				}
				int cost = pm.accessoryData[i].getIntOr("cost", 0);
				if (s == Stat.MP_REGEN) {
					v += 0.05f * cost;
				}
			}
		}
		return v;
	}
}
