package com.statuemod;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 像の JSON ファイル。
 * <pre>
 * {
 *   "name": "クリーパー",
 *   "palette": { "G": "minecraft:lime_concrete", "K": "minecraft:black_concrete", ".": "minecraft:air" },
 *   "layers": [            // 先頭が 1 層目(いちばん下)
 *     [ "GGGG",            // 1 層の中の行。先頭の行が像の正面(手前)
 *       "GGGG" ],
 *     ...
 *   ]
 * }
 * </pre>
 * 文字は palette の 1 文字キー。空白は常に空気(置かない)。
 */
public final class StatueData {

	public final String name;
	/** 空気のマスにも「空気を置く」(周りの既存ブロックを消す)か。既定は false(置かない) */
	public final boolean replaceAir;
	public final Map<Character, String> palette;
	/** layers.get(y).get(row) は 1 行の文字列 */
	public final List<List<String>> layers;
	public final int width;
	public final int depth;

	private StatueData(String name, boolean replaceAir, Map<Character, String> palette,
			List<List<String>> layers) {
		this.name = name;
		this.replaceAir = replaceAir;
		this.palette = palette;
		this.layers = layers;
		int w = 1;
		int d = 1;
		for (List<String> layer : layers) {
			d = Math.max(d, layer.size());
			for (String row : layer) {
				w = Math.max(w, row.length());
			}
		}
		this.width = w;
		this.depth = d;
	}

	public int height() {
		return layers.size();
	}

	public static StatueData load(Path file) throws IOException {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return parse(JsonParser.parseReader(reader), stem(file));
		} catch (RuntimeException e) {
			throw new IOException("JSON を読めません: " + e.getMessage(), e);
		}
	}

	private static String stem(Path file) {
		String n = file.getFileName().toString();
		return n.endsWith(".json") ? n.substring(0, n.length() - 5) : n;
	}

	static StatueData parse(JsonElement root, String fallbackName) {
		if (!root.isJsonObject()) {
			throw new IllegalArgumentException("一番外側は { } である必要があります");
		}
		JsonObject obj = root.getAsJsonObject();
		String name = obj.has("name") ? obj.get("name").getAsString() : fallbackName;
		boolean replaceAir = obj.has("replace_air") && obj.get("replace_air").getAsBoolean();

		if (!obj.has("palette") || !obj.get("palette").isJsonObject()) {
			throw new IllegalArgumentException("\"palette\" がありません");
		}
		Map<Character, String> palette = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("palette").entrySet()) {
			if (e.getKey().length() != 1) {
				throw new IllegalArgumentException("palette のキーは 1 文字にしてください: \"" + e.getKey() + "\"");
			}
			palette.put(e.getKey().charAt(0), e.getValue().getAsString());
		}

		if (!obj.has("layers") || !obj.get("layers").isJsonArray()) {
			throw new IllegalArgumentException("\"layers\" がありません");
		}
		List<List<String>> layers = new ArrayList<>();
		int index = 0;
		for (JsonElement layerEl : obj.getAsJsonArray("layers")) {
			index++;
			List<String> rows = new ArrayList<>();
			if (layerEl.isJsonArray()) {
				for (JsonElement row : (JsonArray) layerEl) {
					rows.add(row.getAsString());
				}
			} else {
				throw new IllegalArgumentException(index + " 層目は文字列の配列にしてください");
			}
			layers.add(rows);
		}
		if (layers.isEmpty()) {
			throw new IllegalArgumentException("layers が空です");
		}
		for (List<String> layer : layers) {
			for (String row : layer) {
				for (char c : row.toCharArray()) {
					if (c != ' ' && !palette.containsKey(c)) {
						throw new IllegalArgumentException("palette にない文字 '" + c + "' が使われています");
					}
				}
			}
		}
		return new StatueData(name, replaceAir, palette, layers);
	}
}
