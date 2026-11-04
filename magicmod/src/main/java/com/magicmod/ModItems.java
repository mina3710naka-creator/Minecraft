package com.magicmod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

/**
 * アイテム登録。item("id", "日本語名", "見た目の種類", 作り方) の形で書く。
 * tools/gen_assets.py はこのファイルを読んで、テクスチャ・モデル・言語ファイルを作る。
 */
public final class ModItems {

	public static final Map<String, Item> ALL = new LinkedHashMap<>();
	public static final Map<String, String> NAMES = new LinkedHashMap<>();

	private static final Map<Element, Item> CRYSTALS = new EnumMap<>(Element.class);
	private static final Map<Element, Item> ESSENCES = new EnumMap<>(Element.class);
	private static final Map<Element, Item> INGOTS = new EnumMap<>(Element.class);
	private static final Map<Element, Item> CORES = new EnumMap<>(Element.class);
	private static final Map<Element, Item> BOOKS = new EnumMap<>(Element.class);
	private static final Map<Element, Item> MINION_DROPS = new EnumMap<>(Element.class);
	private static final Map<Element, Item> MIDBOSS_DROPS = new EnumMap<>(Element.class);
	public static final List<Item> WANDS = new ArrayList<>();
	public static final Map<Runes, Item> RUNES = new EnumMap<>(Runes.class);
	public static final Map<String, Item> CONTRACTS = new LinkedHashMap<>();
	public static final Map<Gear.Kind, Item> GEAR = new EnumMap<>(Gear.Kind.class);
	public static final Map<String, Item> WEAPONS = new LinkedHashMap<>();

	// ---- 基本素材 ----
	public static final Item CRYSTAL_SHARD = item("crystal_shard", "水晶欠片", "shard", Item::new);
	public static final Item RAW_SILVER = item("raw_silver", "銀の原石", "raw:c8ccd8", Item::new);
	public static final Item SILVER_INGOT = item("silver_ingot", "銀インゴット", "ingot:d8dce8", Item::new);
	public static final Item MAGISTEEL_INGOT = item("magisteel_ingot", "魔鋼インゴット", "ingot:5a6ad8", Item::new);
	public static final Item DRAGON_BONE = item("dragon_bone", "ドラゴンの骨", "bone", p -> new Item(p.rarity(Rarity.RARE)));
	public static final Item STARDUST_SHARD = item("stardust_shard", "星屑の欠片", "star", p -> new Item(p.rarity(Rarity.EPIC)));
	public static final Item RUNE_STONE = item("rune_stone", "刻印石", "runestone", Item::new);
	public static final Item SYNTH_CATALYST = item("synth_catalyst", "合成触媒", "catalyst", Item::new);
	public static final Item PURIFYING_WATER = item("purifying_water", "解呪の聖水", "potion:fff6c8", PurifyItem::new);
	public static final Item REBIRTH_ORB = item("rebirth_orb", "転身の宝珠", "orb", p -> new Item(p.rarity(Rarity.EPIC).stacksTo(16)));
	public static final Item MAGIC_GUIDE = item("magic_guide", "魔法の手引き", "guide", GuideItem::new);
	public static final Item OMNIS_HEART = item("omnis_heart", "魔王の心臓", "core:ff40a0", p -> new Item(p.rarity(Rarity.EPIC).fireResistant().stacksTo(16)));
	public static final Item SYNTH_SCROLL = item("synth_scroll", "合成魔法の巻物", "scroll", SynthScrollItem::new);

