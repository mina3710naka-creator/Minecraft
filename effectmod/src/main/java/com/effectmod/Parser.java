package com.effectmod;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * 文章 → 効果 の解釈。日本語/英語のキーワードで「いつ(きっかけ)」「何を(効果)」「どれくらい(数字)」を拾う。
 * 例: 「攻撃したとき雷を落とす 3」「歩くと足元に氷ブロックを置く」「30%の確率で相手に毒 8秒」
 */
public final class Parser {

	public enum Trigger {
		HIT("攻撃したとき"), HURT("ダメージを受けたとき"), USE("右クリックしたとき"), KILL("倒したとき"),
		MINE("ブロックを壊したとき"), MOVE("動いている間"), PASSIVE("装備している間");

		public final String label;

		Trigger(String label) {
			this.label = label;
		}
	}

	/** cost = 付与に必要な経験値レベルの基本値。 */
	public enum Action {
		SLASH("斬撃", 3), SHOCKWAVE("衝撃波", 3), EXPLODE("爆発", 4), LIGHTNING("落雷", 5), IGNITE("発火", 2),
		FIREBALL("火球", 3), FREEZE("凍結", 2), HEAL("回復", 2), LIFESTEAL("吸収", 3), DASH("ダッシュ", 2),
		LEAP("大ジャンプ", 2), BLINK("テレポート", 4), KNOCKBACK("吹き飛ばし", 2), POTION("ポーション効果", 2),
		PLACE("ブロック設置", 3);

		public final String label;
		public final int cost;

		Action(String label, int cost) {
			this.label = label;
			this.cost = cost;
		}
	}

	public record Result(Entry entry, String error) {
		public boolean ok() {
			return entry != null;
		}
	}

