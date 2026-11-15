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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 文章 → 効果 の解釈。日本語/英語のキーワードで「いつ(きっかけ)」「何を(効果)」「どれくらい(数字)」を拾う。
 * 例: 「攻撃したとき雷を落とす 3」「歩くと足元に氷ブロックを置く」「30%の確率で相手に毒 8秒」
 */
public final class Parser {

	public enum Trigger {
		HIT("攻撃したとき"), HURT("ダメージを受けたとき"), USE("右クリックしたとき"), KILL("倒したとき"),
		MINE("ブロックを壊したとき"), MOVE("動いている間"), PASSIVE("装備している間"), SNEAK("しゃがんだとき"),
		LAND("着地したとき"), JUMP("ジャンプしたとき");

		public final String label;

		Trigger(String label) {
			this.label = label;
		}
	}

	/** cost = 付与に必要な経験値レベルの基本値。cats が空でなければ、その種類のアイテム専用。def = きっかけを書かなかったときの既定。 */
	public enum Action {
		// ---- どのアイテムにも付けられる
		SLASH("斬撃", 3), SHOCKWAVE("衝撃波", 3), EXPLODE("爆発", 4), LIGHTNING("落雷", 5), IGNITE("発火", 2),
		FIREBALL("火球", 3), FREEZE("凍結", 2), HEAL("回復", 2), LIFESTEAL("吸収", 3), DASH("ダッシュ", 2),
		LEAP("大ジャンプ", 2), BLINK("テレポート", 4), KNOCKBACK("吹き飛ばし", 2), POTION("ポーション効果", 2),
		PLACE("ブロック設置", 3),
		METEOR("隕石", 5, Trigger.USE), TORNADO("竜巻", 4, Trigger.USE), VACUUM("引き寄せ", 3, Trigger.USE),
		CHAIN("連鎖雷", 5, Trigger.HIT), COUNTER("反撃", 3, Trigger.HURT), BARRIER("バリア", 3, Trigger.USE),
		INVULN("無敵", 6, Trigger.USE), TIMESTOP("時間停止", 6, Trigger.USE), XP("経験値ボーナス", 2, Trigger.KILL),
		LOOT("ドロップ追加", 3, Trigger.KILL), FEED("満腹回復", 2, Trigger.USE), CLEANSE("浄化", 3, Trigger.USE),
		FLAME_CONE("火炎放射", 4, Trigger.USE), BLIZZARD("吹雪", 4, Trigger.USE), POISON_CLOUD("毒霧", 3, Trigger.USE),
		HEAL_AURA("癒しの光", 3, Trigger.USE), SCOUT("索敵", 2, Trigger.USE), BACKSTAB("背後テレポート", 4, Trigger.HIT),
		STUN("スタン", 3, Trigger.HIT), EXECUTE("処刑", 5, Trigger.HIT), SELF_REPAIR("自己修復", 3, Trigger.PASSIVE),
		MAGNET("回収", 2, Trigger.PASSIVE), BONUS_DAMAGE("追加ダメージ", 2, Trigger.HIT), BONEMEAL("成長", 2, Trigger.USE),
		FIREWORK("花火", 3, Trigger.USE), EARTHQUAKE("地震", 4, Trigger.USE), DRAIN_AURA("ドレイン", 4, Trigger.USE),
		ALLY_BUFF("仲間強化", 3, Trigger.USE), SUMMON_WOLF("狼召喚", 4, Trigger.USE), SUMMON_GOLEM("ゴーレム召喚", 6, Trigger.USE),
		BERSERK("狂戦士", 4, Trigger.HURT), FIRE_TRAIL("炎の足跡", 3, Trigger.MOVE), INTIMIDATE("威圧", 3, Trigger.PASSIVE),
		ORE_SENSE("鉱石探知", 3, Trigger.USE),
		// ---- 専用(2つずつ)
		IAI("居合", 4, Trigger.USE, ItemCat.SWORD), WHIRLWIND("旋風斬", 4, Trigger.USE, ItemCat.SWORD),
		CLEAVE("地割れ", 4, Trigger.USE, ItemCat.AXE), TIMBER("伐採", 3, Trigger.MINE, ItemCat.AXE),
		VEIN("鉱脈採掘", 4, Trigger.MINE, ItemCat.PICKAXE), BIGMINE("広範囲採掘", 4, Trigger.MINE, ItemCat.PICKAXE),
		SANDSTORM("砂嵐", 3, Trigger.USE, ItemCat.SHOVEL), PITFALL("落とし穴", 3, Trigger.HIT, ItemCat.SHOVEL),
		HARVEST("豊作", 3, Trigger.USE, ItemCat.HOE), REAPER("大鎌", 4, Trigger.USE, ItemCat.HOE),
		MULTISHOT("連射", 4, Trigger.USE, ItemCat.BOW), ARROW_RAIN("矢の雨", 5, Trigger.USE, ItemCat.BOW, ItemCat.CROSSBOW),
		SHOTGUN("散弾", 4, Trigger.USE, ItemCat.CROSSBOW), RAILGUN("貫通弾", 5, Trigger.USE, ItemCat.CROSSBOW),
		THUNDER_SPEAR("雷神の槍", 5, Trigger.USE, ItemCat.TRIDENT), WHIRLPOOL("渦潮", 4, Trigger.USE, ItemCat.TRIDENT),
		GROUND_SLAM("叩きつけ", 4, Trigger.USE, ItemCat.MACE), GRAVITY_WELL("重力井戸", 5, Trigger.USE, ItemCat.MACE),
		REFLECT("反射", 4, Trigger.HURT, ItemCat.SHIELD), SANCTUARY("聖域", 5, Trigger.HURT, ItemCat.SHIELD),
		CLAIRVOYANCE("千里眼", 3, Trigger.PASSIVE, ItemCat.HELMET), EVADE("見切り", 4, Trigger.HURT, ItemCat.HELMET),
		ENDURE("不屈", 4, Trigger.HURT, ItemCat.CHESTPLATE), THORNS("棘の鎧", 3, Trigger.HURT, ItemCat.CHESTPLATE),
		STEALTH("隠密", 3, Trigger.SNEAK, ItemCat.LEGGINGS), SPRINT_BURST("韋駄天", 3, Trigger.MOVE, ItemCat.LEGGINGS),
		WATER_WALK("水面歩行", 3, Trigger.MOVE, ItemCat.BOOTS), LAND_QUAKE("着地衝撃", 4, Trigger.LAND, ItemCat.BOOTS),
		TURBO("ターボ飛行", 4, Trigger.PASSIVE, ItemCat.ELYTRA), SONIC("衝撃飛行", 4, Trigger.MOVE, ItemCat.ELYTRA),
		HOOK_PULL("フック", 3, Trigger.USE, ItemCat.FISHING_ROD), FISH_LOOT("大漁", 3, Trigger.USE, ItemCat.FISHING_ROD),
		BLOCK_PULL("ブロック引き寄せ", 3, Trigger.USE, ItemCat.FISHING_ROD),
		SHEAR_AREA("一斉刈り", 2, Trigger.USE, ItemCat.SHEARS), LEAF_CUT("剪定", 2, Trigger.MINE, ItemCat.SHEARS),
		TILL_AREA("耕地化", 2, Trigger.USE, ItemCat.HOE), PATH("道づくり", 2, Trigger.USE, ItemCat.SHOVEL);

