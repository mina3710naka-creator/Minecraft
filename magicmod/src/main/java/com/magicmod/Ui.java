package com.magicmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * テーブル・祭壇・ステータス画面。画面の中身はサーバーで組み立てて送り、クライアントはそれを表示するだけ
 * (ボタンを押すと action 文字列が返ってくる)。
 */
public final class Ui {

	static final class State {
		String screen = "";
		String tab = "";
		String sub = "";
		BlockPos pos;
		String synthBase = "";
		final List<String> synthSubs = new ArrayList<>();
		int synthTier = 1;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private Ui() {
	}

	/** 画面の中身。 */
	static final class Model {
		final CompoundTag t = new CompoundTag();
		final ListTag info = new ListTag(), tabs = new ListTag(), subs = new ListTag(), rows = new ListTag();

		Model(String screen, String title) {
			t.putString("screen", screen);
			t.putString("title", title);
			t.put("info", info);
			t.put("tabs", tabs);
			t.put("subs", subs);
			t.put("rows", rows);
		}

		Model info(String s) {
			info.add(StringTag.valueOf(s));
			return this;
		}

		void tab(String id, String label, boolean on) {
			CompoundTag c = new CompoundTag();
			c.putString("id", id);
			c.putString("label", label);
			c.putBoolean("on", on);
			tabs.add(c);
		}

		void sub(String id, String label, boolean on) {
			CompoundTag c = new CompoundTag();
			c.putString("id", id);
			c.putString("label", label);
			c.putBoolean("on", on);
			subs.add(c);
		}

		CompoundTag row(String text, List<String> tip, String btn, String act, boolean enabled) {
			CompoundTag c = new CompoundTag();
			c.putString("text", text);
			ListTag tl = new ListTag();
			tip.forEach(s -> tl.add(StringTag.valueOf(s)));
			c.put("tip", tl);
			c.putString("btn", btn);
			c.putString("act", act);
			c.putBoolean("en", enabled);
			rows.add(c);
			return c;
		}

		void row2(CompoundTag row, String btn, String act, boolean enabled) {
			row.putString("btn2", btn);
			row.putString("act2", act);
			row.putBoolean("en2", enabled);
		}

		void text(String s) {
			row(s, List.of(), "", "", false);
		}
	}

	public static void open(ServerPlayer p, String screen, @Nullable BlockPos pos) {
		State st = new State();
		st.screen = screen;
		st.pos = pos;
		State old = STATES.get(p.getUUID());
		if (old != null && old.screen.equals(screen) && screen.equals("magic_table")) {
			st.synthBase = old.synthBase;
			st.synthSubs.addAll(old.synthSubs);
			st.synthTier = old.synthTier;
		}
		STATES.put(p.getUUID(), st);
		send(p, st);
	}

	/** 自己テスト用: すべての画面・タブを組み立ててみる(送らない)。 */
	static int buildAll(ServerPlayer p, BlockPos pos) {
		int n = 0;
		String[][] screens = { { "magic_table", "learn", "equip", "enhance", "offer", "wand", "ench", "synth", "recipes" },
				{ "forge_table", "learn", "equip", "ench", "fuse" }, { "summoning_altar", "" }, { "arcane_altar", "" },
				{ "archetype_altar", "" }, { "seal_altar", "" }, { "status", "overview", "runes", "gear", "familiars", "skills" },
				{ "guide", "howto", "affinity", "enemies", "numbers" } };
		for (String[] sc : screens) {
			for (int i = 1; i < sc.length; i++) {
				State st = new State();
				st.screen = sc[0];
				st.tab = sc[i];
				st.pos = pos;
				st.synthBase = "fire_1";
				st.synthSubs.add("thunder_3");
				build(p, st);
				n++;
			}
		}
		return n;
	}

	private static Model build(ServerPlayer p, State st) {
		return switch (st.screen) {
			case "magic_table" -> magicTable(p, st);
			case "forge_table" -> forgeTable(p, st);
			case "summoning_altar" -> summoningAltar(p, st);
			case "arcane_altar" -> arcaneAltar(p, st);
			case "archetype_altar" -> archetypeAltar(p, st);
			case "seal_altar" -> sealAltar(p, st);
			case "guide" -> guide(p, st);
			default -> status(p, st);
		};
	}

	private static void send(ServerPlayer p, State st) {
		Model m = switch (st.screen) {
			case "magic_table" -> magicTable(p, st);
			case "forge_table" -> forgeTable(p, st);
			case "summoning_altar" -> summoningAltar(p, st);
			case "arcane_altar" -> arcaneAltar(p, st);
			case "archetype_altar" -> archetypeAltar(p, st);
			case "seal_altar" -> sealAltar(p, st);
			case "guide" -> guide(p, st);
			default -> status(p, st);
		};
		Net.sendUi(p, m.t);
	}

	public static void action(ServerPlayer p, String screen, String act) {
		State st = STATES.get(p.getUUID());
		if (st == null || !st.screen.equals(screen)) {
			return;
		}
		if (st.pos != null && p.distanceToSqr(Vec3.atCenterOf(st.pos)) > 64) {
			return;
		}
		String err = null;
		if (act.startsWith("tab:")) {
			st.tab = act.substring(4);
			st.sub = "";
		} else if (act.startsWith("sub:")) {
			st.sub = act.substring(4);
		} else {
			try {
				err = switch (st.screen) {
					case "magic_table" -> magicTableAct(p, st, act);
					case "forge_table" -> forgeAct(p, st, act);
					case "summoning_altar", "arcane_altar" -> altarAct(p, st, act);
					case "archetype_altar" -> archetypeAct(p, act);
					case "seal_altar" -> sealAct(p, st, act);
					case "status" -> statusAct(p, st, act);
					default -> null;
				};
			} catch (RuntimeException e) {
				MagicMod.LOGGER.warn("[magicmod] ui action failed: {}", act, e);
				err = "エラー: " + e.getMessage();
			}
		}
		if (err != null) {
			Casting.msg(p, err, ChatFormatting.RED);
		}
		PlayerMagic.of(p).dirty = true;
		send(p, st);
	}

	private static String sel(String cur, String def) {
		return cur == null || cur.isEmpty() ? def : cur;
	}

	private static String circled(int n) {
		return String.valueOf("①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬".charAt(Math.max(0, Math.min(12, n - 1))));
	}

	private static List<String> spellTip(Spell s) {
		List<String> l = new ArrayList<>();
		l.add("§f" + s.name + " §7(" + s.element.ja + "・" + s.rankName() + (s.tag.isEmpty() ? "" : "・" + s.tag) + ")");
		l.add("§7状態異常: §f" + s.status);
		l.add("§7特殊効果: §f" + s.special);
		l.add("§7MP " + Math.round(s.mp) + " / CT " + s.ct + "秒" + (s.damage ? " / ダメージ " + String.format("%.1f", s.element.base() * s.coef) : " / 補助"));
		l.add("§7装着に必要な本Lv: " + s.requiredBookLevel());
		return l;
	}

	// =============================================================================================
	// 魔法テーブル

	private static Cost learnCost(Spell s) {
		Element e = s.element;
		return switch (s.rank) {
			case "basic" -> Cost.of().add(ModItems.crystal(e), 2).add(Items.LAPIS_LAZULI, 1);
			case "middle" -> Cost.of().add(ModItems.crystal(e), 4).add(ModItems.minionDrop(e), 2).add(Items.LAPIS_LAZULI, 2);
			case "upper" -> Cost.of().add(ModItems.crystal(e), 8).add(ModItems.midbossDrop(e), 1).add(ModItems.essence(e), 1)
					.add(Items.DIAMOND, 1);
			case "max" -> Cost.of().add(ModItems.core(e), 1).add(Enemies.catalysts(e)).add(ModItems.essence(e), 2);
			default -> Cost.of().add(ModItems.crystal(e), 3).add(ModItems.minionDrop(e), 1).add(Items.LAPIS_LAZULI, 1);
		};
	}

	private static Cost enhanceCost(Spell s, int lv) {
		Cost c = Cost.of().add(ModItems.crystal(s.element), 2 + lv).add(Items.LAPIS_LAZULI, 1 + lv);
		if (lv >= 2) {
			c.add(ModItems.essence(s.element), 1);
		}
		return c;
	}

