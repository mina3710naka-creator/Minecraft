package com.lampvideo.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 曲の旋律(NoteAnalyzer のイベント)の置き場。.minecraft/lampvideo(または lampvideo/media, lampvideo/melody)に、
 * 曲と同じ名前の「名前.melody.json」(tools/melody_tool.py で作る主旋律)があれば、それを読む。なければ、曲を自動で解析する。
 * 解析は、別スレッドで1回だけやって、覚えておく(ファイルが変わったらやり直す)。 */
public final class SynthCache {

	private static final class Entry {
		final long modified;
		volatile NoteAnalyzer.Events events;
		volatile boolean failed;

		Entry(long modified) {
			this.modified = modified;
		}
	}

	private static final int MAX_FILES = 4;
	private static final Map<String, Entry> CACHE = new HashMap<>();

	private SynthCache() {
	}

	/** 解析が終わっていれば、その結果。まだなら、解析を始めて(始めていなければ)、null。 */
	public static synchronized NoteAnalyzer.Events get(Path file) {
		String key = file.toAbsolutePath().toString();
		long modified;
		final Path melody = findMelody(file);
		try {
			modified = Files.getLastModifiedTime(file).toMillis();
			if (melody != null) {
				modified += Files.getLastModifiedTime(melody).toMillis();
			}
		} catch (IOException e) {
			return null;
		}
		Entry e = CACHE.get(key);
		if (e != null && e.modified != modified) {
			CACHE.remove(key);
			e = null;
		}
		if (e == null) {
			if (CACHE.size() >= MAX_FILES) {
				CACHE.clear();   // 古いものを覚えておく必要はない
			}
			final Entry entry = new Entry(modified);
			CACHE.put(key, entry);
			Thread t = new Thread(() -> {
				try {
					entry.events = melody != null ? loadMelody(melody) : NoteAnalyzer.analyze(file, null);
				} catch (IOException | RuntimeException ex) {
					entry.failed = true;
				}
			}, "lampvideo-note-analyzer");
			t.setDaemon(true);
			t.start();
			return null;
		}
		return e.events;
	}

	private static Path findMelody(Path media) {
		String name = media.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String base = (dot > 0 ? name.substring(0, dot) : name) + ".melody.json";
		Path root = FabricLoader.getInstance().getGameDir().resolve("lampvideo");
		for (Path dir : new Path[] { root, root.resolve("media"), root.resolve("melody") }) {
			Path p = dir.resolve(base);
			if (Files.isRegularFile(p)) {
				return p;
			}
		}
		return null;
	}

	/** melody_tool.py の出力({"notes":[{"t":開始ms,"d":長さms,"n":MIDI ノート,"v":強さ}]})を、イベントにする。 */
	private static NoteAnalyzer.Events loadMelody(Path file) throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		JsonArray notes = root.getAsJsonArray("notes");
		java.util.List<int[]> list = new java.util.ArrayList<>();
		for (JsonElement el : notes) {
			JsonObject o = el.getAsJsonObject();
			list.add(new int[] { o.get("t").getAsInt(), o.get("n").getAsInt(), o.has("v") ? o.get("v").getAsInt() : 100 });
		}
		list.sort((a, b) -> Integer.compare(a[0], b[0]));
		NoteAnalyzer.Events ev = new NoteAnalyzer.Events();
		for (int[] n : list) {
			ev.add(n[0], NoteAnalyzer.PITCHED, n[1], n[2]);
		}
		return ev;
	}
}