		public final String label;
		public final int cost;
		public final Trigger def;
		public final java.util.Set<ItemCat> cats;

		Action(String label, int cost) {
			this(label, cost, null);
		}

		Action(String label, int cost, Trigger def, ItemCat... cats) {
			this.label = label;
			this.cost = cost;
			this.def = def;
			this.cats = cats.length == 0 ? java.util.Set.of() : java.util.EnumSet.copyOf(java.util.List.of(cats));
		}

		public boolean usableOn(ItemStack s) {
			if (cats.isEmpty() || s == null || s.isEmpty()) {
				return true;
			}
			for (ItemCat c : cats) {
				if (c.matches(s)) {
					return true;
				}
			}
			return false;
		}
	}

	private record Rule(Action action, String[] keys) {
	}

	private static Rule rule(Action a, String... keys) {
		return new Rule(a, keys);
	}

	/** ポーションより先に調べるキーワード(長い言葉・専用の効果が先)。 */
	private static final List<Rule> PRE = List.of(
			rule(Action.FIREWORK, "花火", "firework"), rule(Action.EARTHQUAKE, "地震", "アースクエイク", "earthquake"),
			rule(Action.DRAIN_AURA, "ドレイン", "吸魂", "lifedrain"),
			rule(Action.ALLY_BUFF, "仲間強化", "味方強化", "仲間", "バフ", "allybuff"),
			rule(Action.SUMMON_WOLF, "狼", "オオカミ", "wolf"), rule(Action.SUMMON_GOLEM, "ゴーレム", "golem"),
			rule(Action.BERSERK, "狂戦士", "バーサーク", "berserk"), rule(Action.FIRE_TRAIL, "炎の足跡", "火の足跡", "firetrail"),
			rule(Action.INTIMIDATE, "威圧", "intimidate"), rule(Action.ORE_SENSE, "鉱石探知", "鉱石探索", "oresense"),
			rule(Action.TILL_AREA, "耕地化", "耕す", "tillarea"), rule(Action.PATH, "道づくり", "道を作", "dirtpath"),
			rule(Action.IAI, "居合", "iai"), rule(Action.WHIRLWIND, "旋風", "回転斬", "whirlwind"),
			rule(Action.CLEAVE, "地割れ", "cleave"), rule(Action.TIMBER, "伐採", "木こり", "timber"),
			rule(Action.VEIN, "鉱脈", "一括採掘", "vein"), rule(Action.BIGMINE, "広範囲採掘", "範囲採掘", "3x3", "bigmine"),
			rule(Action.SANDSTORM, "砂嵐", "sandstorm"), rule(Action.PITFALL, "落とし穴", "pitfall"),
			rule(Action.HARVEST, "豊作", "一括収穫", "harvest"), rule(Action.REAPER, "大鎌", "reaper"),
			rule(Action.MULTISHOT, "連射", "multishot"), rule(Action.ARROW_RAIN, "矢の雨", "arrow rain", "arrowrain"),
			rule(Action.SHOTGUN, "散弾", "shotgun"), rule(Action.RAILGUN, "貫通弾", "レールガン", "railgun"),
			rule(Action.THUNDER_SPEAR, "雷神", "雷槍", "thunderspear"), rule(Action.WHIRLPOOL, "渦潮", "渦", "whirlpool"),
			rule(Action.GROUND_SLAM, "叩きつけ", "叩き付け", "スラム", "slam"),
			rule(Action.GRAVITY_WELL, "重力", "グラビティ", "ブラックホール", "gravity"),
			rule(Action.REFLECT, "反射", "reflect"), rule(Action.SANCTUARY, "聖域", "sanctuary"),
			rule(Action.CLAIRVOYANCE, "千里眼", "clairvoyance"), rule(Action.EVADE, "見切り", "evade"),
			rule(Action.ENDURE, "不屈", "endure"), rule(Action.THORNS, "棘", "thorns"),
			rule(Action.STEALTH, "隠密", "ステルス", "stealth"), rule(Action.SPRINT_BURST, "韋駄天", "sprintburst"),
			rule(Action.WATER_WALK, "水面歩行", "水上歩行", "waterwalk"),
			rule(Action.LAND_QUAKE, "着地衝撃", "着地で衝撃", "landquake"),
			rule(Action.TURBO, "ターボ", "滑空加速", "turbo"), rule(Action.SONIC, "衝撃飛行", "ソニック", "sonic"),
			rule(Action.HOOK_PULL, "フック", "グラップル", "hook"), rule(Action.FISH_LOOT, "大漁", "釣果", "fishloot"),
			rule(Action.SHEAR_AREA, "一斉刈り", "羊毛刈り", "shear"), rule(Action.LEAF_CUT, "剪定", "葉を刈", "leafcut"),
			rule(Action.METEOR, "隕石", "メテオ", "meteor"), rule(Action.TORNADO, "竜巻", "トルネード", "tornado"),
			rule(Action.VACUUM, "引き寄", "引き込", "吸い寄", "vacuum"),
			rule(Action.CHAIN, "連鎖", "チェイン", "chain"), rule(Action.COUNTER, "反撃", "カウンター", "counter"),
			rule(Action.BARRIER, "バリア", "障壁", "barrier"), rule(Action.INVULN, "無敵", "invincible"),
			rule(Action.TIMESTOP, "時間停止", "時止め", "タイムストップ", "timestop"),
			rule(Action.XP, "経験値", "経験", "experience"), rule(Action.LOOT, "ドロップ", "財宝", "loot"),
			rule(Action.FEED, "満腹回復", "満腹度", "空腹回復", "お腹", "feed"), rule(Action.CLEANSE, "浄化", "解毒", "cleanse", "purify"),
			rule(Action.FLAME_CONE, "火炎放射", "フレイムスロワー", "flamethrower"),
			rule(Action.BLIZZARD, "吹雪", "ブリザード", "blizzard"),
			rule(Action.POISON_CLOUD, "毒霧", "毒ガス", "毒の雲", "poisoncloud"),
			rule(Action.HEAL_AURA, "癒し", "オーラ", "aura"), rule(Action.SCOUT, "索敵", "探知", "スカウト", "scout", "detect"),
			rule(Action.BACKSTAB, "背後", "バックスタブ", "backstab"), rule(Action.STUN, "スタン", "気絶", "麻痺", "stun"),
			rule(Action.EXECUTE, "処刑", "とどめ", "execute"), rule(Action.SELF_REPAIR, "修復", "repair", "mend"),
			rule(Action.MAGNET, "回収", "マグネット", "磁石", "magnet"),
			rule(Action.BONUS_DAMAGE, "追加ダメージ", "追撃", "真ダメージ", "bonusdamage"),
			rule(Action.BONEMEAL, "骨粉", "成長", "bonemeal", "grow"),
			rule(Action.FIREBALL, "火球", "ファイアボール", "fireball"), rule(Action.LIGHTNING, "雷", "稲妻", "lightning", "thunder"),
			rule(Action.EXPLODE, "爆発", "爆破", "explo"), rule(Action.SHOCKWAVE, "衝撃波", "shockwave", "衝撃"),
			rule(Action.SLASH, "斬", "切り裂", "slash"));