	private static int bestBookLevel(ServerPlayer p, Element e) {
		int best = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.getItem() instanceof BookItem bi && bi.element == e) {
				best = Math.max(best, Books.level(Books.bookOf(s)));
			} else if (Books.isCaster(s)) {
				for (CompoundTag b : Books.wandBooks(s)) {
					if (Books.element(b) == e) {
						best = Math.max(best, Books.level(b));
					}
				}
			}
		}
		return best;
	}

	private static Element heldElement(ServerPlayer p) {
		ItemStack s = p.getMainHandItem();
		if (s.getItem() instanceof BookItem bi) {
			return bi.element;
		}
		if (Books.isCaster(s)) {
			Element e = Books.firstElement(s);
			if (e != null) {
				return e;
			}
		}
		return Element.FIRE;
	}

	private static void elementSubs(Model m, State st, Element def) {
		String cur = sel(st.sub, def.id);
		for (Element e : Element.values()) {
			m.sub(e.id, e.ja, cur.equals(e.id));
		}
	}

	private static Model magicTable(ServerPlayer p, State st) {
		PlayerMagic pm = PlayerMagic.of(p);
		Model m = new Model("magic_table", "魔法テーブル");
		String tab = sel(st.tab, "learn");
		String[][] tabs = { { "learn", "獲得" }, { "equip", "装着" }, { "enhance", "強化" }, { "offer", "本に捧げる" }, { "wand", "杖の本" },
				{ "ench", "エンチャント" }, { "synth", "合成炉" }, { "recipes", "図鑑" } };
		for (String[] t : tabs) {
			m.tab(t[0], t[1], tab.equals(t[0]));
		}
		ItemStack held = p.getMainHandItem();
		switch (tab) {
			case "learn" -> {
				elementSubs(m, st, heldElement(p));
				Element e = Element.byId(sel(st.sub, heldElement(p).id));
				m.info("素材を使って魔法を習得します。習得した魔法は「装着」タブで本のスロットに入れて使います。");
				m.info("§7" + e.ja + "属性 / 基準値 " + Math.round(e.base()) + "  所持している" + e.ja + "の本の最高Lv: " + bestBookLevel(p, e));
				for (Spell s : Spells.of(e)) {
					boolean known = pm.learned.contains(s.id);
					Cost c = learnCost(s);
					List<String> tip = spellTip(s);
					tip.add("§6必要素材:");
					tip.addAll(c.lines(p));
					boolean ok = !known && c.has(p);
					String why = "";
					if (s.no == 10 && bestBookLevel(p, e) < 4) {
						ok = false;
						why = " §c(" + e.ja + "の本Lv4以上が必要)";
						tip.add("§c最大魔法は、Lv4以上の" + e.ja + "の本を持っていないと合成できません");
					}
					m.row(circled(s.no) + " " + s.name + " §7[" + s.rankName() + "]" + (known ? " §a習得済" : why), tip,
							known ? "習得済" : "習得", "learn:" + s.id, ok);
				}
			}
			case "equip" -> {
				if (!(held.getItem() instanceof BookItem bi)) {
					m.info("§e魔法の本をメインハンドに持って開いてください。");
					break;
				}
				CompoundTag book = Books.bookOf(held);
				int lv = Books.level(book);
				m.info(bi.element.ja + "の魔法書  Lv" + lv + "  スロット " + Books.slotCount(lv) + "(合成魔法は2枠使う)");
				List<String> slots = Books.slots(book);
				for (int i = 0; i < slots.size(); i++) {
					String id = slots.get(i);
					if (id.equals("|")) {
						m.text("   §8[" + (i + 1) + "] (合成魔法の2枠目)");
						continue;
					}
					if (id.isEmpty()) {
						m.text("   §8[" + (i + 1) + "] (空き)");
					} else {
						m.row("[" + (i + 1) + "] " + Net.spellName(id).replace("◆", "§d◆"), List.of(), "外す", "unequip:" + i, true);
					}
				}
				m.text("§6── 装着できる魔法 ──");
				for (Spell s : Spells.of(bi.element)) {
					if (!pm.learned.contains(s.id)) {
						continue;
					}
					boolean in = slots.contains(s.id);
					boolean lvOk = lv >= s.requiredBookLevel();
					m.row(circled(s.no) + " " + s.name + (lvOk ? "" : " §c(本Lv" + s.requiredBookLevel() + "が必要)"), spellTip(s),
							in ? "装着中" : "装着", "equip:" + s.id, !in && lvOk && slots.contains(""));
				}
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
					ItemStack s = p.getInventory().getItem(i);
					if (s.getItem() instanceof SynthScrollItem) {
						CompoundTag d = Books.data(s);
						Spell base = Spells.get(d.getStringOr("base", ""));
						if (base != null && base.element == bi.element) {
							m.row("§d◆" + d.getStringOr("name", "合成魔法") + " §7(合成魔法の巻物)", SynthScrollItem.lines(d), "装着", "equipsyn:" + i,
									hasTwoFree(slots));
						}
					}
				}
			}
			case "enhance" -> {
				elementSubs(m, st, heldElement(p));
				Element e = Element.byId(sel(st.sub, heldElement(p).id));
				m.info("同じ魔法を素材で上書きして、効果量を +6%/Lv 上げます(最大 Lv5)。");
				for (Spell s : Spells.of(e)) {
					if (!pm.learned.contains(s.id)) {
						continue;
					}
					int lv = pm.enhance.getOrDefault(s.id, 0);
					Cost c = enhanceCost(s, lv);
					List<String> tip = spellTip(s);
					tip.add("§6必要素材:");
					tip.addAll(c.lines(p));
					m.row(circled(s.no) + " " + s.name + " §b+" + lv, tip, lv >= 5 ? "最大" : "強化", "enhance:" + s.id, lv < 5 && c.has(p));
				}
			}
			case "offer" -> {
				if (!(held.getItem() instanceof BookItem bi)) {
					m.info("§e魔法の本をメインハンドに持って開いてください。");
					break;
				}
				CompoundTag book = Books.bookOf(held);
				int lv = Books.level(book);
				m.info(bi.element.ja + "の魔法書  Lv" + lv + "  EXP " + book.getIntOr("exp", 0) + (lv >= 5 ? " (MAX)" : " / " + Books.EXP_TO_LEVEL[lv]));
				for (Item it : offerItems(bi.element)) {
					int have = Cost.count(p, it);
					int exp = offerExp(it, bi.element);
					var r = m.row(it.getDefaultInstance().getHoverName().getString() + " §7×" + have + "  §b(+" + exp + " EXP/個)", List.of(), "1個", "offer:" + id(it) + ":1",
							have > 0 && lv < 5);
					m.row2(r, "全部", "offer:" + id(it) + ":all", have > 0 && lv < 5);
				}
			}
			case "wand" -> {
				if (!Books.isCaster(held)) {
					m.info("§e杖(または魔法剣)をメインハンドに持って開いてください。");
					break;
				}
				int cap = wandCap(p, held);
				List<CompoundTag> books = Books.wandBooks(held);
				m.info("杖の本 " + books.size() + " / " + cap + " 冊。1番目の本の属性が「エレメンタル」「魔封じの型」の属性になります。");
				for (int i = 0; i < books.size(); i++) {
					CompoundTag b = books.get(i);
					List<String> tip = new ArrayList<>();
					BookItem.describe(b, c -> tip.add(c.getString()));
					var r = m.row((i + 1) + ". " + Books.element(b).ja + "の魔法書 Lv" + Books.level(b), tip, "取り出す", "wout:" + i,
							!Books.isFused(held));
					m.row2(r, "先頭へ", "wtop:" + i, i > 0);
				}
				m.text("§6── 持っている本 ──");
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
					ItemStack s = p.getInventory().getItem(i);
					if (s.getItem() instanceof BookItem bi) {
						CompoundTag b = Books.bookOf(s);
						List<String> tip = new ArrayList<>();
						BookItem.describe(b, c -> tip.add(c.getString()));
						m.row(bi.element.ja + "の魔法書 Lv" + Books.level(b), tip, "セット", "win:" + i, books.size() < cap);
					}
				}
			}
			case "ench" -> enchTab(p, m, held);
			case "synth" -> synthTab(p, st, m);
			case "recipes" -> {
				m.info("代表的な組み合わせ(固定レシピ)は最初から載っています。自分で見つけた組み合わせも「既知レシピ」として残ります。");
				for (var en : Spells.FIXED_RECIPES.entrySet()) {
					recipeRow(p, m, en.getKey(), en.getValue(), "固定レシピ");
				}
				for (String key : pm.recipes) {
					if (!Spells.FIXED_RECIPES.containsKey(key)) {
						recipeRow(p, m, key, null, "既知レシピ");
					}
				}
			}
			default -> {
			}
		}
		return m;
	}

	private static void recipeRow(ServerPlayer p, Model m, String key, @Nullable String name, String kind) {
		String[] parts = key.split("\\+");
		Spell base = Spells.get(parts[0]);
		if (base == null) {
			return;
		}
		List<Spell> subs = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i < parts.length; i++) {
			Spell s = Spells.get(parts[i]);
			if (s != null) {
				subs.add(s);
				sb.append(" + ").append(s.name);
			}
		}
		String n = name != null ? name : Spells.autoName(base, subs);
		m.row("§d" + n + " §7= " + base.name + sb + " §8[" + kind + "]", List.of(), "炉にセット", "sprep:" + key, true);
	}

	private static boolean hasTwoFree(List<String> slots) {
		for (int i = 0; i + 1 < slots.size(); i++) {
			if (slots.get(i).isEmpty() && slots.get(i + 1).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static int wandCap(ServerPlayer p, ItemStack wand) {
		return Books.wandCapacity(wand) + Math.round(Gear.stat(PlayerMagic.of(p), Gear.Stat.WAND_CAP));
	}

	private static String id(Item it) {
		return BuiltInRegistries.ITEM.getKey(it).toString();
	}

	private static List<Item> offerItems(Element e) {
		return List.of(ModItems.crystal(e), ModItems.minionDrop(e), ModItems.essence(e), ModItems.midbossDrop(e), ModItems.core(e),
				ModItems.CRYSTAL_SHARD);
	}

	private static int offerExp(Item it, Element e) {
		if (it == ModItems.crystal(e)) {
			return 25;
		}
		if (it == ModItems.minionDrop(e)) {
			return 40;
		}
		if (it == ModItems.essence(e)) {
			return 120;
		}
		if (it == ModItems.midbossDrop(e)) {
			return 300;
		}
		if (it == ModItems.core(e)) {
			return 1000;
		}
		return 5;
	}

	private static Cost wandEnchCost(int lv) {
		return Cost.of().add(Items.LAPIS_LAZULI, 2 * lv + 2).add(ModItems.CRYSTAL_SHARD, 2 * lv + 2).xp((lv + 1) * 2);
	}

	private static void enchTab(ServerPlayer p, Model m, ItemStack held) {
		if (Books.isCaster(held)) {
			m.info("杖のエンチャント " + Books.enchCount(held) + " / " + Books.enchCapacity(held) + " 枠(杖のランクで増える)");
			for (String e : Enchants.WAND_IDS) {
				int lv = Books.wandEnch(held, e);
				Cost c = wandEnchCost(lv);
				boolean slotOk = lv > 0 || Books.enchCount(held) < Books.enchCapacity(held);
				List<String> tip = new ArrayList<>(List.of("§f" + Enchants.desc(e), "§6必要素材:"));
				tip.addAll(c.lines(p));
				m.row(Enchants.name(e) + " §b" + (lv > 0 ? Enchants.roman(lv) : "-") + " §7/ 最大 " + Enchants.roman(Enchants.max(e)), tip,
						lv >= Enchants.max(e) ? "最大" : "付与", "ench:" + e, lv < Enchants.max(e) && slotOk && c.has(p));
			}
		} else if (held.getItem() instanceof GearItem) {
			m.info("装備にも杖と同じ系統のエンチャントを付けられます(各 最大 III)。");
			for (String e : List.of("power", "cost")) {
				int lv = Books.data(held).getIntOr(e, 0);
				Cost c = Cost.of().add(Items.LAPIS_LAZULI, 4 * (lv + 1)).add(ModItems.MAGISTEEL_INGOT, 1).xp(lv + 1);
				List<String> tip = new ArrayList<>(List.of(e.equals("power") ? "§f魔法威力 +5%/Lv" : "§fMP自然回復 +5%/Lv", "§6必要素材:"));
				tip.addAll(c.lines(p));
				m.row((e.equals("power") ? "魔力強化" : "MP回復強化") + " §b" + (lv > 0 ? Enchants.roman(lv) : "-"), tip, lv >= 3 ? "最大" : "付与",
						"gench:" + e, lv < 3 && c.has(p));
			}
		} else {
			m.info("§e杖・魔法剣・装備品(帽子/ペンダント/手袋/腕輪)をメインハンドに持って開いてください。");
		}
	}

	private static Cost synthCost(ServerPlayer p, Spell base, List<Spell> subs, int tier) {
		Cost c = Cost.of().add(ModItems.SYNTH_CATALYST, tier * subs.size());
		List<Spell> all = new ArrayList<>(subs);
		all.add(base);
		for (Spell s : all) {
			c.add(ModItems.crystal(s.element), 4 * subs.size());
			if (tier >= 2) {
				c.add(ModItems.essence(s.element), 1);
			}
		}
		if (tier >= 3) {
			c.add(ModItems.midbossDrop(base.element), subs.size());
		}
		float red = 1 - 0.1f * PlayerMagic.of(p).skill("c_synth");
		return c.scale(red);
	}

	private static void synthTab(ServerPlayer p, State st, Model m) {
		PlayerMagic pm = PlayerMagic.of(p);
		elementSubs(m, st, heldElement(p));
		Spell base = Spells.get(st.synthBase);
		List<Spell> subs = new ArrayList<>();
		for (String id : st.synthSubs) {
			Spell s = Spells.get(id);
			if (s != null) {
				subs.add(s);
			}
		}
		m.info("ベースの攻撃の形 + サブの状態異常・特殊効果。失敗は無く、素材ランクが高いほど強い。合成魔法は本の枠を2つ使う。");
		var r = m.row("ベース: " + (base == null ? "§8(未選択)" : "§e" + base.name), base == null ? List.of() : spellTip(base), "解除",
				"sbase:clear", base != null);
		for (int i = 0; i < 2; i++) {
			Spell s = i < subs.size() ? subs.get(i) : null;
			m.row("サブ" + (i + 1) + ": " + (s == null ? "§8(未選択)" : "§b" + s.name), s == null ? List.of() : spellTip(s), "解除",
					"ssub:clear:" + i, s != null);
		}
		var tr = m.row("素材ランク: §6" + st.synthTier + " §7(威力 ×" + Spells.TIER_POWER[st.synthTier - 1] + ")", List.of(), "◀", "stier:-1",
				st.synthTier > 1);
		m.row2(tr, "▶", "stier:1", st.synthTier < 3);
		if (base != null && !subs.isEmpty()) {
			String key = Spells.recipeKey(base.id, subs.stream().map(s -> s.id).toList());
			PlayerMagic.Synth sy = new PlayerMagic.Synth("preview", base.id, subs.stream().map(s -> s.id).toList(), st.synthTier,
					Spells.autoName(base, subs));
			Spell out = Spells.build(sy);
			List<String> tip = new ArrayList<>();
			tip.add("§d" + sy.name() + (Spells.FIXED_RECIPES.containsKey(key) ? " §6[固定レシピ]" : pm.recipes.contains(key) ? " §a[既知]" : " §7[新発見]"));
			if (out != null) {
				tip.add("§7MP " + Math.round(out.mp) + " / CT " + String.format("%.1f", out.ct) + "秒 / ダメージ " + String.format("%.1f",
						out.element.base() * out.coef));
				StringBuilder stt = new StringBuilder(base.status);
				subs.forEach(s -> stt.append(" + ").append(s.status));
				tip.add("§7状態異常: §f" + stt);
				if (out.curseBonus != 1f) {
					tip.add("§5呪いの合成親和性: 状態異常 ×" + out.curseBonus);
				}
			}
			boolean hint = subs.stream().anyMatch(s -> Spells.goodPair(base.element, s.element));
			if (hint) {
				tip.add("§e★ 炉が光っている: 相性の良い組み合わせ");
			}
			Cost c = synthCost(p, base, subs, st.synthTier);
			tip.add("§6必要素材:");
			tip.addAll(c.lines(p));
			m.row((hint ? "§e★ " : "") + "§d" + sy.name() + " §7を合成する(プレビュー: マウスを乗せる)", tip, "合成する", "synth:do", c.has(p));
		}
		m.text("§6── 習得済みの魔法(" + Element.byId(sel(st.sub, heldElement(p).id)).ja + ") ──");
		Element e = Element.byId(sel(st.sub, heldElement(p).id));
		for (Spell s : Spells.of(e)) {
			if (!pm.learned.contains(s.id)) {
				continue;
			}
			boolean hint = base != null && Spells.goodPair(base.element, s.element);
			var row = m.row((hint ? "§e★ " : "") + circled(s.no) + " " + s.name, spellTip(s), "ベース", "sbase:" + s.id, !s.id.equals(st.synthBase));
			m.row2(row, "サブ", "ssub:" + s.id, base != null && !s.id.equals(st.synthBase) && st.synthSubs.size() < 2
					&& !st.synthSubs.contains(s.id));
		}
	}

	private static String magicTableAct(ServerPlayer p, State st, String act) {
		PlayerMagic pm = PlayerMagic.of(p);
		ItemStack held = p.getMainHandItem();
		String[] a = act.split(":", 3);
		switch (a[0]) {
			case "learn" -> {
				Spell s = Spells.get(a[1]);
				if (s == null || pm.learned.contains(s.id)) {
					return null;
				}
				if (s.no == 10 && bestBookLevel(p, s.element) < 4) {
					return "最大魔法には Lv4 以上の" + s.element.ja + "の本が必要です";
				}
				if (!learnCost(s).take(p)) {
					return "素材が足りません";
				}
				pm.learned.add(s.id);
				p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, p.getSoundSource(), 1f, 1f);
				p.sendSystemMessage(Component.literal("「" + s.name + "」を習得した!(装着タブで本に入れよう)").withStyle(s.element.fmt));
			}
			case "equip" -> {
				if (!(held.getItem() instanceof BookItem)) {
					return "本を持ってください";
				}
				Spell s = Spells.get(a[1]);
				CompoundTag book = Books.bookOf(held);
				List<String> slots = Books.slots(book);
				if (s == null || !pm.learned.contains(s.id) || slots.contains(s.id)) {
					return null;
				}
				if (Books.level(book) < s.requiredBookLevel()) {
					return "本Lv" + s.requiredBookLevel() + " が必要です";
				}
				int i = slots.indexOf("");
				if (i < 0) {
					return "空きスロットがありません";
				}
				slots.set(i, s.id);
				Books.setSlots(book, slots);
				Books.setBook(held, book);
			}
			case "equipsyn" -> {
				if (!(held.getItem() instanceof BookItem)) {
					return "本を持ってください";
				}
				ItemStack scroll = p.getInventory().getItem(Integer.parseInt(a[1]));
				if (!(scroll.getItem() instanceof SynthScrollItem)) {
					return null;
				}
				CompoundTag book = Books.bookOf(held);
				List<String> slots = Books.slots(book);
				int at = -1;
				for (int i = 0; i + 1 < slots.size(); i++) {
					if (slots.get(i).isEmpty() && slots.get(i + 1).isEmpty()) {
						at = i;
						break;
					}
				}
				if (at < 0) {
					return "空きスロットが2つ続いていません";
				}
				CompoundTag d = Books.data(scroll);
				List<String> subs = new ArrayList<>();
				for (var x : d.getListOrEmpty("subs")) {
					x.asString().ifPresent(subs::add);
				}
				String id = "s" + (++pm.synthCounter);
				pm.synths.put(id, new PlayerMagic.Synth(id, d.getStringOr("base", ""), subs, d.getIntOr("tier", 1), d.getStringOr("name", "合成魔法")));
				slots.set(at, "syn:" + id);
				slots.set(at + 1, "|");
				Books.setSlots(book, slots);
				Books.setBook(held, book);
				scroll.shrink(1);
			}
			case "unequip" -> {
				if (!(held.getItem() instanceof BookItem)) {
					return null;
				}
				CompoundTag book = Books.bookOf(held);
				List<String> slots = Books.slots(book);
				int i = Integer.parseInt(a[1]);
				if (i < 0 || i >= slots.size()) {
					return null;
				}
				String id = slots.get(i);
				if (id.startsWith("syn:")) {
					PlayerMagic.Synth sy = pm.synths.remove(id.substring(4));
					if (sy != null) {
						Status.give(p, SynthScrollItem.make(sy.base(), sy.subs(), sy.tier(), sy.name()));
					}
					if (i + 1 < slots.size() && slots.get(i + 1).equals("|")) {
						slots.set(i + 1, "");
					}
				}
				slots.set(i, "");
				Books.setSlots(book, slots);
				Books.setBook(held, book);
			}
			case "enhance" -> {
				Spell s = Spells.get(a[1]);
				if (s == null || !pm.learned.contains(s.id)) {
					return null;
				}
				int lv = pm.enhance.getOrDefault(s.id, 0);
				if (lv >= 5) {
					return null;
				}
				if (!enhanceCost(s, lv).take(p)) {
					return "素材が足りません";
				}
				pm.enhance.put(s.id, lv + 1);
				p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, p.getSoundSource(), 0.6f, 1.4f);
			}
			case "offer" -> {
				if (!(held.getItem() instanceof BookItem bi)) {
					return "本を持ってください";
				}
				Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(a[1] + ":" + a[2].substring(0, a[2].lastIndexOf(':'))));
				String howMany = a[2].substring(a[2].lastIndexOf(':') + 1);
				if (!offerItems(bi.element).contains(it)) {
					return null;
				}
				int have = Cost.count(p, it);
				int n = howMany.equals("all") ? have : Math.min(1, have);
				if (n <= 0) {
					return null;
				}
				Cost.of().add(it, n).take(p);
				CompoundTag book = Books.bookOf(held);
				int up = Books.addExp(book, Math.round(n * offerExp(it, bi.element) * (1 + 0.2f * pm.skill("c_exp"))));
				Books.setBook(held, book);
				p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, p.getSoundSource(), 0.8f, 1f);
				if (up > 0) {
					pm.skillPoints++;
					p.sendSystemMessage(Component.literal(bi.element.ja + "の魔法書が Lv" + up + " になった!(スキルポイント +1)").withStyle(ChatFormatting.GOLD));
				}
			}
			case "win" -> {
				if (!Books.isCaster(held)) {
					return "杖を持ってください";
				}
				ItemStack b = p.getInventory().getItem(Integer.parseInt(a[1]));
				if (!(b.getItem() instanceof BookItem)) {
					return null;
				}
				List<CompoundTag> books = Books.wandBooks(held);
				if (books.size() >= wandCap(p, held)) {
					return "この杖にはもう本をセットできません";
				}
				books.add(Books.bookOf(b));
				Books.setWandBooks(held, books);
				b.shrink(1);
				p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PUT, p.getSoundSource(), 1f, 1f);
			}
			case "wout" -> {
				if (!Books.isCaster(held) || Books.isFused(held)) {
					return null;
				}
				List<CompoundTag> books = Books.wandBooks(held);
				int i = Integer.parseInt(a[1]);
				if (i < 0 || i >= books.size()) {
					return null;
				}
				CompoundTag b = books.remove(i);
				Books.setWandBooks(held, books);
				Books.setSelected(held, 0);
				Status.give(p, Books.toItem(b));
			}
			case "wtop" -> {
				if (!Books.isCaster(held)) {
					return null;
				}
				List<CompoundTag> books = Books.wandBooks(held);
				int i = Integer.parseInt(a[1]);
				if (i <= 0 || i >= books.size()) {
					return null;
				}
				books.add(0, books.remove(i));
				Books.setWandBooks(held, books);
				Books.setSelected(held, 0);
			}
			case "ench" -> {
				if (!Books.isCaster(held)) {
					return null;
				}
				String e = a[1];
				int lv = Books.wandEnch(held, e);
				if (lv >= Enchants.max(e) || lv == 0 && Books.enchCount(held) >= Books.enchCapacity(held)) {
					return "エンチャント枠がありません";
				}
				if (!wandEnchCost(lv).take(p)) {
					return "素材が足りません";
				}
				Books.setWandEnch(held, e, lv + 1);
				p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, p.getSoundSource(), 1f, 1f);
			}
			case "gench" -> {
				if (!(held.getItem() instanceof GearItem)) {
					return null;
				}
				CompoundTag d = Books.data(held);
				int lv = d.getIntOr(a[1], 0);
				if (lv >= 3) {
					return null;
				}
				if (!Cost.of().add(Items.LAPIS_LAZULI, 4 * (lv + 1)).add(ModItems.MAGISTEEL_INGOT, 1).xp(lv + 1).take(p)) {
					return "素材が足りません";
				}
				d.putInt(a[1], lv + 1);
				Books.setData(held, d);
			}
			case "sbase" -> {
				if (a[1].equals("clear")) {
					st.synthBase = "";
					st.synthSubs.clear();
				} else if (pm.learned.contains(a[1])) {
					st.synthBase = a[1];
					st.synthSubs.remove(a[1]);
				}
			}
			case "ssub" -> {
				if (a[1].equals("clear")) {
					int i = Integer.parseInt(a[2]);
					if (i < st.synthSubs.size()) {
						st.synthSubs.remove(i);
					}
				} else if (pm.learned.contains(a[1]) && st.synthSubs.size() < 2 && !st.synthSubs.contains(a[1]) && !a[1].equals(st.synthBase)) {
					st.synthSubs.add(a[1]);
				}
			}
			case "stier" -> st.synthTier = Math.max(1, Math.min(3, st.synthTier + Integer.parseInt(a[1])));
			case "sprep" -> {
				String[] parts = act.substring(6).split("\\+");
				for (String s : parts) {
					if (!pm.learned.contains(s)) {
						return "まだ習得していない魔法が含まれています: " + Net.spellName(s);
					}
				}
				st.synthBase = parts[0];
				st.synthSubs.clear();
				for (int i = 1; i < parts.length; i++) {
					st.synthSubs.add(parts[i]);
				}
				st.tab = "synth";
				st.sub = Spells.get(parts[0]).element.id;
			}
			case "synth" -> {
				Spell base = Spells.get(st.synthBase);
				List<Spell> subs = new ArrayList<>();
				st.synthSubs.forEach(id -> {
					Spell s = Spells.get(id);
					if (s != null) {
						subs.add(s);
					}
				});
				if (base == null || subs.isEmpty()) {
					return "ベースとサブを選んでください";
				}
				if (!synthCost(p, base, subs, st.synthTier).take(p)) {
					return "素材が足りません";
				}
				List<String> ids = subs.stream().map(s -> s.id).toList();
				String key = Spells.recipeKey(base.id, ids);
				boolean fresh = pm.recipes.add(key) && !Spells.FIXED_RECIPES.containsKey(key);
				String name = Spells.autoName(base, subs);
				Status.give(p, SynthScrollItem.make(base.id, ids, st.synthTier, name));
				((ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.5, p.getZ(),
						40, 0.6, 0.6, 0.6, 1);
				p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, p.getSoundSource(), 1f, 1.4f);
				p.sendSystemMessage(Component.literal("合成成功!「" + name + "」の巻物を手に入れた" + (fresh ? "(新しいレシピを発見)" : "")
						+ "  装着タブで" + base.element.ja + "の本に入れよう").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			default -> {
			}
		}
		return null;
	}

	// =============================================================================================
	// 鍛冶テーブル

	private static Cost techCost(Techniques.Tech t) {
		return switch (t.rank) {
			case "basic" -> Cost.of().add(Items.IRON_INGOT, 2).add(ModItems.CRYSTAL_SHARD, 2);
			case "middle" -> Cost.of().add(ModItems.SILVER_INGOT, 2).add(Items.IRON_INGOT, 2);
			case "upper" -> Cost.of().add(ModItems.MAGISTEEL_INGOT, 2).add(Items.DIAMOND, 1);
			default -> Cost.of().add(ModItems.STARDUST_SHARD, 1).add(ModItems.MAGISTEEL_INGOT, 4).add(Items.DIAMOND, 2);
		};
	}

	private static Cost weaponEnchCost(int lv) {
		return Cost.of().add(ModItems.SILVER_INGOT, lv + 1).add(Items.LAPIS_LAZULI, lv + 1).xp((lv + 1) * 2);
	}

	private static int weaponEnchCount(ItemStack s) {
		CompoundTag e = Books.data(s).getCompoundOrEmpty("mm_wench");
		int n = 0;
		for (String k : e.keySet()) {
			if (e.getIntOr(k, 0) > 0) {
				n++;
			}
		}
		return n;
	}

	private static int weaponEnchCap(ItemStack s) {
		return Books.isFused(s) ? Math.min(8, 3 + Books.data(s).getIntOr("mm_tier", 1)) - Books.enchCount(s) + 0 : 3;
	}

	private static Model forgeTable(ServerPlayer p, State st) {
		PlayerMagic pm = PlayerMagic.of(p);
		Model m = new Model("forge_table", "鍛冶テーブル");
		String tab = sel(st.tab, "learn");
		String[][] tabs = { { "learn", "わざ習得" }, { "equip", "わざ装着" }, { "ench", "武器エンチャント" }, { "fuse", "武器合体" } };
		for (String[] t : tabs) {
			m.tab(t[0], t[1], tab.equals(t[0]));
		}
		ItemStack held = p.getMainHandItem();
		String cat = ModItems.category(held);
		if (cat == null && !tab.equals("fuse")) {
			m.info("§e武器(剣・斧・メイス・槍・弓・MODの武器など)をメインハンドに持って開いてください。");
			return m;
		}
		switch (tab) {
			case "learn" -> {
				m.info(Techniques.categoryName(cat) + "  熟練度 Lv" + pm.masteryLevel(cat) + " (EXP " + pm.mastery.getOrDefault(cat, 0) + ")  基準値 "
						+ Math.round(Balance.get().categoryBase.getOrDefault(cat, 8f)));
				for (Techniques.Tech t : Techniques.of(cat)) {
					boolean known = pm.techLearned.contains(t.id);
					Cost c = techCost(t);
					boolean mOk = pm.masteryLevel(cat) >= t.requiredMastery();
					List<String> tip = new ArrayList<>(List.of("§f" + t.name, "§7状態異常: " + t.row.status(), "§7特殊効果: " + t.row.special(),
							"§7スタミナ " + Math.round(t.stamina) + " / CT " + t.ct + "秒", "§7必要熟練度 Lv" + t.requiredMastery(), "§6必要素材:"));
					tip.addAll(c.lines(p));
					m.row(circled(t.no) + " " + t.name + (known ? " §a習得済" : mOk ? "" : " §c(熟練度Lv" + t.requiredMastery() + ")"), tip,
							known ? "習得済" : "習得", "tlearn:" + t.id, !known && mOk && c.has(p));
				}
			}
			case "equip" -> {
				int n = Techniques.slots(pm, cat, held);
				List<String> lo = pm.techLoadout(cat);
				lo.removeIf(String::isEmpty);
				m.info(Techniques.categoryName(cat) + " のわざスロット " + lo.size() + " / " + n + "  ([G] で使用、[V] で切替)");
				for (int i = 0; i < lo.size(); i++) {
					Techniques.Tech t = Techniques.get(lo.get(i));
					m.row("[" + (i + 1) + "] " + (t == null ? lo.get(i) : t.name), List.of(), "外す", "tunequip:" + i, true);
				}
				m.text("§6── 習得済みのわざ ──");
				for (Techniques.Tech t : Techniques.of(cat)) {
					if (pm.techLearned.contains(t.id)) {
						boolean in = lo.contains(t.id);
						m.row(circled(t.no) + " " + t.name, List.of("§7" + t.row.special()), in ? "装着中" : "装着", "tequip:" + t.id, !in && lo.size() < n);
					}
				}
			}
			case "ench" -> {
				int cap = weaponEnchCap(held);
				m.info("武器エンチャント " + weaponEnchCount(held) + " / " + cap + " 枠" + (Books.isFused(held) ? "(魔法剣: 杖と武器の合算)" : ""));
				for (String e : Enchants.WEAPON_IDS) {
					int lv = Calc.weaponEnch(held, e);
					Cost c = weaponEnchCost(lv);
					boolean slotOk = lv > 0 || weaponEnchCount(held) < cap;
					List<String> tip = new ArrayList<>(List.of("§f" + Enchants.desc(e), "§6必要素材:"));
					tip.addAll(c.lines(p));
					m.row(Enchants.name(e) + " §b" + (lv > 0 ? Enchants.roman(lv) : "-") + " §7/ 最大 " + Enchants.roman(Enchants.max(e)), tip,
							lv >= Enchants.max(e) ? "最大" : "付与", "wench:" + e, lv < Enchants.max(e) && slotOk && c.has(p));
				}
			}
			case "fuse" -> {
				ItemStack off = p.getOffhandItem();
				m.info("メインハンドに近接武器、オフハンドに杖を持って合体します(設計書 第9章)。");
				m.info("合体後: 近接攻撃力はそのまま、杖の近接付与系の魔法が通常攻撃に乗り、右クリック/[R] で魔法を撃てる。");
				m.info("近接ヒット時のMP回復が2倍。エンチャント枠は杖と武器の合算。エレメンタルの属性は合体時の1番目の本で固定。");
				if (Books.isFused(held)) {
					m.text("§aこの武器はすでに魔法剣です。");
					break;
				}
				boolean weaponOk = cat != null && !cat.equals("bow") && !cat.equals("throw");
				boolean wandOk = off.getItem() instanceof WandItem && !Books.wandBooks(off).isEmpty();
				Element e = wandOk ? Books.firstElement(off) : null;
				m.text((weaponOk ? "§a✔" : "§c✘") + " メインハンド: 近接武器 " + (weaponOk ? held.getHoverName().getString() : ""));
				m.text((wandOk ? "§a✔" : "§c✘") + " オフハンド: 本をセットした杖");
				if (e != null) {
					Cost c = Cost.of().add(ModItems.ingot(e), 1).xp(5);
					List<String> tip = new ArrayList<>(List.of("§6必要素材:"));
					tip.addAll(c.lines(p));
					m.row("合体する(" + e.ja + "の属性インゴットを使う)", tip, "合体", "fuse", weaponOk && c.has(p));
				}
			}
			default -> {
			}
		}
		return m;
	}

	private static String forgeAct(ServerPlayer p, State st, String act) {
		PlayerMagic pm = PlayerMagic.of(p);
		ItemStack held = p.getMainHandItem();
		String cat = ModItems.category(held);
		String[] a = act.split(":", 2);
		switch (a[0]) {
			case "tlearn" -> {
				Techniques.Tech t = Techniques.get(a[1]);
				if (t == null || pm.techLearned.contains(t.id) || pm.masteryLevel(t.category) < t.requiredMastery()) {
					return null;
				}
				if (!techCost(t).take(p)) {
					return "素材が足りません";
				}
				pm.techLearned.add(t.id);
				List<String> lo = pm.techLoadout(t.category);
				lo.removeIf(String::isEmpty);
				if (lo.size() < Techniques.slots(pm, t.category, held)) {
					lo.add(t.id);
				}
				p.level().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, p.getSoundSource(), 1f, 1f);
				p.sendSystemMessage(Component.literal("わざ「" + t.name + "」を習得した!").withStyle(ChatFormatting.GOLD));
			}
			case "tequip" -> {
				Techniques.Tech t = Techniques.get(a[1]);
				if (t == null || cat == null || !t.category.equals(cat) || !pm.techLearned.contains(t.id)) {
					return null;
				}
				List<String> lo = pm.techLoadout(cat);
				lo.removeIf(String::isEmpty);
				if (lo.contains(t.id) || lo.size() >= Techniques.slots(pm, cat, held)) {
					return "スロットがいっぱいです";
				}
				lo.add(t.id);
			}
			case "tunequip" -> {
				if (cat == null) {
					return null;
				}
				List<String> lo = pm.techLoadout(cat);
				int i = Integer.parseInt(a[1]);
				if (i >= 0 && i < lo.size()) {
					lo.remove(i);
				}
			}
			case "wench" -> {
				String e = a[1];
				int lv = Calc.weaponEnch(held, e);
				if (cat == null || lv >= Enchants.max(e) || lv == 0 && weaponEnchCount(held) >= weaponEnchCap(held)) {
					return "エンチャント枠がありません";
				}
				if (!weaponEnchCost(lv).take(p)) {
					return "素材が足りません";
				}
				CompoundTag d = Books.data(held);
				CompoundTag w = d.getCompoundOrEmpty("mm_wench");
				w.putInt(e, lv + 1);
				d.put("mm_wench", w);
				Books.setData(held, d);
				p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, p.getSoundSource(), 0.6f, 1.2f);
			}
			case "fuse" -> {
				ItemStack off = p.getOffhandItem();
				if (cat == null || cat.equals("bow") || cat.equals("throw") || Books.isFused(held) || !(off.getItem() instanceof WandItem w)) {
					return "近接武器と杖を持ってください";
				}
				Element e = Books.firstElement(off);
				if (e == null) {
					return "杖に本が入っていません";
				}
				if (!Cost.of().add(ModItems.ingot(e), 1).xp(5).take(p)) {
					return "素材が足りません";
				}
				CompoundTag d = Books.data(held);
				CompoundTag wd = Books.data(off);
				d.putBoolean("mm_fused", true);
				d.putString("mm_fel", e.id);
				d.putInt("mm_tier", w.tier);
				d.put("mm_books", wd.getListOrEmpty("mm_books").copy());
				d.put("mm_ench", wd.getCompoundOrEmpty("mm_ench").copy());
				d.putInt("mm_enchcap", w.tier);
				Books.setData(held, d);
				held.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
						Component.literal("魔法剣(" + e.ja + ")・" + held.getHoverName().getString()).withStyle(e.fmt));
				off.shrink(1);
				((ServerLevel) p.level()).sendParticles(Fx.big(e), p.getX(), p.getY() + 1.2, p.getZ(), 40, 0.5, 0.5, 0.5, 0.1);
				p.level().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, p.getSoundSource(), 1f, 0.7f);
				p.sendSystemMessage(Component.literal("武器と杖が合体し、魔法剣になった!").withStyle(ChatFormatting.GOLD));
			}
			default -> {
			}
		}
		return null;
	}

	// =============================================================================================
	// 祭壇

	static Cost contractCost(Familiars.Kind k) {
		return switch (k) {
			case WOLF -> Cost.of().add(Items.BONE, 8).add(ModItems.CRYSTAL_SHARD, 4);
			case MUD_SLIME -> Cost.of().add(Items.SLIME_BALL, 8).add(ModItems.crystal(Element.WATER), 2);
			case FAIRY -> Cost.of().add(Items.AMETHYST_SHARD, 4).add(ModItems.crystal(Element.LIGHT), 4).add(ModItems.essence(Element.LIGHT), 1);
			case HAWK -> Cost.of().add(Items.FEATHER, 8).add(ModItems.crystal(Element.WIND), 4);
			case GOLEM -> Cost.of().add(Items.IRON_BLOCK, 2).add(ModItems.crystal(Element.GRAVITY), 4);
			case SPIRIT_WOLVES -> Cost.of().add(Items.BONE, 16).add(ModItems.crystal(Element.DARK), 4).add(ModItems.essence(Element.DARK), 1);
			case ELEMENTAL -> Cost.of().add(Items.BLAZE_ROD, 4).add(ModItems.essence(Element.FIRE), 1).add(ModItems.essence(Element.WATER), 1)
					.add(ModItems.essence(Element.WIND), 1);
			case GUARDIAN -> Cost.of().add(ModItems.minionDrop(Element.LIGHT), 4).add(Items.GOLD_BLOCK, 1).add(ModItems.essence(Element.LIGHT), 1);
			case PHOENIX -> Cost.of().add(ModItems.midbossDrop(Element.FIRE), 2).add(ModItems.essence(Element.FIRE), 2).add(Items.TOTEM_OF_UNDYING, 1);
			case ANCIENT_DRAGON -> Cost.of().add(ModItems.DRAGON_BONE, 4).add(Items.NETHER_STAR, 1).add(Items.DRAGON_HEAD, 1);
		};
	}

	static Cost runeCost(Runes r) {
		Cost c = Cost.of().add(ModItems.RUNE_STONE, r == Runes.CONQUEROR ? 4 : 1);
		return switch (r) {
			case MIGHT -> c.add(Items.BLAZE_POWDER, 4);
			case SWIFT -> c.add(Items.SUGAR, 8).add(ModItems.crystal(Element.WIND), 2);
			case IRON_WALL -> c.add(Items.IRON_BLOCK, 1);
			case REGEN -> c.add(Items.GLISTERING_MELON_SLICE, 2).add(Items.GHAST_TEAR, 1);
			case CLARITY -> c.add(ModItems.crystal(Element.LIGHT), 4).add(Items.AMETHYST_SHARD, 4);
			case FIGHTING_SPIRIT -> c.add(ModItems.crystal(Element.FIRE), 2).add(ModItems.crystal(Element.THUNDER), 2);
			case GUARD -> c.add(Items.IRON_INGOT, 6).add(ModItems.crystal(Element.GRAVITY), 2);
			case HASTE -> c.add(ModItems.crystal(Element.THUNDER), 4).add(Items.SUGAR, 4);
			case CRITICAL -> c.add(Items.FLINT, 8).add(ModItems.crystal(Element.DARK), 2);
			case CONQUEROR -> c.add(ModItems.STARDUST_SHARD, 1).add(Items.DIAMOND_BLOCK, 1);
		};
	}

	private static Model summoningAltar(ServerPlayer p, State st) {
		Model m = new Model("summoning_altar", "召喚祭壇");
		PlayerMagic pm = PlayerMagic.of(p);
		m.info("使い魔契約書を作ります。契約書を右クリックで契約、[H] で召喚/送還。召喚中は時間でMPを消費します。");
		m.info("§7契約スロット " + pm.contracts.size() + " / " + Familiars.slots(pm) + "(契約の腕輪・スキルで2体まで)");
		for (Familiars.Kind k : Familiars.Kind.values()) {
			Balance.FamiliarRow r = k.row();
			Cost c = contractCost(k);
			List<String> tip = new ArrayList<>(List.of("§f" + r.name() + " §7(" + r.role() + ")", "§7" + r.special(),
					"§7HP " + Math.round(r.hp()) + " / 攻撃 " + Math.round(r.attack()) + " / 維持MP " + r.upkeep() + "/秒",
					k.bonus == null ? "§7アーキタイプ補正なし" : "§7" + k.bonus.ja + "なら性能+10%・維持コスト-10%", "§6必要素材:"));
			tip.addAll(c.lines(p));
			m.row(r.name() + " §7- " + r.role() + (pm.contracts.contains(k.id) ? " §a契約中" : ""), tip, "契約書を作る", "make:" + k.id, c.has(p));
		}
		return m;
	}

	private static Model arcaneAltar(ServerPlayer p, State st) {
		Model m = new Model("arcane_altar", "秘術の祭壇");
		PlayerMagic pm = PlayerMagic.of(p);
		m.info("刻印石を加工して刻印を作ります。刻印を右クリックで刻む(常時発動・MP消費なし)。同じ刻印は重ね掛け不可。");
		m.info("§7オーラスロット " + pm.runes.size() + " / " + Runes.slots(pm));
		for (Runes r : Runes.values()) {
			Balance.RuneRow row = r.row();
			Cost c = runeCost(r);
			List<String> tip = new ArrayList<>(List.of("§f" + row.name() + " §7(" + row.kind() + ")", "§7" + row.effect(),
					"§7効果量 均衡 " + pct(row.value()) + " / 物理 " + pct(row.valuePhysical()) + " / 魔法 " + pct(row.valueMagic()), "§6必要素材:"));
			tip.addAll(c.lines(p));
			m.row(row.name() + " §7- " + row.effect() + (pm.runes.contains(r.id) ? " §a刻印中" : ""), tip, "刻印を作る", "make:" + r.id, c.has(p));
		}
		return m;
	}

	private static String pct(float f) {
		return Math.round(f * 1000) / 10f + "%";
	}

	private static String altarAct(ServerPlayer p, State st, String act) {
		if (!act.startsWith("make:")) {
			return null;
		}
		String id = act.substring(5);
		if (st.screen.equals("summoning_altar")) {
			Familiars.Kind k = Familiars.Kind.byId(id);
			if (k == null) {
				return null;
			}
			if (!contractCost(k).take(p)) {
				return "素材が足りません";
			}
			Status.give(p, new ItemStack(ModItems.CONTRACTS.get(k.id)));
		} else {
			Runes r = Runes.byId(id);
			if (r == null) {
				return null;
			}
			if (!runeCost(r).take(p)) {
				return "素材が足りません";
			}
			Status.give(p, new ItemStack(ModItems.RUNES.get(r)));
		}
		p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, p.getSoundSource(), 1f, 0.8f);
		return null;
	}

	private static Model archetypeAltar(ServerPlayer p, State st) {
		Model m = new Model("archetype_altar", "選定の祭壇(アーキタイプ)");
		PlayerMagic pm = PlayerMagic.of(p);
		m.info("プレイの方向性を選びます。今: §e" + pm.archetype.ja + (pm.archetype == Archetype.NONE ? "(均衡型と同じ扱い)" : ""));
		m.info(pm.archetype == Archetype.NONE ? "§7最初の1回は無料。" : "§7選び直すには「転身の宝珠」が1個必要です。");
		Balance b = Balance.get();
		for (Archetype a : List.of(Archetype.PHYSICAL, Archetype.MAGIC, Archetype.BALANCED)) {
			Balance.ArchRow r = b.arch(a);
			List<String> tip = new ArrayList<>();
			tip.add("§7物理わざ威力 ×" + r.techPower() + " / わざCT ×" + r.techCt() + " / スタミナ上限 ×" + r.staminaMax());
			tip.add("§7魔法威力 ×" + r.spellPower() + " / 魔法CT ×" + r.spellCt() + " / MP上限 ×" + r.mpMax());
			switch (a) {
				case PHYSICAL -> {
					tip.add("§f手数は物理わざで。魔法は強いが連打できない「切り札」。");
					tip.add("§f使い魔(ウルフ・ゴーレム等)と物理系刻印(剛力・鉄壁)が+10%");
				}
				case MAGIC -> {
					tip.add("§f左クリックがMP・CT消費なしの「魔封じの型」になる(当たるとMP回復)。");
					tip.add("§f使い魔(エレメンタル・フェニックス等)と魔法系刻印(明鏡・迅速)が+10%");
				}
				default -> {
					tip.add("§f補正なし。器用貧乏だが安定。");
					tip.add("§f物理型・魔法型の固有装備を両方同時に装着できる唯一の型。");
				}
			}
			boolean cur = pm.archetype == a;
			m.row("§e" + a.ja, tip, cur ? "選択中" : "これにする", "arch:" + a.id, !cur && (pm.archetype == Archetype.NONE
					|| Cost.of().add(ModItems.REBIRTH_ORB, 1).has(p)));
		}
		return m;
	}

	private static String archetypeAct(ServerPlayer p, String act) {
		if (!act.startsWith("arch:")) {
			return null;
		}
		PlayerMagic pm = PlayerMagic.of(p);
		Archetype a = Archetype.byId(act.substring(5));
		if (a == Archetype.NONE || a == pm.archetype) {
			return null;
		}
		if (pm.archetype != Archetype.NONE && !Cost.of().add(ModItems.REBIRTH_ORB, 1).take(p)) {
			return "転身の宝珠が必要です";
		}
		pm.archetype = a;
		for (int i = 0; i < 4; i++) {
			Gear.Kind k = pm.accessories[i] == null ? null : Gear.byId(pm.accessories[i].replace(MagicMod.MOD_ID + ":", ""));
			if (k != null && !k.canEquip(a)) {
				Status.unequip(p, i);
			}
		}
		pm.mp = Math.min(pm.mp, Calc.maxMp(pm));
		((ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(),
				40, 0.5, 1, 0.5, 0.3);
		p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, p.getSoundSource(), 1f, 1f);
		p.sendSystemMessage(Component.literal("アーキタイプ「" + a.ja + "」を選んだ").withStyle(ChatFormatting.GOLD));
		return null;
	}

	static Cost summonCost(Enemies.Def d) {
		Element e = d.element();
		if (d.tier() == Enemies.Tier.MIDBOSS) {
			return Cost.of().add(ModItems.crystal(e), 12).add(ModItems.minionDrop(e), 4).add(Items.GOLD_INGOT, 4);
		}
		return Cost.of().add(ModItems.midbossDrop(e), 3).add(ModItems.essence(e), 4).add(Items.DIAMOND, 2);
	}

	private static Model sealAltar(ServerPlayer p, State st) {
		Model m = new Model("seal_altar", "封印の祭壇");
		String sub = sel(st.sub, "midboss");
		m.sub("midboss", "中ボス", sub.equals("midboss"));
		m.sub("boss", "属性超越ボス", sub.equals("boss"));
		m.info("封印を解いて強敵を呼び出します。中ボスは中位〜上位魔法の素材、ボスは最大魔法に必要な「属性核石」を落とします。");
		m.info("§7ボスは自分と同じ属性が半減(免疫)。弱点属性の本に持ち替えよう。ボスは第2形態(HP50%以下)で技が変わる。");
		PlayerMagic pm = PlayerMagic.of(p);
		for (Enemies.Def d : Enemies.BY_ID.values()) {
			if (d.tier() == Enemies.Tier.MINION || d.tier() == Enemies.Tier.MIDBOSS != sub.equals("midboss")) {
				continue;
			}
			Cost c = summonCost(d);
			List<String> tip = new ArrayList<>(List.of("§f" + d.name(), "§7属性 " + d.element().ja + " / 弱点 " + (d.weak() == null ? "-" : d.weak().ja)
					+ " / HP " + Math.round(d.hp()) + " / 防御係数 " + d.defense(), "§6必要素材:"));
			tip.addAll(c.lines(p));
			m.row(d.element().fmt + "[" + d.element().ja + "] §f" + d.name() + (pm.defeated.contains(d.id()) ? " §a討伐済" : ""), tip, "召喚",
					"summon:" + d.id(), c.has(p));
		}
		return m;
	}

	private static String sealAct(ServerPlayer p, State st, String act) {
		if (!act.startsWith("summon:")) {
			return null;
		}
		Enemies.Def d = Enemies.BY_ID.get(act.substring(7));
		if (d == null || d.tier() == Enemies.Tier.MINION) {
			return null;
		}
		if (!summonCost(d).take(p)) {
			return "素材が足りません";
		}
		Vec3 c = Vec3.atBottomCenterOf(st.pos.above());
		Vec3 away = c.subtract(p.position());
		away = new Vec3(away.x, 0, away.z);
		if (away.lengthSqr() < 0.01) {
			away = new Vec3(1, 0, 0);
		}
		Vec3 pos = c.add(away.normalize().scale(4));
		Enemies.summonAt((ServerLevel) p.level(), d, pos);
		p.closeContainer();
		Net.sendUi(p, closeTag());
		STATES.remove(p.getUUID());
		return null;
	}

	private static CompoundTag closeTag() {
		CompoundTag t = new CompoundTag();
		t.putBoolean("close", true);
		return t;
	}

	// =============================================================================================
	// ステータス画面

	private static Model status(ServerPlayer p, State st) {
		PlayerMagic pm = PlayerMagic.of(p);
		Model m = new Model("status", "ステータス");
		String tab = sel(st.tab, "overview");
		String[][] tabs = { { "overview", "概要" }, { "runes", "刻印" }, { "gear", "装備" }, { "familiars", "使い魔" }, { "skills", "スキルツリー" } };
		for (String[] t : tabs) {
			m.tab(t[0], t[1], tab.equals(t[0]));
		}
		switch (tab) {
			case "overview" -> {
				m.info("アーキタイプ: §e" + pm.archetype.ja + (pm.archetype == Archetype.NONE ? " §7(選定の祭壇で選ぶ)" : ""));
				m.info("MP " + Math.round(pm.mp) + " / " + Math.round(Calc.maxMp(pm)) + "   スタミナ " + Math.round(pm.stamina) + " / "
						+ Math.round(Calc.maxStamina(pm)) + "   スキルポイント §b" + pm.skillPoints);
				m.info("習得した魔法 " + pm.learned.size() + " / 104   わざ " + pm.techLearned.size() + " / 63   討伐したボス "
						+ pm.defeated.stream().filter(id -> Enemies.BY_ID.containsKey(id) && Enemies.BY_ID.get(id).tier() == Enemies.Tier.BOSS).count()
						+ " / 8");
				for (String c : Techniques.CATEGORIES) {
					m.text(Techniques.categoryName(c) + "  熟練度 Lv" + pm.masteryLevel(c) + " §7(EXP " + pm.mastery.getOrDefault(c, 0) + ")");
				}
				m.text("§7操作: 右クリック/[R]=魔法  [Z]/[X]=魔法切替  [G]=物理わざ  [V]=わざ切替  [H]=使い魔  [K]=この画面");
			}
			case "runes" -> {
				m.info("オーラスロット " + pm.runes.size() + " / " + Runes.slots(pm) + "  (戦闘中の切り替えは60秒のクールタイム)");
				for (String id : pm.runes) {
					Runes r = Runes.byId(id);
					if (r != null) {
						m.row(r.ja() + " §7- " + r.row().effect() + " §b" + pct(r.value(pm.archetype)), List.of(), "外す", "rune_off:" + id, true);
					}
				}
				if (pm.runes.isEmpty()) {
					m.text("§8刻印なし(秘術の祭壇で作り、右クリックで刻む)");
				}
			}
			case "gear" -> {
				m.info("装備は右クリックで付けます。物理型・魔法型の固有装備は、均衡型なら両方付けられます。");
				for (Gear.Slot s : Gear.Slot.values()) {
					ItemStack g = Status.gearStack(pm, s.ordinal());
					m.row(s.ja + ": " + (g.isEmpty() ? "§8(なし)" : "§f" + g.getHoverName().getString()), List.of(), "外す", "gear_off:" + s.ordinal(),
							!g.isEmpty());
				}
			}
			case "familiars" -> {
				m.info("契約スロット " + pm.contracts.size() + " / " + Familiars.slots(pm) + "   召喚中 " + pm.activeFamiliars.size() + " 体");
				for (String id : pm.contracts) {
					Familiars.Kind k = Familiars.Kind.byId(id);
					if (k != null) {
						m.row(k.ja() + " Lv" + Familiars.level(pm, k) + " §7(維持MP " + String.format("%.2f", Familiars.upkeep(pm, k)) + "/秒)",
								List.of("§7" + k.row().special()), "解約", "fam_off:" + id, true);
					}
				}
				m.row("召喚 / 送還", List.of(), pm.activeFamiliars.isEmpty() ? "召喚" : "送還", "fam_toggle", !pm.contracts.isEmpty());
			}
			case "skills" -> {
				String sub = sel(st.sub, "common");
				m.sub("common", "共通", sub.equals("common"));
				m.sub("physical", "物理", sub.equals("physical"));
				for (Element e : Element.values()) {
					m.sub("el:" + e.id, e.ja, sub.equals("el:" + e.id));
				}
				m.info("スキルポイント §b" + pm.skillPoints + " §7(本のLvアップ・熟練度アップ・ボス討伐・経験値レベル10ごとに入手)");
				for (SkillTree.Node n : SkillTree.group(sub)) {
					int lv = pm.skill(n.id());
					m.row(n.name() + " §b" + lv + "/" + n.max(), List.of("§7" + n.desc()), lv >= n.max() ? "最大" : "習得(SP" + n.cost() + ")",
							"skill:" + n.id(), lv < n.max() && pm.skillPoints >= n.cost());
				}
			}
			default -> {
			}
		}
		return m;
	}

	private static String statusAct(ServerPlayer p, State st, String act) {
		PlayerMagic pm = PlayerMagic.of(p);
		String[] a = act.split(":", 2);
		switch (a[0]) {
			case "rune_off" -> Status.removeRune(p, a[1]);
			case "gear_off" -> Status.unequip(p, Integer.parseInt(a[1]));
			case "fam_off" -> {
				Familiars.dismissAll(p);
				pm.contracts.remove(a[1]);
				Familiars.Kind k = Familiars.Kind.byId(a[1]);
				if (k != null && k != Familiars.Kind.PHOENIX) {
					Status.give(p, new ItemStack(ModItems.CONTRACTS.get(k.id)));
				}
			}
			case "fam_toggle" -> Familiars.toggle(p);
			case "skill" -> {
				return SkillTree.learn(pm, a[1]);
			}
			default -> {
			}
		}
		return null;
	}

	// =============================================================================================
	// 手引き

	private static Model guide(ServerPlayer p, State st) {
		Model m = new Model("guide", "魔法の手引き");
		String tab = sel(st.tab, "howto");
		String[][] tabs = { { "howto", "遊び方" }, { "affinity", "属性相性" }, { "enemies", "敵図鑑" }, { "numbers", "数値" } };
		for (String[] t : tabs) {
			m.tab(t[0], t[1], tab.equals(t[0]));
		}
		switch (tab) {
			case "howto" -> {
				String[] lines = { "§e1. 選定の祭壇でアーキタイプ(物理型/魔法型/均衡型)を選ぶ",
						"§e2. 水晶鉱石を掘って水晶欠片 → 木の杖・属性の結晶・魔法の本をクラフト",
						"§e3. 魔法テーブル: 「獲得」で魔法を習得 → 本を持って「装着」 → 杖を持って「杖の本」で本をセット",
						"§e4. 杖を右クリック(または [R])で魔法。[Z]/[X] で切替。MPは近接攻撃を当てると回復する(殴って溜めて撃つ)",
						"§e5. 同じ魔法・属性を連発するとオーバーヒート(MP・CTが増える)。時間で冷める",
						"§e6. 鍛冶テーブル: 武器を持って物理わざを習得・装着。[G] で使い [V] で切替。スタミナを使う",
						"§e7. 敵を倒して素材 → 中位・上位魔法。封印の祭壇で中ボス・ボスを呼び、属性核石で最大魔法",
						"§e8. 秘術の祭壇で刻印(常時バフ)、召喚祭壇で使い魔契約書。帽子・ペンダント等は右クリックで装備",
						"§e9. 合成炉(魔法テーブル): 魔法を組み合わせて合成魔法の巻物を作る。失敗なし。本の枠を2つ使う",
						"§e10. 鍛冶テーブル「武器合体」: 武器+杖で魔法剣。近接でMPがよく回復し、近接付与魔法が乗る",
						"§7[K] ステータス画面: スキルツリー・刻印・装備・使い魔の管理" };
				for (String s : lines) {
					m.text(s);
				}
			}
			case "affinity" -> {
				String[] lines = { "§c炎§f: 凍結した敵に強い / 水中・水属性の敵に弱い", "§9水§f: 燃えている敵に強い(消火+追加ダメージ) / 電気の敵に通りにくい",
						"§e電気§f: 濡れた敵(水魔法・雨・水中)に強い / 転倒・拘束で地に伏せた敵に弱い", "§a風§f: 毒・出血・火傷中の敵に強い / ノックバック耐性の高い重い敵に弱い",
						"§5重力§f: 飛行・浮遊中の敵に強い(強制落下) / 水中の敵に弱い", "§8闇§f: アンデッド以外の高HPの敵に強い",
						"§f光: アンデッドに2倍(最大魔法は3倍)・暗闇の敵に強い / 速く動く敵に当たりにくい", "§d呪い§f: デバフの無い敵に強い / ボス(状態異常耐性)に弱い",
						"§7敵の弱点属性 ×1.5、同属性(免疫) ×0.5", "§7合成: 呪い×闇=親和性 高 / 呪い×電気・重力=中 / 呪い×光=低(打ち消し合う)" };
				for (String s : lines) {
					m.text(s);
				}
			}
			case "enemies" -> {
				for (Enemies.Def d : Enemies.BY_ID.values()) {
					String tier = switch (d.tier()) {
						case MINION -> "雑魚";
						case MIDBOSS -> "中ボス";
						case BOSS -> "ボス";
					};
					m.text(d.element().fmt + "[" + d.element().ja + "] §f" + d.name() + " §7(" + tier + " / HP " + Math.round(d.hp()) + " / 弱点 "
							+ (d.weak() == null ? "-" : d.weak().ja) + ")");
				}
			}
			case "numbers" -> {
				Balance b = Balance.get();
				m.info("数値は「魔法MOD_数値バランス.xlsx」から作った balance.json の値です(config/magicmod/balance.json で上書き可)。");
				for (Element e : Element.values()) {
					m.text(e.ja + " 基準値 " + Math.round(e.base()));
				}
				b.spellRanks.forEach((k, v) -> m.text("§7" + v.name() + ": 係数 " + v.coef() + " / MP " + v.cost() + " / CT " + v.ct()));
				m.text("§7MP上限 " + b.res("mp_max", 100) + " / MP回復 " + b.res("mp_regen", 1) + "/秒 / 近接ヒットMP " + b.res("mp_on_hit", 3)
						+ " / スタミナ " + b.res("stamina_max", 100) + " (回復 " + b.res("stamina_regen", 10) + "/秒)");
			}
			default -> {
			}
		}
		return m;
	}
}
