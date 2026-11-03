package com.magicmod;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 遊び方の設定(config/magicmod/settings.json)。無ければ既定値で作る。/magic settings reload で読み直す。
 * 数値バランス(balance.json)とは別に、難易度や好みで変えたいものをここに置く。
 */
public final class Settings {

	/** easy / normal / hard。敵の HP と与ダメージにかかる。 */
	public String difficulty = "normal";
	/** 中ボスの HP 倍率(表の HP に掛ける)。 */
	public double midbossHpMultiplier = 1.0;
	/** 属性超越ボスの HP 倍率。表どおりだと 1 戦 10 分前後なので、短くしたいときは 0.5 など。 */
	public double bossHpMultiplier = 1.0;
	/** 敵の魔法・攻撃の強さ。 */
	public double enemyDamageMultiplier = 1.0;
	/** プレイヤーの魔法・わざの強さ。 */
	public double playerDamageMultiplier = 1.0;
	/** プレイヤー同士で魔法・わざが当たるか(サーバーの pvp 設定も必要)。 */
	public boolean pvp = false;
	/** 魔法が地形を変える(火を消す・溶岩を固める・水を凍らせる・雪を溶かす)。 */
	public boolean terrainEffects = true;
	/** 炎の大技で地面に火がつく(mobGriefing が有効なときだけ)。 */
	public boolean fireIgnitesGround = true;
	/** 新しく入ったプレイヤーのダメージ数値の表示(各自ステータス画面で切り替え可)。 */
	public boolean damageNumbersDefault = true;
	/** 雑魚敵が自然に湧く重み(0 で湧かない)。 */
	public int minionSpawnWeight = 4;
	/** 予兆(危険範囲の表示)が出てから攻撃が来るまでの時間の倍率(大きいほど避けやすい)。 */
	public double telegraphTimeMultiplier = 1.0;

	private static Settings instance;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Settings get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("magicmod").resolve("settings.json");
	}

	public static Settings load() {
		Path p = path();
		Settings s = null;
		try {
			if (Files.isRegularFile(p)) {
				try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
					s = GSON.fromJson(r, Settings.class);
				}
			}
		} catch (Exception e) {
			MagicMod.LOGGER.warn("[magicmod] settings.json を読めませんでした。既定値を使います", e);
		}
		if (s == null) {
			s = new Settings();
		}
		try {
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				GSON.toJson(s, w);
			}
		} catch (Exception e) {
			MagicMod.LOGGER.warn("[magicmod] settings.json を書けませんでした", e);
		}
		instance = s;
		return s;
	}

	/** 難易度による敵の HP 倍率。 */
	public double difficultyHp() {
		return switch (difficulty) {
			case "easy" -> 0.7;
			case "hard" -> 1.4;
			default -> 1.0;
		};
	}

	/** 難易度による敵の与ダメージ倍率。 */
	public double difficultyDamage() {
		return switch (difficulty) {
			case "easy" -> 0.7;
			case "hard" -> 1.35;
			default -> 1.0;
		};
	}

	public String summary() {
		return "難易度 " + difficulty + " / 中ボスHP ×" + midbossHpMultiplier + " / ボスHP ×" + bossHpMultiplier + " / 敵の攻撃 ×" + enemyDamageMultiplier
				+ " / 自分の攻撃 ×" + playerDamageMultiplier + " / PvP " + (pvp ? "ON" : "OFF") + " / 地形変化 " + (terrainEffects ? "ON" : "OFF")
				+ " / 予兆の時間 ×" + telegraphTimeMultiplier;
	}
}