	/** ポーションより後に調べるキーワード。 */
	private static final List<Rule> POST = List.of(
			rule(Action.FREEZE, "凍", "氷結", "freeze", "frost"), rule(Action.IGNITE, "燃", "炎", "火", "ignite", "burn"),
			rule(Action.BLINK, "テレポ", "瞬間移動", "ワープ", "warp", "blink", "teleport"),
			rule(Action.KNOCKBACK, "吹き飛", "ノックバック", "knockback", "弾き"),
			rule(Action.LIFESTEAL, "吸血", "吸収", "ライフスティール", "lifesteal", "drain"),
			rule(Action.HEAL, "回復", "ヒール", "治", "heal"), rule(Action.DASH, "ダッシュ", "突進", "加速", "dash", "rush"),
			rule(Action.LEAP, "ジャンプ", "跳", "飛び", "打ち上げ", "leap", "jump", "launch"));

	private static Action find(List<Rule> rules, String s) {
		for (Rule r : rules) {
			if (has(s, r.keys())) {
				return r.action();
			}
		}
		return null;
	}

	public record Result(Entry entry, String error) {
		public boolean ok() {
			return entry != null;
		}
	}

	private static final Pattern SEC = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:秒|sec(?:ond)?s?|s\\b)");
	private static final Pattern CD = Pattern.compile("(\\d+)\\s*秒(?:ごと|に1回|に一回|おき|毎)|クールダウン\\s*(\\d+)\\s*秒?|cooldown\\s*(\\d+)");
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
			{"health_boost", "体力増強"},
			{"slow_falling", "落下速度低下", "ふわふわ", "スローフォール"}, {"conduit_power", "コンジット"},
			{"dolphins_grace", "イルカ"}};

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
		return parse(raw, null);
	}

	/** item が空でなければ、その種類専用の効果かどうかも確かめる。 */
	public static Result parse(String raw, ItemStack item) {
		if (raw == null || raw.isBlank()) {
			return err("文章を入力してください");
		}
		String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
		if (s.length() > MAX_TEXT) {
			return err("文章が長すぎます(" + MAX_TEXT + "文字まで)");
		}

		// 登録した言葉(config/effectmod_words.txt)を元の文章に置きかえる
		String expanded = Macros.expand(raw);
		if (!expanded.equals(raw)) {
			s = Normalizer.normalize(expanded, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
			raw = expanded;
		}

		// 数字: クールダウン・秒数・確率・威力
		int seconds = 0;
		int chance = 100;
		int cd = 0;
		String rest = s;
		Matcher cm = CD.matcher(rest);
		if (cm.find()) {
			String g = cm.group(1) != null ? cm.group(1) : cm.group(2) != null ? cm.group(2) : cm.group(3);
			cd = clamp(parseInt(g), 1, 600) * 20;
			rest = rest.substring(0, cm.start()) + " " + rest.substring(cm.end());
		}
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
		} else if (has(s, "しゃがみ", "スニーク", "sneak", "crouch")) {
			trigger = Trigger.SNEAK;
		} else if (has(s, "着地", "landing")) {
			trigger = Trigger.LAND;
		} else if (has(s, "ジャンプしたとき", "ジャンプした時", "ジャンプすると", "ジャンプ時", "ジャンプで", "跳んだとき", "on jump", "when jump")) {
			trigger = Trigger.JUMP;
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
		if (has(s, "ブロック", "block") && has(s, "引き寄", "引っ張", "引き", "pull")) {
			action = Action.BLOCK_PULL;
		} else if (placeWord || (has(s, "ブロック", "block") && trigger != Trigger.MINE)) {
			action = Action.PLACE;
		} else if ((action = find(PRE, s)) != null) {
			// 見つかった
		} else if ((potion = potion(s)) != null) {
			action = Action.POTION;
		} else {
			action = find(POST, s);
		}
		if (action == null) {
			return err("効果が分かりません。斬撃・衝撃波・爆発・雷・火球・発火・凍結・回復・吸血・ダッシュ・ジャンプ・テレポート・吹き飛ばし・ブロックを置く・ポーション効果(毒/速度など)の言葉を入れてください");
		}
		if (!action.usableOn(item)) {
			StringBuilder who = new StringBuilder();
			for (ItemCat c : action.cats) {
				who.append(who.length() > 0 ? "・" : "").append(c.label);
			}
			return err("「" + action.label + "」は " + who + " 専用です");
		}
		if (trigger == null) {
			trigger = action.def != null ? action.def
					: action == Action.PLACE ? Trigger.MOVE
							: action == Action.POTION && !HARMFUL.contains(potion) ? Trigger.PASSIVE : Trigger.USE;
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
		return new Result(new Entry(text, trigger.name(), action.name(), arg, power, seconds, chance, cd), null);
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
		if (e.cd() > 0) {
			sb.append(" ").append(e.cd() / 20).append("秒ごと");
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