	static {
		String[] crystalNames = { "風の結晶", "雷石", "火の結晶", "水の結晶", "重力の結晶", "闇の結晶", "光の結晶", "呪いの結晶" };
		String[] coreNames = { "暴風の核石", "雷光の核石", "業火の核石", "深海の核石", "重力の核石", "虚無の核石", "聖光の核石", "災厄の核石" };
		String[][] minion = { { "wind_feather", "風の羽根片" }, { "charged_fang", "帯電の牙" }, { "lava_fang", "溶岩の牙" },
				{ "slime_shard", "粘液の欠片" }, { "void_shard", "虚空の欠片" }, { "night_powder", "夜の鱗粉" },
				{ "holy_shard", "聖なる欠片" }, { "ominous_powder", "不吉な鱗粉" } };
		String[][] mid = { { "gale_feather", "疾風の羽根" }, { "charged_crystal", "帯電結晶" }, { "scorching_scale", "灼熱の鱗" },
				{ "abyssal_scale", "深海の鱗" }, { "compressed_stardust", "圧縮された星屑" }, { "cursed_shadow_shard", "呪われた影の欠片" },
				{ "holy_gem", "聖なる輝石" }, { "greater_curse_crystal", "呪いの結晶(上位)" } };
		String[] minionStyle = { "feather", "fang", "fang", "blob", "shard", "powder", "shard", "powder" };
		String[] midStyle = { "feather", "gem", "scale", "scale", "star", "shard", "gem", "gem" };
		for (Element e : Element.values()) {
			int i = e.ordinal();
			String hex = String.format("%06x", e.color);
			CRYSTALS.put(e, item(e.id + "_crystal", crystalNames[i], "gem:" + hex, Item::new));
			ESSENCES.put(e, item(e.id + "_essence", e.ja + "のエッセンス", "essence:" + hex, Item::new));
			INGOTS.put(e, item(e.id + "_ingot", e.ja + "の属性インゴット", "ingot:" + hex, Item::new));
			MINION_DROPS.put(e, item(minion[i][0], minion[i][1], minionStyle[i] + ":" + hex, Item::new));
			MIDBOSS_DROPS.put(e, item(mid[i][0], mid[i][1], midStyle[i] + ":" + hex, p -> new Item(p.rarity(Rarity.UNCOMMON))));
			CORES.put(e, item(e.id + "_core", coreNames[i], "core:" + hex, p -> new Item(p.rarity(Rarity.EPIC).fireResistant())));
			BOOKS.put(e, item(e.id + "_book", e.ja + "の魔法書", "book:" + hex, p -> new BookItem(p.stacksTo(1), e)));
		}
		String[][] wands = { { "wood_wand", "木の杖" }, { "silver_wand", "銀の杖" }, { "magisteel_wand", "魔鋼の杖" },
				{ "dragonbone_wand", "竜骨の杖" }, { "stardust_wand", "星屑の杖" } };
		for (int i = 0; i < wands.length; i++) {
			final int tier = i + 1;
			Rarity r = tier >= 5 ? Rarity.EPIC : tier >= 4 ? Rarity.RARE : tier >= 3 ? Rarity.UNCOMMON : Rarity.COMMON;
			WANDS.add(item(wands[i][0], wands[i][1], "wand:" + tier, p -> new WandItem(p.stacksTo(1).rarity(r), tier)));
		}
		for (Runes r : Runes.values()) {
			RUNES.put(r, item("rune_" + r.id, r.ja(), "rune:" + r.id, p -> new RuneItem(p.stacksTo(1), r)));
		}
		for (Familiars.Kind k : Familiars.Kind.values()) {
			CONTRACTS.put(k.id, item("contract_" + k.id, "使い魔契約書(" + k.ja() + ")", "contract:" + k.id,
					p -> new ContractItem(p.stacksTo(1).rarity(k == Familiars.Kind.ANCIENT_DRAGON ? Rarity.EPIC : Rarity.UNCOMMON), k)));
		}
		for (Gear.Kind g : Gear.Kind.values()) {
			GEAR.put(g, item(g.id, g.ja, "gear:" + g.slot.name().toLowerCase() + ":" + g.id,
					p -> new GearItem(p.stacksTo(1).rarity(g.only != null ? Rarity.RARE : Rarity.UNCOMMON), g)));
		}
		weapon("long_sword", "ロングソード", "blade", 3.5f, -2.4f);
		weapon("war_hammer", "ウォーハンマー", "blade", 5f, -3.0f);
		weapon("naginata", "薙刀", "polearm", 4f, -2.8f);
		weapon("twin_blades", "双剣", "dual", 2f, -1.6f);
		weapon("claws", "クロー", "dual", 1.5f, -1.4f);
		weapon("flail", "フレイル", "swing", 4.5f, -2.9f);
		weapon("kusarigama", "クサリ鎌", "swing", 3.5f, -2.6f);
		weapon("nunchaku", "ヌンチャク", "swing", 2.5f, -2.0f);
		weapon("javelin", "投げ槍", "throw", 3f, -2.6f);
		weapon("boomerang", "ブーメラン", "throw", 1.5f, -2.0f);
		weapon("greatsword", "大剣", "heavy", 7f, -3.2f);
		weapon("great_hammer", "戦槌", "heavy", 8f, -3.4f);
		WEAPONS.put("throwing_knife", item("throwing_knife", "投げナイフ", "weapon:throwing_knife",
				p -> new WeaponItem(p.stacksTo(16), "throw", true)));
		WEAPONS.put("shuriken", item("shuriken", "手裏剣", "weapon:shuriken", p -> new WeaponItem(p.stacksTo(16), "throw", true)));
	}

