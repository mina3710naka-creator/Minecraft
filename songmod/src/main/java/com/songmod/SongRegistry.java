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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * 建てた曲の音符ブロックの一覧。音符ブロックが鳴った(通電した)瞬間を毎 tick 見て、
 * 本来の音が届かない遠く(40 ブロック超)にいるプレイヤーにも同じ音を聞かせる。
 * 一覧とチャンクの常時読み込みの記録は、ゲームフォルダの songmod-builds.json に保存する。
 */
public final class SongRegistry {

	private static final List<Entry> ENTRIES = new ArrayList<>();
	/** -Dsongmod.trace=true で、鳴った音符の記録(ゲームの時刻つき)をログに出す。動作の検証用 */
	static final boolean TRACE = Boolean.getBoolean("songmod.trace");

	public static final class Entry {
		final String dim;
		final List<BlockPos> notes;
		final List<int[]> forced;
		final boolean[] powered;
		/** 回路の経路にあるリピーター(始点から順)。信号の先頭 = 通電している最後のもの */
		final List<BlockPos> repeaters;
		/** repeaters と同じ並びで、各リピーターが通電する時刻(始点から、リピーター 1 目盛り単位)。古い記録では空 */
		final int[] times;
		/** 始点のボタン。押されている間は「曲が始まった(最初のリピーターが通電するまでの猶予)」とみなす */
		final BlockPos start;
		/** この回路を建てた元(選曲台の位置、またはプレイヤー)。同じ元が建て直すとき、前のを消すのに使う */
		final String source;
		/** 置いたブロックの座標(x, y, z の繰り返し)。再起動後でも回路を消せるように保存する */
		final int[] placed;
		/** 回路の床(h = 0)の高さ。これより下に置いたブロックは、消すとき草ブロックにする。古い記録では Integer.MIN_VALUE(= 草にしない) */
		final int groundY;
		/** notes と同じ並びの、音符ごとの強さ(1〜100 %)。古い記録では空(= すべて 100 %) */
		final int[] vel;
		/** notes と同じ並びの、本来のMIDIノート番号(0 は打楽器または不明)。オクターブ跳躍を無くすのに使う */
		final int[] midis;
		/** notes と同じ並びの、発音予定時刻(リピーター 1 目盛り単位)。走査の高速化に使う */
		final int[] noteTimes;
		/** 全体の音量(%)。選曲台の画面で変えられる */
		volatile int volumePct;
		/** 現在動的に常時読み込み(forced)にしているチャンク(座標 asLong) */
		final java.util.Set<Long> activeChunks = new java.util.HashSet<>();
		/** 自己診断用: 音符ブロックごとに、鳴ったか(保存しない) */
		boolean[] fired;
		int firedCount;
		long firstFire = -1;
		long lastFire = -1;

		Entry(String dim, List<BlockPos> notes, List<BlockPos> repeaters, int[] times, BlockPos start, int groundY, String source,
				int[] placed, List<int[]> forced, int[] vel, int[] midis, int[] noteTimes, int volumePct) {
			this.groundY = groundY;
			this.vel = vel;
			this.midis = midis != null ? midis : new int[0];
			this.noteTimes = noteTimes != null ? noteTimes : new int[0];
			this.volumePct = volumePct;
			this.dim = dim;
			this.notes = notes;
			this.repeaters = repeaters;
			this.times = times;
			this.start = start;
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
			BlockPos start, int groundY, String source, int[] placed, List<int[]> forced,
			int[] vel, int[] midis, int[] noteTimes, int volumePct) {
		Entry e = new Entry(level.dimension().toString(), notes, repeaters, times, start, groundY, source, placed, forced, vel, midis, noteTimes, volumePct);
		ENTRIES.add(e);
		save();
		return e;
	}

	public static synchronized void remove(Entry e) {
		if (e != null && ENTRIES.remove(e)) {
			save();
		}
	}

