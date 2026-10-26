package com.songmod;

/** 音符ブロックの楽器。土台のブロックで決まり、{@code baseMidi} が音符値 0(F#)の MIDI 番号。 */
public enum Inst {
	BASS("BASS", 30, "minecraft:oak_planks"),
	DIDGERIDOO("DIDGERIDOO", 30, "minecraft:pumpkin"),
	GUITAR("GUITAR", 42, "minecraft:white_wool"),
	HARP("HARP", 54, "minecraft:coarse_dirt"),
	BIT("BIT", 54, "minecraft:emerald_block"),
	BANJO("BANJO", 54, "minecraft:hay_block"),
	PLING("PLING", 54, "minecraft:glowstone"),
	IRON_XYLOPHONE("IRON_XYLOPHONE", 54, "minecraft:iron_block"),
	COW_BELL("COW_BELL", 66, "minecraft:soul_sand"),
	FLUTE("FLUTE", 78, "minecraft:clay"),
	BELL("BELL", 78, "minecraft:gold_block"),
	CHIME("CHIME", 78, "minecraft:packed_ice"),
	XYLOPHONE("XYLOPHONE", 78, "minecraft:bone_block");

	/** Minecraft の NoteBlockInstrument の定数名 */
	public final String mcName;
	public final int baseMidi;
	public final String block;

	Inst(String mcName, int baseMidi, String block) {
		this.mcName = mcName;
		this.baseMidi = baseMidi;
		this.block = block;
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
