package com.effectmod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 自分だけの言葉の登録。config/effectmod_words.txt に「名前=文章」と1行ずつ書くと、
 * 文章の中に「名前」を書くだけで、その文章に置きかわる(付与台のボタンにも出る)。
 * 例: 炎の一撃=攻撃したとき相手を燃やす 5
 */
public final class Macros {

	private static Map<String, String> words;

	private Macros() {
	}

	private static String norm(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
	}

	private static synchronized Map<String, String> load() {
		if (words != null) {
			return words;
		}
		words = new LinkedHashMap<>();
		try {
			Path f = FabricLoader.getInstance().getConfigDir().resolve("effectmod_words.txt");
			if (!Files.exists(f)) {
				Files.createDirectories(f.getParent());
				Files.writeString(f, String.join("\n",
						"# 自分だけの言葉を登録するファイル。「名前=文章」の形で1行ずつ書く(# で始まる行は無視)。",
						"# 付与台の文章に「名前」を書くと、右の文章に置きかわります。ボタンにも出ます。",
						"# 例:",
						"炎の一撃=攻撃したとき相手を燃やす 5",
						"氷の道=歩くと足元に氷ブロックを置く",
						"") + "\n", StandardCharsets.UTF_8);
			}
			for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				String t = line.strip();
				int eq = t.indexOf('=');
				if (t.isEmpty() || t.startsWith("#") || eq < 1 || eq == t.length() - 1) {
					continue;
				}
				words.put(t.substring(0, eq).strip(), t.substring(eq + 1).strip());
			}
		} catch (IOException | RuntimeException e) {
			EffectMod.LOGGER.warn("effectmod_words.txt を読めませんでした", e);
		}
		return words;
	}

	/** 登録した言葉(名前 → 文章)。 */
	public static Map<String, String> all() {
		return load();
	}

	/** 文章の中の登録した言葉を、元の文章に置きかえる。 */
	public static String expand(String text) {
		if (text == null) {
			return "";
		}
		String out = text;
		String low = norm(text);
		for (Map.Entry<String, String> e : load().entrySet()) {
			String k = norm(e.getKey());
			if (!k.isEmpty() && low.contains(k)) {
				out = norm(out).replace(k, e.getValue());
				low = norm(out);
			}
		}
		return out;
	}

	public static List<String[]> asPresets() {
		List<String[]> l = new ArrayList<>();
		load().forEach((k, v) -> l.add(new String[] {k, v}));
		return l;
	}
}