	private static final Pattern SEC = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:秒|sec(?:ond)?s?|s\\b)");
	private static final Pattern PCT = Pattern.compile("(\\d+)\\s*%");
	private static final Pattern NUM = Pattern.compile("\\d+");
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
	private static final Pattern WORD = Pattern.compile("[a-z][a-z_]{2,}");

	public static final int MAX_TEXT = 120;

	private static final String[][] POTIONS = {
			{"fire_resistance", "火炎耐性", "耐火"}, {"haste", "採掘速度", "ヘイスト"}, {"mining_fatigue", "採掘疲労"},
			{"jump_boost", "跳躍強化", "ジャンプ力", "ジャンプ強化"}, {"water_breathing", "水中呼吸"},
			{"night_vision", "暗視"}, {"poison", "毒"}, {"weakness", "衰弱"}, {"slowness", "鈍足", "スロー"},
			{"blindness", "盲目"}, {"darkness", "暗闇"}, {"nausea", "吐き気"}, {"regeneration", "再生"},
			{"speed", "速度", "スピード", "素早"}, {"resistance", "耐性", "ダメージ軽減"}, {"glowing", "発光"},
			{"invisibility", "透明"}, {"luck", "幸運"}, {"levitation", "浮遊", "浮か"}, {"wither", "ウィザー", "衰え"},
			{"hunger", "空腹"}, {"strength", "筋力", "攻撃力アップ", "パワー"}, {"saturation", "満腹"},
			{"health_boost", "体力増強"}};

	private static final Set<String> HARMFUL = Set.of("poison", "slowness", "weakness", "blindness", "wither", "hunger",
			"levitation", "glowing", "mining_fatigue", "nausea", "darkness", "instant_damage", "unluck", "bad_omen");

	private static final String[][] BLOCKS = {
			{"blue_ice", "青氷"}, {"packed_ice", "氷塊"}, {"ice", "氷"}, {"obsidian", "黒曜石"}, {"cobblestone", "丸石"},
			{"stone_bricks", "石レンガ"}, {"stone", "石"}, {"deepslate", "深層岩"}, {"gravel", "砂利"}, {"sandstone", "砂岩"},
			{"sand", "砂"}, {"dirt", "土"}, {"grass_block", "草"}, {"glass", "ガラス"}, {"snow_block", "雪"},
			{"lava", "溶岩"}, {"water", "水"}, {"cobweb", "蜘蛛の巣", "クモの巣", "くもの巣"}, {"torch", "松明"},
			{"slime_block", "スライム"}, {"honey_block", "ハチミツ", "蜂蜜"}, {"magma_block", "マグマ"},
			{"netherrack", "ネザーラック"}, {"soul_sand", "ソウルサンド"}, {"bricks", "レンガ"}, {"white_wool", "羊毛"},
			{"iron_block", "鉄"}, {"gold_block", "金"}, {"diamond_block", "ダイヤ"}, {"emerald_block", "エメラルド"},
			{"lapis_block", "ラピス"}, {"redstone_block", "レッドストーン"}, {"copper_block", "銅"}, {"moss_block", "苔"},
			{"clay", "粘土"}, {"granite", "花崗岩"}, {"andesite", "安山岩"}, {"diorite", "閃緑岩"},
			{"end_stone", "エンドストーン"}, {"prismarine", "プリズマリン"}, {"sea_lantern", "シーランタン"},
			{"glowstone", "グロウストーン"}, {"oak_planks", "木材", "オーク", "木"}};

	private static final Set<String> FORBIDDEN = Set.of("bedrock", "barrier", "command_block", "chain_command_block",
			"repeating_command_block", "structure_block", "structure_void", "jigsaw", "end_portal", "end_portal_frame",
			"nether_portal", "end_gateway", "light", "test_block", "test_instance_block", "spawner", "trial_spawner",
			"vault", "reinforced_deepslate", "moving_piston", "piston_head");

	private Parser() {
	}

	private static boolean has(String s, String... keys) {
		for (String k : keys) {
			if (s.contains(k)) {
				return true;
			}
		}
		return false;
	}

	private static Result err(String m) {
		return new Result(null, m);
	}

	public static Result parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return err("文章を入力してください");
		}
		String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
		if (s.length() > MAX_TEXT) {
			return err("文章が長すぎます(" + MAX_TEXT + "文字まで)");
		}

		// 数字: 秒数・確率・威力
		int seconds = 0;
		int chance = 100;
		String rest = s;
		Matcher m = SEC.matcher(rest);
		if (m.find()) {
			seconds = clamp((int) Math.round(Double.parseDouble(m.group(1))), 1, 600);
			rest = rest.substring(0, m.start()) + " " + rest.substring(m.end());
		}
		m = PCT.matcher(rest);
		if (m.find()) {
			chance = clamp(Integer.parseInt(m.group(1)), 1, 100);
			rest = rest.substring(0, m.start()) + " " + rest.substring(m.end());
		}
		int power = 1;
		m = NUM.matcher(rest);
		if (m.find()) {
			power = clamp(parseInt(m.group()), 1, 10);
		}

		// ブロックID(minecraft:xxx)の中の文字をキーワードと取り違えないように、IDを除いた文章で判定する
		String full = s;
		s = ID.matcher(s).replaceAll(" ");

		// きっかけ(順番が優先順位)
		Trigger trigger = null;
		if (has(s, "受け", "され", "被弾", "被ダメ", "hurt", "defend", "damaged")) {
			trigger = Trigger.HURT;
		} else if (has(s, "倒", "キル", "撃破", "kill")) {
			trigger = Trigger.KILL;
		} else if (has(s, "掘", "採掘", "破壊", "壊", "mine", "break", "dig")) {
			trigger = Trigger.MINE;
		} else if (has(s, "右クリック", "クリック", "使", "振っ", "振る", "構え", "use", "click")) {
			trigger = Trigger.USE;
		} else if (has(s, "歩", "走", "移動", "動", "walk", "move", "run", "sprint")) {
			trigger = Trigger.MOVE;
		} else if (has(s, "装備", "持って", "持つ", "いる間", "常に", "passive", "hold", "wear")) {
			trigger = Trigger.PASSIVE;
		} else if (has(s, "攻撃", "殴", "当て", "当た", "斬った", "切った", "hit", "attack", "strike")) {
			trigger = Trigger.HIT;
		}

		// 効果
		Action action = null;
		String potion = null;
		boolean placeWord = has(s, "置", "設置", "足場", "橋", "壁", "place", "bridge", "wall", "生成", "生や");
		if (placeWord || (has(s, "ブロック", "block") && trigger != Trigger.MINE)) {
			action = Action.PLACE;
		} else if (has(s, "火球", "ファイアボール", "fireball")) {
			action = Action.FIREBALL;
		} else if (has(s, "雷", "稲妻", "lightning", "thunder")) {
			action = Action.LIGHTNING;
		} else if (has(s, "爆発", "爆破", "explo")) {
			action = Action.EXPLODE;
		} else if (has(s, "衝撃波", "shockwave", "衝撃")) {
			action = Action.SHOCKWAVE;
		} else if (has(s, "斬", "切り裂", "slash")) {
			action = Action.SLASH;
		} else if ((potion = potion(s)) != null) {
			action = Action.POTION;
		} else if (has(s, "凍", "氷結", "freeze", "frost")) {
			action = Action.FREEZE;
		} else if (has(s, "燃", "炎", "火", "ignite", "burn")) {
			action = Action.IGNITE;
		} else if (has(s, "テレポ", "瞬間移動", "ワープ", "warp", "blink", "teleport")) {
			action = Action.BLINK;
		} else if (has(s, "吹き飛", "ノックバック", "knockback", "弾き")) {
			action = Action.KNOCKBACK;
		} else if (has(s, "吸血", "吸収", "ライフスティール", "lifesteal", "drain")) {
			action = Action.LIFESTEAL;
		} else if (has(s, "回復", "ヒール", "治", "heal")) {
			action = Action.HEAL;
		} else if (has(s, "ダッシュ", "突進", "加速", "dash", "rush")) {
			action = Action.DASH;
		} else if (has(s, "ジャンプ", "跳", "飛び", "打ち上げ", "leap", "jump", "launch")) {
			action = Action.LEAP;
		}
		if (action == null) {
			return err("効果が分かりません。斬撃・衝撃波・爆発・雷・火球・発火・凍結・回復・吸血・ダッシュ・ジャンプ・テレポート・吹き飛ばし・ブロックを置く・ポーション効果(毒/速度など)の言葉を入れてください");
		}
		if (trigger == null) {
			trigger = action == Action.PLACE ? Trigger.MOVE : action == Action.POTION && !HARMFUL.contains(potion) ? Trigger.PASSIVE : Trigger.USE;
		}

		boolean target = has(s, "相手", "敵", "対象", "target", "enemy");
		String arg = "";
		if (action == Action.PLACE) {
			String block = block(full);
			if (block == null) {
				return err("置くブロックが分かりません(例: 氷 / 黒曜石 / ガラス / クモの巣 / minecraft:stone)");
			}
			String where = "FEET";
			if (target && trigger != Trigger.MOVE && trigger != Trigger.PASSIVE && trigger != Trigger.USE) {
				where = "TARGET";
			} else if (has(s, "壁", "wall")) {
				where = "WALL";
			} else if (has(s, "周囲", "まわり", "周り", "around", "広く")) {
				where = "AROUND";
			} else if (has(s, "前", "front", "ahead")) {
				where = "FRONT";
			}
			boolean temp = trigger == Trigger.MOVE || where.equals("TARGET") || where.equals("WALL") || where.equals("AROUND")
					|| has(s, "一時", "消え", "temp", "しばらく");
			if (has(s, "永久", "残る", "permanent")) {
				temp = false;
			}
			arg = block + ";" + where + ";" + (temp ? 1 : 0);
		} else if (action == Action.POTION) {
			boolean self = has(s, "自分", "自身", "self");
			boolean tgt = target || (!self && HARMFUL.contains(potion));
			arg = potion + ";" + (tgt ? "target" : "self");
		}
		String text = raw.trim();
		return new Result(new Entry(text, trigger.name(), action.name(), arg, power, seconds, chance), null);
	}

	private static int parseInt(String s) {
		try {
			return Integer.parseInt(s.length() > 4 ? s.substring(0, 4) : s);
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static Identifier id(String s) {
		return s.contains(":") ? Identifier.tryParse(s) : Identifier.tryParse("minecraft:" + s);
	}

	private static String potion(String s) {
		for (String[] row : POTIONS) {
			for (int i = 1; i < row.length; i++) {
				if (s.contains(row[i])) {
					return row[0];
				}
			}
		}
		Matcher m = WORD.matcher(s);
		while (m.find()) {
			Identifier id = id(m.group());
			if (id != null && BuiltInRegistries.MOB_EFFECT.getOptional(id).isPresent()) {
				return id.getPath();
			}
		}
		return null;
	}

	private static String block(String s) {
		Matcher m = ID.matcher(s);
		while (m.find()) {
			String ok = usableBlock(m.group());
			if (ok != null) {
				return ok;
			}
		}
		for (String[] row : BLOCKS) {
			for (int i = 1; i < row.length; i++) {
				if (s.contains(row[i])) {
					String ok = usableBlock(row[0]);
					if (ok != null) {
						return ok;
					}
				}
			}
		}
		m = WORD.matcher(s);
		while (m.find()) {
			String ok = usableBlock(m.group());
			if (ok != null) {
				return ok;
			}
		}
		return null;
	}

	/** 置いてよいブロックなら "minecraft:xxx" を返す(ブロックエンティティ・管理用ブロックは不可)。 */
	private static String usableBlock(String name) {
		Identifier id = id(name);
		if (id == null) {
			return null;
		}
		var opt = BuiltInRegistries.BLOCK.getOptional(id);
		if (opt.isEmpty() || FORBIDDEN.contains(id.getPath())) {
			return null;
		}
		Block b = opt.get();
		if (b.defaultBlockState().isAir() || b.defaultBlockState().hasBlockEntity()) {
			return null;
		}
		return id.toString();
	}

	public static boolean harmful(String effectPath) {
		return HARMFUL.contains(effectPath);
	}

	/** 付与にかかる経験値レベル。 */
	public static int cost(Entry e) {
		return Action.valueOf(e.action()).cost + e.power() / 2;
	}

	/** 表示用の説明。例: 「攻撃したとき → 斬撃 Lv3」 */
	public static String label(Entry e) {
		Trigger t = Trigger.valueOf(e.trigger());
		Action a = Action.valueOf(e.action());
		StringBuilder sb = new StringBuilder(t.label).append(" → ").append(a.label);
		String[] arg = e.arg().split(";");
		if (a == Action.PLACE && arg.length > 0) {
			sb.append("「").append(arg[0].replace("minecraft:", "")).append("」");
		} else if (a == Action.POTION && arg.length > 0) {
			sb.append("「").append(arg[0]).append("」");
		}
		sb.append(" Lv").append(e.power());
		if (e.seconds() > 0) {
			sb.append(" ").append(e.seconds()).append("秒");
		}
		if (e.chance() < 100) {
			sb.append(" ").append(e.chance()).append("%");
		}
		return sb.toString();
	}

	public static List<String> labels(List<Entry> list) {
		List<String> out = new ArrayList<>();
		for (Entry e : list) {
			out.add(label(e));
		}
		return out;
	}
}
