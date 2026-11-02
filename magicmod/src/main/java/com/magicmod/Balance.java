package com.magicmod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 数値バランス表(魔法MOD_数値バランス.xlsx → tools/gen_data.py → balance.json)の値。
 * config/magicmod/balance.json があればそちらを優先して読む(jar を作り直さずに調整できる)。
 */
public final class Balance {

	public record RankRow(String name, float coef, float cost, float ct) {
	}

	public record ArchRow(float techPower, float techCt, float staminaMax, float spellPower, float spellCt, float mpMax) {
	}

	public record SpellRow(Element element, int no, String name, String tag, String rank, String status, String special,
			float mp, float ct, boolean damage) {
	}

	public record TechRow(String category, int no, String name, String rank, String status, String special,
			float stamina, float ct, boolean damage) {
	}

	public record FamiliarRow(int no, String name, String role, String special, float hp, float attack, float upkeep) {
	}

	public record RuneRow(int no, String name, String effect, float value, float valuePhysical, float valueMagic, String kind) {
	}

	public record EnemyRow(String tier, Element element, String name, String note, float hp, float defense, Element weak,
			float weakMult) {
	}

	private static Balance instance;

	public final Map<Element, Float> elementBase = new EnumMap<>(Element.class);
	public final Map<String, RankRow> spellRanks = new HashMap<>();
	public final Map<String, RankRow> techRanks = new HashMap<>();
	public final Map<String, Float> categoryBase = new HashMap<>();
	public final Map<String, String> categoryName = new HashMap<>();
	public final Map<Archetype, ArchRow> archetypes = new EnumMap<>(Archetype.class);
	public final Map<String, float[]> enemyScale = new HashMap<>();
	public final Map<String, Float> resources = new HashMap<>();
	public final List<SpellRow> spells = new ArrayList<>();
	public final List<TechRow> techniques = new ArrayList<>();
	public final List<FamiliarRow> familiars = new ArrayList<>();
	public final List<RuneRow> runes = new ArrayList<>();
	public final List<EnemyRow> enemies = new ArrayList<>();

	public static Balance get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Balance load() {
		JsonObject root;
		try {
			Path override = FabricLoader.getInstance().getConfigDir().resolve("magicmod").resolve("balance.json");
			if (Files.isRegularFile(override)) {
				try (Reader r = Files.newBufferedReader(override, StandardCharsets.UTF_8)) {
					root = JsonParser.parseReader(r).getAsJsonObject();
				}
				MagicMod.LOGGER.info("[magicmod] balance.json を {} から読みました", override);
			} else {
				try (InputStream in = Balance.class.getResourceAsStream("/magicmod/balance.json");
						Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
					root = JsonParser.parseReader(r).getAsJsonObject();
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("magicmod: balance.json を読めませんでした", e);
		}
		Balance b = new Balance();
		root.getAsJsonObject("elements").entrySet().forEach(en -> b.elementBase.put(Element.byId(en.getKey()),
				en.getValue().getAsJsonObject().get("base").getAsFloat()));
		root.getAsJsonObject("spell_ranks").entrySet().forEach(en -> {
			JsonObject o = en.getValue().getAsJsonObject();
			b.spellRanks.put(en.getKey(), new RankRow(str(o, "name"), f(o, "coef"), f(o, "mp"), f(o, "ct")));
		});
		root.getAsJsonObject("tech_ranks").entrySet().forEach(en -> {
			JsonObject o = en.getValue().getAsJsonObject();
			b.techRanks.put(en.getKey(), new RankRow(str(o, "name"), f(o, "coef"), f(o, "stamina"), f(o, "ct")));
		});
		root.getAsJsonObject("categories").entrySet().forEach(en -> {
			JsonObject o = en.getValue().getAsJsonObject();
			b.categoryBase.put(en.getKey(), f(o, "base"));
			b.categoryName.put(en.getKey(), str(o, "name"));
		});
		root.getAsJsonObject("archetypes").entrySet().forEach(en -> {
			JsonObject o = en.getValue().getAsJsonObject();
			b.archetypes.put(Archetype.byId(en.getKey()), new ArchRow(f(o, "tech_power"), f(o, "tech_ct"),
					f(o, "stamina_max"), f(o, "spell_power"), f(o, "spell_ct"), f(o, "mp_max")));
		});
		root.getAsJsonObject("enemy_scale").entrySet().forEach(en -> {
			JsonObject o = en.getValue().getAsJsonObject();
			b.enemyScale.put(en.getKey(), new float[] { f(o, "hp_mult"), f(o, "defense") });
		});
		root.getAsJsonObject("resources").entrySet().forEach(en -> b.resources.put(en.getKey(), en.getValue().getAsFloat()));
		for (JsonElement je : root.getAsJsonArray("spells")) {
			JsonObject o = je.getAsJsonObject();
			b.spells.add(new SpellRow(Element.byId(str(o, "element")), o.get("no").getAsInt(), str(o, "name"), str(o, "tag"),
					str(o, "rank"), str(o, "status"), str(o, "special"), f(o, "mp"), f(o, "ct"), o.get("damage").getAsBoolean()));
		}
		for (JsonElement je : root.getAsJsonArray("techniques")) {
			JsonObject o = je.getAsJsonObject();
			b.techniques.add(new TechRow(str(o, "category"), o.get("no").getAsInt(), str(o, "name"), str(o, "rank"),
					str(o, "status"), str(o, "special"), f(o, "stamina"), f(o, "ct"), o.get("damage").getAsBoolean()));
		}
		for (JsonElement je : root.getAsJsonArray("familiars")) {
			JsonObject o = je.getAsJsonObject();
			b.familiars.add(new FamiliarRow(o.get("no").getAsInt(), str(o, "name"), str(o, "role"), str(o, "special"),
					f(o, "hp"), f(o, "attack"), f(o, "upkeep")));
		}
		for (JsonElement je : root.getAsJsonArray("runes")) {
			JsonObject o = je.getAsJsonObject();
			b.runes.add(new RuneRow(o.get("no").getAsInt(), str(o, "name"), str(o, "effect"), f(o, "value"),
					f(o, "value_physical"), f(o, "value_magic"), str(o, "kind")));
		}
		for (JsonElement je : root.getAsJsonArray("enemies")) {
			JsonObject o = je.getAsJsonObject();
			b.enemies.add(new EnemyRow(str(o, "tier"), Element.byId(str(o, "element")), str(o, "name"), str(o, "note"),
					f(o, "hp"), f(o, "defense"), Element.byId(str(o, "weak")), f(o, "weak_mult")));
		}
		return b;
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static float f(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? 0 : e.getAsFloat();
	}

	public float elementBase(Element e) {
		return elementBase.getOrDefault(e, 8f);
	}

	public float res(String key, float def) {
		return resources.getOrDefault(key, def);
	}

	public ArchRow arch(Archetype a) {
		return archetypes.get(a == Archetype.NONE ? Archetype.BALANCED : a);
	}
}
