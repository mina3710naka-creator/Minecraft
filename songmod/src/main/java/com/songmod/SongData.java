package com.songmod;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 曲の JSON ファイル(tools/audio_to_notes.py が mp3 などから作る)。
 * <pre>
 * { "name": "きらきら星", "notes": [ [0, 60], [5, 60, "bell"], ... ] }
 * </pre>
 * notes の各要素は [時刻, MIDI ノート番号(60=真ん中のド), 楽器(省略可)]。時刻の単位はリピーター 1 目盛り(0.1 秒)。
 */
public final class SongData {

	public final String name;
	public final List<SongPlanner.Note> notes;

	private SongData(String name, List<SongPlanner.Note> notes) {
		this.name = name;
		this.notes = notes;
	}

	public static SongData load(Path file) throws IOException {
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			String name = root.has("name") ? root.get("name").getAsString() : file.getFileName().toString();
			if (!root.has("notes") || !root.get("notes").isJsonArray()) {
				throw new IOException("\"notes\" の配列がありません");
			}
			List<SongPlanner.Note> notes = new ArrayList<>();
			for (JsonElement e : root.getAsJsonArray("notes")) {
				JsonArray a = e.getAsJsonArray();
				if (a.size() < 2) {
					throw new IOException("notes の要素は [時刻, 音程] の形にしてください: " + e);
				}
				Inst forced = null;
				if (a.size() >= 3) {
					forced = Inst.byName(a.get(2).getAsString());
					if (forced == null) {
						throw new IOException("知らない楽器です: " + a.get(2).getAsString());
					}
				}
				notes.add(new SongPlanner.Note(a.get(0).getAsInt(), a.get(1).getAsInt(), forced));
			}
			if (notes.isEmpty()) {
				throw new IOException("音符が 1 つもありません");
			}
			return new SongData(name, notes);
		} catch (IllegalStateException | ClassCastException | com.google.gson.JsonParseException e) {
			throw new IOException("JSON の形が正しくありません: " + e.getMessage());
		}
	}
}