	static {
		FxDisplay.registerItems();
		// 魔法・物理わざのアイコン(画面・HUD・ホイールで ItemStack として描く。クリエイティブタブには出さない)
		for (Element e : Element.values()) {
			for (int no = 1; no <= 13; no++) {
				item("icon_" + e.id + "_" + no, "魔法アイコン", "icon", Item::new);
			}
		}
		for (String cat : Techniques.CATEGORIES) {
			item("icon_tech_" + cat, "物理わざ", "icon", Item::new);
		}
	}

	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, "magicmod"));

	private ModItems() {
	}

	/** 武器カテゴリごとの振りの型と長さ(槍は突き、双剣は素早く、大型は大きくゆっくり)。 */
	static net.minecraft.world.item.component.SwingAnimation swingOf(String cat) {
		var t = net.minecraft.world.item.SwingAnimationType.WHACK;
		return switch (cat) {
			case "polearm" -> new net.minecraft.world.item.component.SwingAnimation(net.minecraft.world.item.SwingAnimationType.STAB, 9);
			case "dual" -> new net.minecraft.world.item.component.SwingAnimation(t, 5);
			case "swing" -> new net.minecraft.world.item.component.SwingAnimation(t, 11);
			case "heavy" -> new net.minecraft.world.item.component.SwingAnimation(t, 15);
			case "blade" -> new net.minecraft.world.item.component.SwingAnimation(t, 7);
			default -> net.minecraft.world.item.component.SwingAnimation.DEFAULT;
		};
	}

	private static void weapon(String id, String ja, String cat, float dmg, float speed) {
		WEAPONS.put(id, item(id, ja, "weapon:" + id,
				p -> new WeaponItem(p.sword(ToolMaterial.IRON, dmg, speed).component(net.minecraft.core.component.DataComponents.ATTACK_ANIMATION,
						swingOf(cat)), cat, cat.equals("throw"))));
	}

	public static Item item(String id, String ja, String style, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, id));
		Item item = factory.apply(new Item.Properties().setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		ALL.put(id, item);
		NAMES.put(id, ja);
		return item;
	}

	/** 魔法のアイコン(合成魔法はベースの魔法の絵)。 */
	public static Item icon(Element e, int no) {
		Item it = ALL.get("icon_" + e.id + "_" + Math.max(1, Math.min(13, no)));
		return it == null ? crystal(e) : it;
	}

	public static Item techIcon(String cat) {
		Item it = ALL.get("icon_tech_" + cat);
		return it == null ? CRYSTAL_SHARD : it;
	}

	public static Item crystal(Element e) {
		return e == null ? CRYSTAL_SHARD : CRYSTALS.get(e);
	}

	public static Item essence(Element e) {
		return ESSENCES.get(e);
	}

	public static Item ingot(Element e) {
		return INGOTS.get(e);
	}

	public static Item core(Element e) {
		return CORES.get(e);
	}

	public static Item book(Element e) {
		return BOOKS.get(e);
	}

	public static Item minionDrop(Element e) {
		return MINION_DROPS.get(e);
	}

	public static Item midbossDrop(Element e) {
		return MIDBOSS_DROPS.get(e);
	}

	/** 物理わざの投擲で飛ばす見た目。 */
	public static Item weaponVisual(LivingEntity caster) {
		ItemStack s = caster.getMainHandItem();
		if (s.isEmpty() || s.is(Items.BOW) || s.is(Items.CROSSBOW)) {
			return Items.ARROW;
		}
		return s.getItem();
	}

	/** 武器カテゴリ(設計書 12.2)。素手・対象外は null。 */
	public static String category(ItemStack s) {
		if (s.isEmpty()) {
			return null;
		}
		if (s.getItem() instanceof WeaponItem w) {
			return w.category;
		}
		if (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE)) {
			return "blade";
		}
		if (s.is(ItemTags.SPEARS) || s.is(Items.TRIDENT)) {
			return "polearm";
		}
		if (s.is(Items.BOW) || s.is(Items.CROSSBOW)) {
			return "bow";
		}
		return null;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(WANDS.get(2)))
				.title(Component.literal("魔法MOD"))
				.displayItems((context, output) -> {
					for (Item b : ModBlocks.ITEMS.values()) {
						output.accept(b);
					}
					ALL.forEach((id, i) -> {
						if (!id.startsWith("fx_") && !id.startsWith("icon_")) {
							output.accept(i);
						}
					});
				})
				.build());
	}
}
