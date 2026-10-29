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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

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
		/** 回路の経路にあるリピーター(始点から順)。信号の先頭 = 通電している最後のもの */
		final List<BlockPos> repeaters;
		/** repeaters と同じ並びで、各リピーターが通電する時刻(始点から、リピーター 1 目盛り単位)。古い記録では空 */
		final int[] times;
		/** この回路を建てた元(選曲台の位置、またはプレイヤー)。同じ元が建て直すとき、前のを消すのに使う */
		final String source;
		/** 置いたブロックの座標(x, y, z の繰り返し)。再起動後でも回路を消せるように保存する */
		final int[] placed;

		Entry(String dim, List<BlockPos> notes, List<BlockPos> repeaters, int[] times, String source, int[] placed,
				List<int[]> forced) {
			this.dim = dim;
			this.notes = notes;
			this.repeaters = repeaters;
			this.times = times;
			this.source = source;
			this.placed = placed;
			this.forced = forced;
			this.powered = new boolean[notes.size()];
		}

		boolean canFollow() {
			return times.length > 0 && times.length == repeaters.size();
		}

		/** 曲の時刻 t(リピーター 1 目盛り単位)の再生位置。リピーター間は直線でつなぐ。範囲外は端 */
		Vec3 at(double t) {
			int n = times.length;
			if (t <= times[0]) {
				return Vec3.atCenterOf(repeaters.get(0));
			}
			if (t >= times[n - 1]) {
				return Vec3.atCenterOf(repeaters.get(n - 1));
			}
			int lo = 0;
			int hi = n - 1;
			while (hi - lo > 1) {
				int mid = (lo + hi) >>> 1;
				if (times[mid] <= t) {
					lo = mid;
				} else {
					hi = mid;
				}
			}
			double f = (t - times[lo]) / (double) (times[hi] - times[lo]);
			Vec3 a = Vec3.atCenterOf(repeaters.get(lo));
			Vec3 b = Vec3.atCenterOf(repeaters.get(hi));
			return a.add(b.subtract(a).scale(f));
		}

		int endTime() {
			return times[times.length - 1];
		}
	}

	private SongRegistry() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getGameDir().resolve("songmod-builds.json");
	}

	public static synchronized Entry add(ServerLevel level, List<BlockPos> notes, List<BlockPos> repeaters, int[] times,
			String source, int[] placed, List<int[]> forced) {
		Entry e = new Entry(level.dimension().toString(), notes, repeaters, times, source, placed, forced);
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
				List<BlockPos> reps = new ArrayList<>();
				if (o.has("repeaters")) {
					for (JsonElement n : o.getAsJsonArray("repeaters")) {
						JsonArray a = n.getAsJsonArray();
						reps.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
					}
				}
				int[] times = new int[0];
				if (o.has("times")) {
					JsonArray ta = o.getAsJsonArray("times");
					times = new int[ta.size()];
					for (int i = 0; i < times.length; i++) {
						times[i] = ta.get(i).getAsInt();
					}
				}
				String source = o.has("source") ? o.get("source").getAsString() : "";
				int[] placed = new int[0];
				if (o.has("placed")) {
					JsonArray pa = o.getAsJsonArray("placed");
					placed = new int[pa.size()];
					for (int i = 0; i < placed.length; i++) {
						placed[i] = pa.get(i).getAsInt();
					}
				}
				List<int[]> forced = new ArrayList<>();
				for (JsonElement n : o.getAsJsonArray("forced")) {
					JsonArray a = n.getAsJsonArray();
					forced.add(new int[] { a.get(0).getAsInt(), a.get(1).getAsInt() });
				}
				ENTRIES.add(new Entry(o.get("dim").getAsString(), notes, reps, times, source, placed, forced));
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
			JsonArray reps = new JsonArray();
			for (BlockPos p : e.repeaters) {
				JsonArray a = new JsonArray();
				a.add(p.getX());
				a.add(p.getY());
				a.add(p.getZ());
				reps.add(a);
			}
			o.add("repeaters", reps);
			JsonArray ts = new JsonArray();
			for (int t : e.times) {
				ts.add(t);
			}
			o.add("times", ts);
			o.addProperty("source", e.source);
			JsonArray pl = new JsonArray();
			for (int v : e.placed) {
				pl.add(v);
			}
			o.add("placed", pl);
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

	/**
	 * その元(source)が建てた回路を、記録をもとに消す(ブロックを空気にして、チャンクの常時読み込みもやめる)。
	 * 建てた時の元の地形までは分からないので、空気になる。@return 消したブロック数
	 */
	public static synchronized int eraseBySource(MinecraftServer server, String source) {
		int n = 0;
		List<Entry> hit = new ArrayList<>();
		for (Entry e : ENTRIES) {
			if (e.source.equals(source)) {
				hit.add(e);
			}
		}
		for (Entry e : hit) {
			ServerLevel level = levelOf(server, e.dim);
			if (level != null) {
				for (int i = 0; i + 2 < e.placed.length; i += 3) {
					level.setBlock(new BlockPos(e.placed[i], e.placed[i + 1], e.placed[i + 2]),
							Blocks.AIR.defaultBlockState(), 2 | 16);
					n++;
				}
				for (int[] c : e.forced) {
					level.setChunkForced(c[0], c[1], false);
				}
			}
			ENTRIES.remove(e);
		}
		if (!hit.isEmpty()) {
			save();
		}
		return n;
	}

	/** 鳴っている曲と、信号の先頭のリピーターの番号 */
	public record Playing(Entry entry, int index) { }

	/**
	 * いま鳴っている曲: そのディメンションの追従できる曲のうち、通電している最後のリピーターを持つもの。
	 * 複数あれば from に近いもの。どれも鳴っていなければ null。
	 */
	public static synchronized Playing playing(ServerLevel level, Vec3 from) {
		String dim = level.dimension().toString();
		Playing best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entry e : ENTRIES) {
			if (!e.dim.equals(dim) || !e.canFollow()) {
				continue;
			}
			for (int i = e.repeaters.size() - 1; i >= 0; i--) {
				BlockPos pos = e.repeaters.get(i);
				if (!level.hasChunkAt(pos)) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (state.is(Blocks.REPEATER) && state.getValue(BlockStateProperties.POWERED)) {
					double d = Vec3.atCenterOf(pos).distanceToSqr(from);
					if (d < bestDist) {
						bestDist = d;
						best = new Playing(e, i);
					}
					break;
				}
			}
		}
		return best;
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
