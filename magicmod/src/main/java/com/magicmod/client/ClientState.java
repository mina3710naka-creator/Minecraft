package com.magicmod.client;

import java.util.ArrayList;
import java.util.List;

import com.magicmod.Net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** サーバーから届いた HUD 用の状態。 */
public final class ClientState {

	public static float mp, maxMp = 100, st, maxSt = 100;
	public static String arch = "";
	public static int sp;
	public static String spell = "", spellEl = "", tech = "";
	public static long spellCd, techCd;
	public static int spellCt = 1, spellMp, techSt, count, index;
	public static float heat;
	public static int castT, castTotal;
	public static int combo, comboLeft, comboTier, justLeft;
	public static long dodgeReady;
	public static final List<String> familiars = new ArrayList<>();

	private ClientState() {
	}

	static void apply(CompoundTag t) {
		mp = t.getIntOr("mp", 0) / 10f;
		maxMp = t.getIntOr("maxMp", 100);
		st = t.getIntOr("st", 0) / 10f;
		maxSt = t.getIntOr("maxSt", 100);
		arch = t.getStringOr("arch", "");
		sp = t.getIntOr("sp", 0);
		spell = t.getStringOr("spell", "");
		spellEl = t.getStringOr("spellEl", "");
		spellCd = t.getLongOr("spellCd", 0);
		spellCt = Math.max(1, t.getIntOr("spellCt", 1));
		spellMp = t.getIntOr("spellMp", 0);
		count = t.getIntOr("count", 0);
		index = t.getIntOr("index", 0);
		heat = t.getFloatOr("heat", 0);
		castT = t.getIntOr("castT", 0);
		castTotal = t.getIntOr("castTotal", 0);
		combo = t.getIntOr("combo", 0);
		comboLeft = t.getIntOr("comboLeft", 0);
		comboTier = t.getIntOr("comboTier", 0);
		justLeft = t.getIntOr("justLeft", 0);
		dodgeReady = t.getLongOr("dodgeReady", 0);
		tech = t.getStringOr("tech", "");
		techCd = t.getLongOr("techCd", 0);
		techSt = t.getIntOr("techSt", 0);
		familiars.clear();
		for (Tag x : t.getListOrEmpty("fam")) {
			x.asString().ifPresent(familiars::add);
		}
		CompoundTag syn = t.getCompoundOrEmpty("syn");
		for (String k : syn.keySet()) {
			Net.CLIENT_SYNTH_NAMES.put(k, syn.getStringOr(k, ""));
		}
	}
}