	static ServerLevel levelOf(MinecraftServer server, String dim) {
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
				BlockPos start = null;
				if (o.has("start")) {
					JsonArray sa = o.getAsJsonArray("start");
					start = new BlockPos(sa.get(0).getAsInt(), sa.get(1).getAsInt(), sa.get(2).getAsInt());
				}
				int[] vel = new int[0];
				if (o.has("vel")) {
					JsonArray va = o.getAsJsonArray("vel");
					vel = new int[va.size()];
					for (int i = 0; i < vel.length; i++) {
						vel[i] = va.get(i).getAsInt();
					}
				}
				int[] midis = new int[0];
				if (o.has("midis")) {
					JsonArray ma = o.getAsJsonArray("midis");
					midis = new int[ma.size()];
					for (int i = 0; i < midis.length; i++) {
						midis[i] = ma.get(i).getAsInt();
					}
				}
				int[] noteTimes = new int[0];
				if (o.has("noteTimes")) {
					JsonArray nta = o.getAsJsonArray("noteTimes");
					noteTimes = new int[nta.size()];
					for (int i = 0; i < noteTimes.length; i++) {
						noteTimes[i] = nta.get(i).getAsInt();
					}
				}
				int volumePct = o.has("volume") ? o.get("volume").getAsInt() : 100;
				int groundY = o.has("ground") ? o.get("ground").getAsInt() : Integer.MIN_VALUE;
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
				ENTRIES.add(new Entry(o.get("dim").getAsString(), notes, reps, times, start, groundY, source, placed, forced, vel, midis, noteTimes, volumePct));
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
			if (e.start != null) {
				JsonArray sa = new JsonArray();
				sa.add(e.start.getX());
				sa.add(e.start.getY());
				sa.add(e.start.getZ());
				o.add("start", sa);
			}
			if (e.groundY != Integer.MIN_VALUE) {
				o.addProperty("ground", e.groundY);
			}
			JsonArray vs = new JsonArray();
			for (int v : e.vel) {
				vs.add(v);
			}
			o.add("vel", vs);
			JsonArray ms = new JsonArray();
			for (int m : e.midis) {
				ms.add(m);
			}
			o.add("midis", ms);
			JsonArray nts = new JsonArray();
			for (int nt : e.noteTimes) {
				nts.add(nt);
			}
			o.add("noteTimes", nts);
			o.addProperty("volume", e.volumePct);
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
			if (level == null || (level.players().isEmpty() && !TRACE)) {
				continue;
			}
			int front = frontIndex(level, e);
			updateDynamicChunks(level, e, front);

			if (front < -1) {
				// 信号が流れていないときは音符ブロック監視を完全スキップ(CPU負荷 0)
				continue;
			}

			int curTime = front >= 0 && front < e.times.length ? e.times[front] : 0;
			boolean hasTimes = e.noteTimes != null && e.noteTimes.length == e.notes.size();

			for (int i = 0; i < e.notes.size(); i++) {
				// 発音時刻が記録されている場合、現在時刻付近(前後数目盛り)の音符ブロックのみ検査
				if (hasTimes) {
					int nt = e.noteTimes[i];
					if (nt < curTime - 3 || nt > curTime + 6) {
						continue;
					}
				}
				BlockPos pos = e.notes.get(i);
				if (!level.hasChunkAt(pos)) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				boolean on = state.is(Blocks.NOTE_BLOCK) && state.getValue(NoteBlock.POWERED);
				if (on && !e.powered[i]) {
					if (e.fired == null) {
						e.fired = new boolean[e.notes.size()];
					}
					if (!e.fired[i]) {
						e.fired[i] = true;
						e.firedCount++;
					}
					long now = level.getGameTime();
					e.lastFire = now;
					if (e.firstFire < 0) {
						e.firstFire = now;
					}
					if (TRACE) {
						SongMod.LOGGER.info("TRACE fire tick={} pos={},{},{} inst={} note={}", now, pos.getX(), pos.getY(), pos.getZ(),
								state.getValue(NoteBlock.INSTRUMENT).name(), state.getValue(NoteBlock.NOTE));
					}
					int origMidi = i < e.midis.length ? e.midis[i] : 0;
					play(level, pos, state, i < e.vel.length ? e.vel[i] : 100, e.volumePct, origMidi);
				}
				e.powered[i] = on;
			}
		}
	}

	private static long chunkKey(int cx, int cz) {
		return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
	}

	private static int chunkX(long key) {
		return (int) (key & 0xFFFFFFFFL);
	}

	private static int chunkZ(long key) {
		return (int) (key >>> 32);
	}

	/**
	 * 演奏の進行に合わせて、必要なチャンク(前方 5 チャンク、後方 1 チャンク)のみを動的に読み込み、
	 * 通過済みのチャンクは解放してサーバー負荷を抑える。
	 */
	private static void updateDynamicChunks(ServerLevel level, Entry e, int front) {
		long startChunk = e.start != null ? chunkKey(e.start.getX() >> 4, e.start.getZ() >> 4) : 0;
		if (e.start != null) {
			int scx = e.start.getX() >> 4;
			int scz = e.start.getZ() >> 4;
			if (e.activeChunks.add(startChunk)) {
				level.setChunkForced(scx, scz, true);
			}
		}

		if (front < -1) {
			// 流れていない: 始点以外の動的チャンクをアンロードして負荷軽減
			if (e.activeChunks.size() > (e.start != null ? 1 : 0)) {
				java.util.Iterator<Long> it = e.activeChunks.iterator();
				while (it.hasNext()) {
					long cp = it.next();
					if (e.start != null && cp == startChunk) {
						continue;
					}
					level.setChunkForced(chunkX(cp), chunkZ(cp), false);
					it.remove();
				}
			}
			return;
		}

		// 流れている: 現在位置の前方 24 リピーター分(約 4〜5 チャンク)・後方 4 リピーター分を維持
		java.util.Set<Long> needed = new java.util.HashSet<>();
		if (e.start != null) {
			needed.add(startChunk);
		}
		int startIdx = Math.max(0, front - 4);
		int endIdx = Math.min(e.repeaters.size(), front + 24);
		for (int idx = startIdx; idx < endIdx; idx++) {
			BlockPos p = e.repeaters.get(idx);
			needed.add(chunkKey(p.getX() >> 4, p.getZ() >> 4));
		}

		// 新しく必要なチャンクをロード
		for (long cp : needed) {
			if (e.activeChunks.add(cp)) {
				level.setChunkForced(chunkX(cp), chunkZ(cp), true);
			}
		}
		// 不要になったチャンクをアンロード
		java.util.Iterator<Long> it = e.activeChunks.iterator();
		while (it.hasNext()) {
			long cp = it.next();
			if (!needed.contains(cp)) {
				level.setChunkForced(chunkX(cp), chunkZ(cp), false);
				it.remove();
			}
		}
	}

