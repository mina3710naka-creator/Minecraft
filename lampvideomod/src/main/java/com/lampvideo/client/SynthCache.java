package com.lampvideo.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 曲の解析結果(NoteAnalyzer のイベント)の置き場。解析は、別スレッドで1回だけやって、覚えておく(ファイルが変わったらやり直す)。 */
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
		try {
			modified = Files.getLastModifiedTime(file).toMillis();
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
					entry.events = NoteAnalyzer.analyze(file, null);
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
}
