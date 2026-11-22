package com.effectmod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

/**
 * Gemini API (無料枠) と連携して、どんな文章でも effectmod の効果に自動変換するクラス。
 * 一度解釈した文章は config/effectmod_gemini_cache.txt にキャッシュし、二度目以降は即座に返します。
 */
public final class GeminiClient {

	private static final String DEFAULT_MODEL = "gemini-2.5-flash";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private static String cachedApiKey = null;
	private static String cachedModel = DEFAULT_MODEL;
	private static boolean configLoaded = false;

	private static final Map<String, String> cache = new LinkedHashMap<>();
	private static boolean cacheLoaded = false;

	private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
	private static final Pattern FIELD_TRIGGER = Pattern.compile("\"trigger\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
	private static final Pattern FIELD_ACTION = Pattern.compile("\"action\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
	private static final Pattern FIELD_ARG = Pattern.compile("\"arg\"\\s*:\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
	private static final Pattern FIELD_POWER = Pattern.compile("\"power\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern FIELD_SECONDS = Pattern.compile("\"seconds\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern FIELD_CHANCE = Pattern.compile("\"chance\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern FIELD_CD = Pattern.compile("\"cd\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

	private GeminiClient() {
	}

	public static synchronized void loadConfig() {
		if (configLoaded) {
			return;
		}
		configLoaded = true;
		try {
			Path dir = FabricLoader.getInstance().getConfigDir();
			Path f = dir.resolve("effectmod_gemini.txt");
			if (!Files.exists(f)) {
				Files.createDirectories(f.getParent());
				Files.writeString(f, String.join("\n",
						"# Gemini API の設定ファイル",
						"# Google AI Studio (無料) で API キーを取得して貼り付けてください: https://aistudio.google.com/",
						"# 従来の AIza... 形式および最新の Auth Key (AQ... 形式) の両方に対応しています。",
						"",
						"GEMINI_API_KEY=",
						"GEMINI_MODEL=gemini-2.5-flash",
						"") + "\n", StandardCharsets.UTF_8);
			}
			for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				String t = line.strip();
				if (t.isEmpty() || t.startsWith("#")) {
					continue;
				}
				int eq = t.indexOf('=');
				if (eq > 0) {
					String key = t.substring(0, eq).strip().toUpperCase(Locale.ROOT);
					String val = t.substring(eq + 1).strip();
					if (key.equals("GEMINI_API_KEY") || key.equals("API_KEY")) {
						cachedApiKey = val.isEmpty() ? null : val;
					} else if (key.equals("GEMINI_MODEL") || key.equals("MODEL")) {
						if (!val.isEmpty()) {
							cachedModel = val;
						}
					}
				}
			}
		} catch (IOException e) {
			EffectMod.LOGGER.warn("effectmod_gemini.txt の読み込みに失敗しました", e);
		}
	}

	public static synchronized boolean hasApiKey() {
		loadConfig();
		return cachedApiKey != null && !cachedApiKey.isBlank();
	}

	public static synchronized String getApiKey() {
		loadConfig();
		return cachedApiKey;
	}

	private static synchronized void loadCache() {
		if (cacheLoaded) {
			return;
		}
		cacheLoaded = true;
		try {
			Path f = FabricLoader.getInstance().getConfigDir().resolve("effectmod_gemini_cache.txt");
			if (Files.exists(f)) {
				for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
					String t = line.strip();
					int eq = t.indexOf('\t');
					if (eq > 0 && eq < t.length() - 1) {
						cache.put(t.substring(0, eq).strip(), t.substring(eq + 1).strip());
					}
				}
			}
		} catch (IOException e) {
			EffectMod.LOGGER.warn("effectmod_gemini_cache.txt の読み込みに失敗しました", e);
		}
	}

	private static synchronized void saveCache(String text, String json) {
		try {
			loadCache();
			cache.put(text.strip(), json.strip());
			Path f = FabricLoader.getInstance().getConfigDir().resolve("effectmod_gemini_cache.txt");
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, String> entry : cache.entrySet()) {
				sb.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
			}
			Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			EffectMod.LOGGER.warn("effectmod_gemini_cache.txt の保存に失敗しました", e);
		}
	}

	public static synchronized Entry getCached(String text) {
		loadCache();
		String rawJson = cache.get(text.strip());
		if (rawJson != null) {
			return parseJsonToEntry(text, rawJson);
		}
		return null;
	}

	/**
	 * Gemini API を非同期で呼び出して文章を効果に解釈します。
	 */
	public static CompletableFuture<Parser.Result> interpretAsync(String rawText, ItemStack targetItem) {
		String text = rawText == null ? "" : rawText.trim();
		if (text.isEmpty()) {
			return CompletableFuture.completedFuture(new Parser.Result(null, "文章を入力してください"));
		}

		// キャッシュにある場合は即座に返す
		Entry cached = getCached(text);
		if (cached != null) {
			return CompletableFuture.completedFuture(new Parser.Result(cached, null));
		}

		if (!hasApiKey()) {
			return CompletableFuture.completedFuture(new Parser.Result(null,
					"解釈できませんでした。config/effectmod_gemini.txt に Gemini API キーを設定すると、どんな文章でも自動解釈できるようになります。"));
		}

		return CompletableFuture.supplyAsync(() -> {
			try {
				String prompt = buildPrompt(text, targetItem);
				String responseJson = callGeminiApi(prompt);
				Entry entry = parseJsonToEntry(text, responseJson);
				if (entry != null) {
					saveCache(text, responseJson);
					return new Parser.Result(entry, null);
				} else {
					return new Parser.Result(null, "Gemini の解釈結果の解析に失敗しました: " + responseJson);
				}
			} catch (Exception e) {
				EffectMod.LOGGER.error("Gemini API の呼び出しエラー: {}", e.getMessage(), e);
				return new Parser.Result(null, "AI解釈エラー: " + e.getMessage());
			}
		});
	}

	private static String buildPrompt(String userText, ItemStack targetItem) {
		StringBuilder actionsList = new StringBuilder();
		for (Parser.Action a : Parser.Action.values()) {
			actionsList.append(a.name()).append(" (").append(a.label).append(")");
			if (!a.cats.isEmpty()) {
				actionsList.append(" [専用]");
			}
			actionsList.append(", ");
		}

		StringBuilder triggersList = new StringBuilder();
		for (Parser.Trigger t : Parser.Trigger.values()) {
			triggersList.append(t.name()).append(" (").append(t.label).append("), ");
		}

		String itemInfo = (targetItem == null || targetItem.isEmpty()) ? "なし (汎用アイテム)" : targetItem.getItem().toString();

		return """
				You are an RPG game ability interpreter for Minecraft.
				Convert the user's arbitrary text into the best matching game ability settings.

				Valid Triggers:
				%s

				Valid Actions:
				%s

				Target Item: %s

				Rules:
				1. trigger: Choose the most fitting Trigger name (e.g. HIT, HURT, USE, KILL, MOVE, PASSIVE, JUMP, LAND).
				2. action: Choose the most fitting Action name from the valid actions.
				3. arg: Additional argument if applicable:
				   - For POTION action: "<potion_id>;<self|target>" (e.g. "speed;self", "poison;target", "regeneration;self", "strength;self")
				   - For PLACE action: "<minecraft:block_id>;<FEET|TARGET|WALL|AROUND|FRONT>;<1 for temporary, 0 for permanent>" (e.g. "minecraft:ice;FEET;1")
				   - Otherwise leave empty string "".
				4. power: Integer 1 to 5 (or up to 10 for very grand descriptions).
				5. seconds: Duration in seconds (e.g. 0 to 10).
				6. chance: Activation probability percentage (integer 1 to 100, default 100).
				7. cd: Cooldown in game ticks (20 ticks = 1 second, e.g. 0, 40, 100).

				Output MUST be ONLY valid JSON matching this schema, no extra commentary or markdown:
				{
				  "trigger": "TRIGGER_NAME",
				  "action": "ACTION_NAME",
				  "arg": "",
				  "power": 3,
				  "seconds": 0,
				  "chance": 100,
				  "cd": 0
				}

				User text: "%s"
				""".formatted(triggersList, actionsList, itemInfo, escapeJson(userText));
	}

	private static String callGeminiApi(String prompt) throws IOException, InterruptedException {
		String key = getApiKey();
		String model = cachedModel == null || cachedModel.isBlank() ? DEFAULT_MODEL : cachedModel;
		String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";

		String requestBody = "{\"contents\":[{\"parts\":[{\"text\":\"" + escapeJson(prompt) + "\"}]}]}";

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.header("Content-Type", "application/json; charset=UTF-8")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
				.timeout(Duration.ofSeconds(15));

		// AQ. (認可キー) および Bearer 認証形式に対応、従来の AIza... 形式もヘッダーとクエリの両方で安全にフォールバック
		if (key.startsWith("AQ.") || key.startsWith("Bearer ") || !key.startsWith("AIza")) {
			String token = key.startsWith("Bearer ") ? key : "Bearer " + key;
			builder.uri(URI.create(url))
					.header("Authorization", token)
					.header("x-goog-api-key", key);
		} else {
			builder.uri(URI.create(url + "?key=" + key))
					.header("x-goog-api-key", key);
		}

		HttpRequest req = builder.build();

		HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
			throw new IOException("HTTP " + resp.statusCode() + " " + resp.body());
		}
		return resp.body();
	}

