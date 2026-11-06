package com.songmod;

/**
 * 音符ブロックの楽器。土台のブロックで決まり、{@code baseMidi} が音符値 0(F#)の MIDI 番号。
 * {@code color} は、その楽器の枝の床に使う色のブロック(見分けやすくするため)。
 * 打楽器(バスドラム・スネア・ハイハット)は音の高さがなく、音符値は常に 0。
 */
public enum Inst {
	BASS("BASS", 30, "minecraft:oak_planks", "minecraft:blue_concrete"),
	DIDGERIDOO("DIDGERIDOO", 30, "minecraft:pumpkin", "minecraft:brown_concrete"),
	GUITAR("GUITAR", 42, "minecraft:white_wool", "minecraft:orange_concrete"),
	HARP("HARP", 54, "minecraft:coarse_dirt", "minecraft:lime_concrete"),
	BIT("BIT", 54, "minecraft:emerald_block", "minecraft:cyan_concrete"),
	BANJO("BANJO", 54, "minecraft:hay_block", "minecraft:yellow_concrete"),
	PLING("PLING", 54, "minecraft:glowstone", "minecraft:pink_concrete"),
	IRON_XYLOPHONE("IRON_XYLOPHONE", 54, "minecraft:iron_block", "minecraft:light_gray_concrete"),
	COW_BELL("COW_BELL", 66, "minecraft:soul_sand", "minecraft:gray_concrete"),
	FLUTE("FLUTE", 78, "minecraft:clay", "minecraft:light_blue_concrete"),
	BELL("BELL", 78, "minecraft:gold_block", "minecraft:yellow_concrete"),
	CHIME("CHIME", 78, "minecraft:packed_ice", "minecraft:white_concrete"),
	XYLOPHONE("XYLOPHONE", 78, "minecraft:bone_block", "minecraft:white_concrete"),
	// 打楽器: 石 = バスドラム、砂 = スネア、ガラス = ハイハット
	BASEDRUM("BASEDRUM", 0, "minecraft:stone", "minecraft:red_concrete"),
	SNARE("SNARE", 0, "minecraft:sand", "minecraft:magenta_concrete"),
	HAT("HAT", 0, "minecraft:glass", "minecraft:purple_concrete");

	/** Minecraft の NoteBlockInstrument の定数名 */
	public final String mcName;
	public final int baseMidi;
	public final String block;
	public final String color;
	/** その土台のブロックが、リピーターに駆動されて隣の音符ブロックを鳴らせる(レッドストーンを通す)か。ガラス・氷・グロウストーン・重力ブロックは、台にしない */
	public final boolean conductor;
	/** 土台が砂など、下に支えがないと落ちるか */
	public final boolean falls;

	Inst(String mcName, int baseMidi, String block, String color) {
		this.mcName = mcName;
		this.baseMidi = baseMidi;
		this.block = block;
		this.color = color;
		this.falls = block.equals("minecraft:sand");
		this.conductor = !block.equals("minecraft:glowstone") && !block.equals("minecraft:packed_ice")
				&& !block.equals("minecraft:glass") && !falls;
	}

	/** 音の高さがなく、音符値が使われない楽器(打楽器) */
	public boolean percussion() {
		return this == BASEDRUM || this == SNARE || this == HAT;
	}

	/** 音の高さに合う楽器: 低音はベース、中音はハープ、高音はベル */
	public static Inst auto(int midi) {
		if (midi < 54) {
			return BASS;
		}
		return midi <= 78 ? HARP : BELL;
	}

	public static Inst byName(String s) {
		for (Inst i : values()) {
			if (i.name().equalsIgnoreCase(s)) {
				return i;
			}
		}
		return null;
	}
}
