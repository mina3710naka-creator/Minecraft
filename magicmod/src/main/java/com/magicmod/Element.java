package com.magicmod;

import net.minecraft.ChatFormatting;

/** 杖で使う実弾 8 属性(設計書 第1章)。 */
public enum Element {
	WIND("wind", "風", 0x9BE8B4, ChatFormatting.GREEN),
	THUNDER("thunder", "電気", 0xF5E050, ChatFormatting.YELLOW),
	FIRE("fire", "炎", 0xFF6A2A, ChatFormatting.RED),
	WATER("water", "水", 0x3E8BFF, ChatFormatting.BLUE),
	GRAVITY("gravity", "重力", 0x8A5CD6, ChatFormatting.DARK_PURPLE),
	DARK("dark", "闇", 0x3B2F4F, ChatFormatting.DARK_GRAY),
	LIGHT("light", "光", 0xFFF6C8, ChatFormatting.WHITE),
	CURSE("curse", "呪い", 0x7A1E5A, ChatFormatting.LIGHT_PURPLE);

	public final String id;
	public final String ja;
	public final int color;
	public final ChatFormatting fmt;

	Element(String id, String ja, int color, ChatFormatting fmt) {
		this.id = id;
		this.ja = ja;
		this.color = color;
		this.fmt = fmt;
	}

	public static Element byId(String id) {
		for (Element e : values()) {
			if (e.id.equals(id)) {
				return e;
			}
		}
		return null;
	}

	/** 基準値(balance.json の属性基準値)。 */
	public float base() {
		return Balance.get().elementBase(this);
	}
}
