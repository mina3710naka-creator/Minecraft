package com.magicmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/** プレイヤーごとの魔法・物理の成長データ(ワールドに保存され、死んでも残る)。 */
public final class PlayerMagic {

	public static final Codec<PlayerMagic> CODEC = CompoundTag.CODEC.xmap(PlayerMagic::load, PlayerMagic::save);

	public static final AttachmentType<PlayerMagic> TYPE = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "player_magic"),
			b -> b.persistent(CODEC).copyOnDeath().initializer(PlayerMagic::new));

	/** 合成魔法の定義(ベース + サブ1〜2 + 素材ランク)。 */
	public record Synth(String id, String base, List<String> subs, int tier, String name) {
	}

	// ---- 保存されるもの ----
	public Archetype archetype = Archetype.NONE;
	public float mp = 100;
	public float stamina = 100;
	public final Set<String> learned = new LinkedHashSet<>();
	public final Map<String, Integer> enhance = new HashMap<>();
	public final Map<String, Synth> synths = new LinkedHashMap<>();
	public final Set<String> recipes = new LinkedHashSet<>();
	public int synthCounter;
	public final Set<String> techLearned = new LinkedHashSet<>();
	public final Map<String, List<String>> techSlots = new HashMap<>();
	public final Map<String, Integer> techSel = new HashMap<>();
	public final Map<String, Integer> mastery = new HashMap<>();
	public final List<String> runes = new ArrayList<>();
	public final String[] accessories = new String[4];
	public final CompoundTag[] accessoryData = new CompoundTag[4];
	public final List<String> contracts = new ArrayList<>();
	public final Map<String, Integer> familiarExp = new HashMap<>();
	public int skillPoints;
	public final Map<String, Integer> skills = new HashMap<>();
	public final Set<String> defeated = new LinkedHashSet<>();
	public int levelMilestone;
	public boolean guideGiven;
	public boolean showNumbers = Settings.get().damageNumbersDefault;
	/** 使い魔を出したままにしているか(ログインし直し・次元移動・復活のあとに呼び直す)。 */
	public boolean familiarsOut;
	/** ギルドの受注中の依頼(種別|引数|必要数|進捗|報酬|名前)と、今日すでに受けた依頼の id。 */
	public final List<String> quests = new ArrayList<>();
	public final Set<String> questTaken = new LinkedHashSet<>();
	public int questsDone;
	/** 解放した変身(id)と、いま選んでいる姿(解放済みの中の位置)。 */
	public final Set<String> forms = new LinkedHashSet<>();
	public int formSel;
	/** 変身ごとの熟練度(スキル使用・変身時間で増える)。 */
	public final Map<String, Integer> formExp = new HashMap<>();
	/** 今日までに達成した依頼の id(1 日 4 件達成のボーナス用)と、生活魔法の経験値。 */
	public final Set<String> questFinished = new LinkedHashSet<>();
	public int lifeExp;
	/** 次に呼ぶ契約(使い魔は同時に 1 体だけ)。 */
	public int familiarPick;

	// ---- 保存しないもの(戦闘中の一時状態) ----
	public final Map<String, Long> cooldowns = new HashMap<>();
	public final Map<Element, Float> overheat = new HashMap<>();
	public final Map<Element, Long> lastCast = new HashMap<>();
	public String lastSpell = "";
	public long lastSpellTick;
	public final Map<Element, Float> pendingBookExp = new HashMap<>();
	public final List<UUID> activeFamiliars = new ArrayList<>();
	public final Map<String, Long> familiarCooldown = new HashMap<>();
	public long fightingSpiritUntil;
	public long lastCombat;
	public long runeSwapReady;
	public String castingSpell = "";
	public int castTicks;
	public int castTotal;
	public int castSlotIndex;
	public long counterUntil;
	public float counterDamage;
	public boolean doubleJumpUsed;
	// ---- 回避・コンボ(保存しない) ----
	public int combo;
	public long comboUntil;
	public int comboTier;
	public boolean lastHitPhysical;
	public long invulnUntil;
	public long dodgeReadyAt;
	public boolean dodgeRewarded;
	public long justDodgeUntil;
	public Element imbue;
	public long imbueUntil;
	public boolean dirty = true;
	public int syncHash;

	public static PlayerMagic of(Player p) {
		return p.getAttachedOrCreate(TYPE);
	}

	public float overheat(Element e) {
		return overheat.getOrDefault(e, 0f);
	}

	public int skill(String id) {
		return skills.getOrDefault(id, 0);
	}

	public int masteryLevel(String category) {
		int exp = mastery.getOrDefault(category, 0);
		int lv = 1;
		for (int t : MASTERY_STEPS) {
			if (exp >= t) {
				lv++;
			}
		}
		return Math.min(lv, 5);
	}

	public static final int[] MASTERY_STEPS = { 60, 200, 500, 1100 };

	public List<String> techLoadout(String category) {
		return techSlots.computeIfAbsent(category, k -> new ArrayList<>());
	}

	public boolean hasRune(String id) {
		return runes.contains(id);
	}

	public long cooldownEnd(String key) {
		return cooldowns.getOrDefault(key, 0L);
	}

	// ---------------------------------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putString("arch", archetype.id);
		t.putFloat("mp", mp);
		t.putFloat("st", stamina);
		t.put("learned", strings(learned));
		CompoundTag en = new CompoundTag();
		enhance.forEach(en::putInt);
		t.put("enhance", en);
		ListTag sy = new ListTag();
		for (Synth s : synths.values()) {
			CompoundTag c = new CompoundTag();
			c.putString("id", s.id());
			c.putString("base", s.base());
			c.put("subs", strings(s.subs()));
			c.putInt("tier", s.tier());
			c.putString("name", s.name());
			sy.add(c);
		}
		t.put("synths", sy);
		t.put("recipes", strings(recipes));
		t.putInt("synthCounter", synthCounter);
		t.put("techLearned", strings(techLearned));
		CompoundTag ts = new CompoundTag();
		techSlots.forEach((k, v) -> ts.put(k, strings(v)));
		t.put("techSlots", ts);
		CompoundTag tsel = new CompoundTag();
		techSel.forEach(tsel::putInt);
		t.put("techSel", tsel);
		CompoundTag ma = new CompoundTag();
		mastery.forEach(ma::putInt);
		t.put("mastery", ma);
		t.put("runes", strings(runes));
		ListTag acc = new ListTag();
		for (int i = 0; i < 4; i++) {
			CompoundTag c = new CompoundTag();
			if (accessories[i] != null) {
				c.putString("id", accessories[i]);
				if (accessoryData[i] != null) {
					c.put("data", accessoryData[i].copy());
				}
			}
			acc.add(c);
		}
		t.put("acc", acc);
		t.put("contracts", strings(contracts));
		CompoundTag fe = new CompoundTag();
		familiarExp.forEach(fe::putInt);
		t.put("famExp", fe);
		t.putInt("sp", skillPoints);
		CompoundTag sk = new CompoundTag();
		skills.forEach(sk::putInt);
		t.put("skills", sk);
		t.put("defeated", strings(defeated));
		t.putInt("lvMilestone", levelMilestone);
		t.putBoolean("guide", guideGiven);
		t.putBoolean("numbers", showNumbers);
		t.putBoolean("famOut", familiarsOut);
		t.put("quests", strings(quests));
		t.put("questTaken", strings(questTaken));
		t.putInt("questsDone", questsDone);
		t.put("questFinished", strings(questFinished));
		t.putInt("lifeExp", lifeExp);
		t.put("forms", strings(forms));
		t.putInt("formSel", formSel);
		CompoundTag fx = new CompoundTag();
		formExp.forEach(fx::putInt);
		t.put("formExp", fx);
		return t;
	}

	public static PlayerMagic load(CompoundTag t) {
		PlayerMagic m = new PlayerMagic();
		m.archetype = Archetype.byId(t.getStringOr("arch", "none"));
		m.mp = t.getFloatOr("mp", 100);
		m.stamina = t.getFloatOr("st", 100);
		readStrings(t, "learned", m.learned);
		CompoundTag en = t.getCompoundOrEmpty("enhance");
		for (String k : en.keySet()) {
			m.enhance.put(k, en.getIntOr(k, 0));
		}
		for (Tag x : t.getListOrEmpty("synths")) {
			if (x instanceof CompoundTag c) {
				List<String> subs = new ArrayList<>();
				readStrings(c, "subs", subs);
				Synth s = new Synth(c.getStringOr("id", ""), c.getStringOr("base", ""), subs, c.getIntOr("tier", 1),
						c.getStringOr("name", "合成魔法"));
				m.synths.put(s.id(), s);
			}
		}
		readStrings(t, "recipes", m.recipes);
		m.synthCounter = t.getIntOr("synthCounter", 0);
		readStrings(t, "techLearned", m.techLearned);
		CompoundTag ts = t.getCompoundOrEmpty("techSlots");
		for (String k : ts.keySet()) {
			List<String> l = new ArrayList<>();
			readStrings(ts, k, l);
			m.techSlots.put(k, l);
		}
		CompoundTag tsel = t.getCompoundOrEmpty("techSel");
		for (String k : tsel.keySet()) {
			m.techSel.put(k, tsel.getIntOr(k, 0));
		}
		CompoundTag ma = t.getCompoundOrEmpty("mastery");
		for (String k : ma.keySet()) {
			m.mastery.put(k, ma.getIntOr(k, 0));
		}
		readStrings(t, "runes", m.runes);
		ListTag acc = t.getListOrEmpty("acc");
		for (int i = 0; i < Math.min(4, acc.size()); i++) {
			if (acc.get(i) instanceof CompoundTag c && c.contains("id")) {
				m.accessories[i] = c.getStringOr("id", null);
				m.accessoryData[i] = c.getCompound("data").orElse(null);
			}
		}
		readStrings(t, "contracts", m.contracts);
		CompoundTag fe = t.getCompoundOrEmpty("famExp");
		for (String k : fe.keySet()) {
			m.familiarExp.put(k, fe.getIntOr(k, 0));
		}
		m.skillPoints = t.getIntOr("sp", 0);
		CompoundTag sk = t.getCompoundOrEmpty("skills");
		for (String k : sk.keySet()) {
			m.skills.put(k, sk.getIntOr(k, 0));
		}
		readStrings(t, "defeated", m.defeated);
		m.levelMilestone = t.getIntOr("lvMilestone", 0);
		m.guideGiven = t.getBooleanOr("guide", false);
		m.showNumbers = t.getBooleanOr("numbers", Settings.get().damageNumbersDefault);
		m.familiarsOut = t.getBooleanOr("famOut", false);
		readStrings(t, "quests", m.quests);
		readStrings(t, "questTaken", m.questTaken);
		m.questsDone = t.getIntOr("questsDone", 0);
		readStrings(t, "questFinished", m.questFinished);
		m.lifeExp = t.getIntOr("lifeExp", 0);
		readStrings(t, "forms", m.forms);
		m.formSel = t.getIntOr("formSel", 0);
		CompoundTag fxc = t.getCompoundOrEmpty("formExp");
		for (String k : fxc.keySet()) {
			m.formExp.put(k, fxc.getIntOr(k, 0));
		}
		return m;
	}

	private static ListTag strings(Iterable<String> it) {
		ListTag l = new ListTag();
		for (String s : it) {
			l.add(StringTag.valueOf(s));
		}
		return l;
	}

	private static void readStrings(CompoundTag t, String key, java.util.Collection<String> out) {
		for (Tag x : t.getListOrEmpty(key)) {
			x.asString().ifPresent(out::add);
		}
	}
}