	private static Entry parseJsonToEntry(String originalText, String geminiResponse) {
		try {
			String json = geminiResponse;
			// マークダウンコードブロックがある場合は中身を抽出
			Matcher mBlock = JSON_BLOCK.matcher(json);
			if (mBlock.find()) {
				json = mBlock.group(1);
			}

			String trigger = extractString(FIELD_TRIGGER, json, "USE");
			String action = extractString(FIELD_ACTION, json, "SLASH");
			String arg = extractString(FIELD_ARG, json, "");
			int power = extractInt(FIELD_POWER, json, 1);
			int seconds = extractInt(FIELD_SECONDS, json, 0);
			int chance = extractInt(FIELD_CHANCE, json, 100);
			int cd = extractInt(FIELD_CD, json, 0);

			// バリデーション
			try {
				Parser.Trigger.valueOf(trigger);
			} catch (IllegalArgumentException e) {
				trigger = Parser.Trigger.USE.name();
			}

			try {
				Parser.Action.valueOf(action);
			} catch (IllegalArgumentException e) {
				action = Parser.Action.SLASH.name();
			}

			power = Math.max(1, Math.min(10, power));
			seconds = Math.max(0, Math.min(300, seconds));
			chance = Math.max(1, Math.min(100, chance));
			cd = Math.max(0, Math.min(1200, cd));

			return new Entry(originalText.strip(), trigger, action, arg, power, seconds, chance, cd);
		} catch (Exception e) {
			EffectMod.LOGGER.warn("Failed to parse Gemini JSON: " + geminiResponse, e);
			return null;
		}
	}

	private static String extractString(Pattern p, String s, String def) {
		Matcher m = p.matcher(s);
		return m.find() ? m.group(1).trim() : def;
	}

	private static int extractInt(Pattern p, String s, int def) {
		Matcher m = p.matcher(s);
		if (m.find()) {
			try {
				return Integer.parseInt(m.group(1));
			} catch (NumberFormatException ignored) {
			}
		}
		return def;
	}

	private static String escapeJson(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\b' -> sb.append("\\b");
				case '\f' -> sb.append("\\f");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < ' ') {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}
}