	/**
	 * その元(source)が建てた回路を、記録をもとに消す(ブロックを空気にして、チャンクの常時読み込みもやめる)。
	 * 建てた時の元の地形までは分からないので、床の層は草ブロック、それより上は空気になる。@return 消したブロック数
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
					// 床の層(回路の床より下)は草ブロック、それより上は空気にする
					level.setBlock(new BlockPos(e.placed[i], e.placed[i + 1], e.placed[i + 2]),
							e.placed[i + 1] < e.groundY ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2 | 16);
					n++;
				}
				for (int[] c : e.forced) {
					level.setChunkForced(c[0], c[1], false);
				}
				for (long cp : e.activeChunks) {
					level.setChunkForced(chunkX(cp), chunkZ(cp), false);
				}
				e.activeChunks.clear();
			}
			ENTRIES.remove(e);
		}
		if (!hit.isEmpty()) {
			save();
		}
		return n;
	}

	/** いちばん新しく完成した回路。なければ null */
	public static synchronized Entry last() {
		return ENTRIES.isEmpty() ? null : ENTRIES.get(ENTRIES.size() - 1);
	}

	/**
	 * 自己診断: 完成している回路ごとに、音符ブロックのうち、何個が鳴ったか(reset で数え直し)。
	 * 鳴らなかった音符ブロックの位置も、最初の 5 個まで出す。
	 */
	public static synchronized List<String> report(boolean reset) {
		List<String> out = new ArrayList<>();
		for (Entry e : ENTRIES) {
			int total = e.notes.size();
			StringBuilder sb = new StringBuilder();
			sb.append(e.source).append(": 音符ブロック ").append(total).append(" 個のうち、鳴ったのは ").append(e.firedCount).append(" 個");
			if (e.fired != null && e.firedCount < total) {
				sb.append("、鳴らなかった例:");
				int shown = 0;
				for (int i = 0; i < total && shown < 5; i++) {
					if (!e.fired[i]) {
						BlockPos p = e.notes.get(i);
						sb.append(" (").append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ()).append(')');
						shown++;
					}
				}
			}
			if (e.firstFire >= 0) {
				sb.append(String.format("  [最初 tick %d、最後 tick %d、%.1f 秒]", e.firstFire, e.lastFire, (e.lastFire - e.firstFire) / 20.0));
			}
			out.add(sb.toString());
			if (reset) {
				e.fired = null;
				e.firedCount = 0;
				e.firstFire = -1;
				e.lastFire = -1;
			}
		}
		if (out.isEmpty()) {
			out.add("完成している回路がありません");
		}
		return out;
	}

	/** その元(選曲台など)が建てた、完成済みの回路。なければ null */
	public static synchronized Entry findBySource(String source) {
		Entry found = null;
		for (Entry e : ENTRIES) {
			if (e.source.equals(source)) {
				found = e; // 同じ元の記録は、建て直すたびに消えるので 1 つのはず。念のためいちばん新しいもの
			}
		}
		return found;
	}

	/**
	 * その回路で、信号の先頭になっている幹のリピーターの番号。
	 * -1 = まだリピーターは通電していないが、始点のボタンは押されている、-2 = 流れていない
	 */
	public static synchronized int frontIndex(ServerLevel level, Entry e) {
		for (int i = e.repeaters.size() - 1; i >= 0; i--) {
			BlockPos pos = e.repeaters.get(i);
			if (!level.hasChunkAt(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.REPEATER) && state.getValue(BlockStateProperties.POWERED)) {
				return i;
			}
		}
		if (e.start != null && level.hasChunkAt(e.start) && level.getBlockState(e.start).getBlock() instanceof ButtonBlock
				&& level.getBlockState(e.start).getValue(ButtonBlock.POWERED)) {
			return -1;
		}
		return -2;
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
			boolean found = false;
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
					found = true;
					break;
				}
			}
			// まだどのリピーターも通電していなくても、始点のボタンが押されていれば「始まった」(index = -1)
			if (!found && e.start != null && level.hasChunkAt(e.start)
					&& level.getBlockState(e.start).getBlock() instanceof ButtonBlock
					&& level.getBlockState(e.start).getValue(ButtonBlock.POWERED)) {
				double d = Vec3.atCenterOf(e.repeaters.get(0)).distanceToSqr(from);
				if (d < bestDist) {
					bestDist = d;
					best = new Playing(e, -1);
				}
			}
		}
		return best;
	}

	/** 音が聞こえる範囲(ブロック)。バニラの音符ブロックは 48 だが、自前で送っているので、広げられる。範囲の外は無音 */
	public static final double RANGE = 100.0;
	/** 音が 80〜100 % で聞こえる範囲(= 範囲の 20 %)。追従は、聞く人をこの中に保つ */
	public static final double LOUD_ZONE = RANGE * 0.2;

	/**
	 * 鳴った音符ブロックの音を、聞く人ごとに、距離で小さくして聞かせる。音符ブロックの上は見えないブロックでふさいであり、
	 * バニラの音は鳴らないので、音量は、このMODが決める:
	 * <pre>音量 = 音符ごとの強さ(vel %) × 全体の音量(volumePct %) × 楽器・音域の補正(SongTrim) × (1 − 距離 / RANGE)   ← 距離 0 で 100 %、RANGE で 0 %、直線</pre>
	 * Minecraft の音は、音量 1.0 のとき「距離 16 ブロックで 0、近いほど大きい」の直線で小さくなるので、
	 * 欲しい音量になる距離(16 ×(1 − 音量))に、本来の方向のまま、音の出どころを置いて送る
	 * (音量を 1 より大きくすると範囲は広がるが、大きさは 1 で頭打ちになり、強さを決められないため)。
	 */
	private static void play(ServerLevel level, BlockPos pos, BlockState state, int vel, int volumePct, int origMidi) {
		NoteBlockInstrument inst = state.getValue(NoteBlock.INSTRUMENT);
		Holder<SoundEvent> sound = inst.getSoundEvent();
		// 打楽器は音の高さを変えない。音階楽器は、本来のMIDIノート番号があればそれに基づいてピッチを計算し、オクターブ跳躍を無くす
		float pitch;
		if (inst.isTunable()) {
			if (origMidi > 0) {
				// baseMidi が音符値 0 (F#) に対応。音符値 12 (pitch=1.0) を基準とした半音差
				double semitones = origMidi - inst.baseMidi - 12;
				pitch = (float) Math.pow(2.0, Math.max(-24.0, Math.min(24.0, semitones)) / 12.0);
			} else {
				pitch = (float) Math.pow(2.0, (state.getValue(NoteBlock.NOTE) - 12) / 12.0);
			}
		} else {
			pitch = 1.0F;
		}
		// 楽器ごと・音域ごとの、もとの音の大きさの違いを、そろえる(大きい音を下げる。SongTrim の表)
		float trim = SongTrim.of(inst.name(), state.getValue(NoteBlock.NOTE));
		double base = Math.max(0.0, Math.min(1.0, vel / 100.0 * volumePct / 100.0 * trim));
		double nx = pos.getX() + 0.5;
		double ny = pos.getY() + 0.5;
		double nz = pos.getZ() + 0.5;
		// 鳴った音符ブロックを、一瞬きらっと光らせる(近くの人に見える。音符ブロックの上は、見えないブロック)
		level.sendParticles(ParticleTypes.END_ROD, nx, pos.getY() + 1.15, nz, 3, 0.15, 0.05, 0.15, 0.01);
		for (ServerPlayer p : level.players()) {
			double dx = nx - p.getX();
			double dy = ny - p.getEyeY();
			double dz = nz - p.getZ();
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double gain = base * (1.0 - d / RANGE);
			if (gain < 0.02) {
				continue; // 遠すぎる、または小さすぎて聞こえない
			}
			double k = d > 1.0E-6 ? 16.0 * (1.0 - gain) / d : 0.0;
			p.connection.send(new ClientboundSoundPacket(sound, SoundSource.RECORDS, p.getX() + dx * k, p.getEyeY() + dy * k,
					p.getZ() + dz * k, 1.0F, pitch, level.getRandom().nextLong()));
		}
	}

	/** その元(選曲台など)の回路の、全体の音量(%)を変える。回路がなければ false */
	public static synchronized boolean setVolume(String source, int pct) {
		Entry e = findBySource(source);
		if (e == null) {
			return false;
		}
		e.volumePct = pct;
		save();
		return true;
	}
}
