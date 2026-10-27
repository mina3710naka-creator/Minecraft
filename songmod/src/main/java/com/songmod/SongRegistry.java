package com.songmod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 建てた曲の音符ブロックの一覧。音符ブロックが鳴った(通電した)瞬間を毎 tick 見て、
 * 本来の音が届かない遠く(40 ブロック超)にいるプレイヤーにも同じ音を聞かせる。
 * 一覧とチャンクの常時読み込みの記録は、ゲームフォルダの songmod-builds.json に保存する。
 */
public final class SongRegistry {

	/** これより近いプレイヤーは、音符ブロック本来の音が聞こえるので追加しない */
	private static final double NEAR = 40.0;
	private static final List<Entry> ENTRIES = new ArrayList<>();

	public static final class Entry {
		final String dim;
		final List<BlockPos> notes;
		final List<int[]> forced;
		final boolean[] powered;

		Entry(String dim, List<BlockPos> notes, List<int[]> forced) {
			this.dim = dim;
			this.notes = notes;
			this.forced = forced;
			this.powered = new boolean[notes.size()];
		}
	}

	private SongRegistry() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getGameDir().resolve("songmod-builds.json");
	}

	public static synchronized Entry add(ServerLevel level, List<BlockPos> notes, List<int[]> forced) {
		Entry e = new Entry(level.dimension().toString(), notes, forced);
		ENTRIES.add(e);
		save();
		return e;
	}

	public static synchronized void remove(Entry e) {
		if (e != null && ENTRIES.remove(e)) {
			save();
		}
	}

	private static ServerLevel levelOf(MinecraftServer server, String dim) {
		for (ServerLevel l : server.getAllLevels()) {
			if (l.dimension().toString().equals(dim)) {
				return l;
			}
		}
		return null;
	}

	public static synchronized void load() {
		ENTRIES.clear();
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			for (JsonElement el : JsonParser.parseReader(r).getAsJsonArray()) {
				JsonObject o = el.getAsJsonObject();
				List<BlockPos> notes = new ArrayList<>();
				for (JsonElement n : o.getAsJsonArray("notes")) {
					JsonArray a = n.getAsJsonArray();
					notes.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
				}
				List<int[]> forced = new ArrayList<>();
				for (JsonElement n : o.getAsJsonArray("forced")) {
					JsonArray a = n.getAsJsonArray();
					forced.add(new int[] { a.get(0).getAsInt(), a.get(1).getAsInt() });
				}
				ENTRIES.add(new Entry(o.get("dim").getAsString(), notes, forced));
			}
		} catch (IOException | RuntimeException e) {
			SongMod.LOGGER.warn("songmod-builds.json を読めません", e);
		}
	}

	static synchronized void save() {
		JsonArray all = new JsonArray();
		for (Entry e : ENTRIES) {
			JsonObject o = new JsonObject();
			o.addProperty("dim", e.dim);
			JsonArray notes = new JsonArray();
			for (BlockPos p : e.notes) {
				JsonArray a = new JsonArray();
				a.add(p.getX());
				a.add(p.getY());
				a.add(p.getZ());
				notes.add(a);
			}
			o.add("notes", notes);
			JsonArray forced = new JsonArray();
			for (int[] c : e.forced) {
				JsonArray a = new JsonArray();
				a.add(c[0]);
				a.add(c[1]);
				forced.add(a);
			}
			o.add("forced", forced);
			all.add(o);
		}
		try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
			w.write(all.toString());
		} catch (IOException ex) {
			SongMod.LOGGER.warn("songmod-builds.json を保存できません", ex);
		}
	}

	/** 毎 tick: 新しく鳴った音符ブロックの音を、遠くのプレイヤーへ送る */
	public static synchronized void tick(MinecraftServer server) {
		if (ENTRIES.isEmpty()) {
			return;
		}
		for (Entry e : ENTRIES) {
			ServerLevel level = levelOf(server, e.dim);
			if (level == null || level.players().isEmpty()) {
				continue;
			}
			for (int i = 0; i < e.notes.size(); i++) {
				BlockPos pos = e.notes.get(i);
				if (!level.hasChunkAt(pos)) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				boolean on = state.is(Blocks.NOTE_BLOCK) && state.getValue(NoteBlock.POWERED);
				if (on && !e.powered[i]) {
					play(level, pos, state);
				}
				e.powered[i] = on;
			}
		}
	}

	private static void play(ServerLevel level, BlockPos pos, BlockState state) {
		Holder<SoundEvent> sound = state.getValue(NoteBlock.INSTRUMENT).getSoundEvent();
		float pitch = (float) Math.pow(2.0, (state.getValue(NoteBlock.NOTE) - 12) / 12.0);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > NEAR * NEAR) {
				// 音の出どころをそのプレイヤーの位置にして、距離で小さくならないように送る
				p.connection.send(new ClientboundSoundPacket(sound, SoundSource.RECORDS, p.getX(), p.getY(), p.getZ(), 1.0F, pitch,
						level.getRandom().nextLong()));
			}
		}
	}
}
