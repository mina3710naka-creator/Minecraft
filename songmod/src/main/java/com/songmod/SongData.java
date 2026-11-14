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
 * { "name": "きらきら星", "row_length": 61, "notes": [ [0, 60], [5, 60, "bell"], ... ] }
 * </pre>
 * notes の各要素は [時刻, MIDI ノート番号(60=真ん中のド), 楽器(文字)・音の強さ 1〜100 %(数字)(どちらも省略可)]。row_length(省略可)は折り返すマス数。時刻の単位はリピーター 1 目盛り(0.1 秒)。
 */
public final class SongData {

	public final String name;
	public final List<SongPlanner.Note> notes;
	/** 折り返すマス数(0 なら自動、負なら折り返さず 1 列) */
	public final int rowLength;

	private SongData(String name, List<SongPlanner.Note> notes, int rowLength) {
		this.name = name;
		this.rowLength = rowLength;
		this.notes = notes;
	}

	/**
	 * 建てるときの設定を反映した曲。速度(2.0 なら 2 倍速)で時刻を割り、移調で音程を動かし、
	 * forced が null でなければ、音程のある音をすべてその楽器にする(打楽器はそのまま)。
	 */
	public SongData transformed(double speed, int transpose, Inst forced) {
		java.util.Set<String> seen = new java.util.HashSet<>();
		List<SongPlanner.Note> out = new ArrayList<>();
		for (SongPlanner.Note n : notes) {
			boolean percussion = n.forced() != null && n.forced().percussion();
			int tick = (int) Math.round(n.tick() / speed);
			int midi = percussion ? n.midi() : n.midi() + transpose;
			Inst inst = percussion ? n.forced() : (forced != null ? forced : n.forced());
			if (seen.add(tick + ":" + midi + ":" + inst)) { // 速さを上げると、同じ音が重なることがある
				out.add(new SongPlanner.Note(tick, midi, inst, n.vel()));
			}
		}
		return new SongData(name, out, rowLength);
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
				int vel = 100;
				for (int i = 2; i < a.size(); i++) {
					// 3 つ目以降: 文字は楽器の名前、数字は音の強さ(1〜100 %)
					if (a.get(i).getAsJsonPrimitive().isString()) {
						forced = Inst.byName(a.get(i).getAsString());
						if (forced == null) {
							throw new IOException("知らない楽器です: " + a.get(i).getAsString());
						}
					} else {
						vel = Math.max(1, Math.min(100, a.get(i).getAsInt()));
					}
				}
				notes.add(new SongPlanner.Note(a.get(0).getAsInt(), a.get(1).getAsInt(), forced, vel));
			}
			if (notes.isEmpty()) {
				throw new IOException("音符が 1 つもありません");
			}
			int row = 0;
			if (root.has("row_length")) {
				JsonElement rl = root.get("row_length");
				row = rl.getAsJsonPrimitive().isString() ? -1 : rl.getAsInt();
			}
			return new SongData(name, notes, row);
		} catch (IllegalStateException | ClassCastException | com.google.gson.JsonParseException e) {
			throw new IOException("JSON の形が正しくありません: " + e.getMessage());
		}
	}
}
