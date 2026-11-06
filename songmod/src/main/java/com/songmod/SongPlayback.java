package com.songmod;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 再生位置の操作: 頭から・早送り/巻き戻し・一時停止/再開。
 *
 * <p>幹のリピーターは一直線で、それぞれが通電する時刻が分かっている。だから、好きな時刻に近い幹のリピーターを
 * 通電させるだけで、そこから先が(遅延も含めて)正しいタイミングで流れる。
 * 流れている途中で動かすときは、先に休符と同じ要領で信号を止め、1 秒後に戻して、入れ直す。
 */
public final class SongPlayback {

	public static final int RESTART = 0;
	public static final int SEEK = 1;
	public static final int PAUSE = 2;

	/** 一時停止中の回路 → 再開する幹のリピーターの時刻 */
	private static final Map<SongRegistry.Entry, Integer> PAUSED = new HashMap<>();

	private SongPlayback() {
	}

	/** 画面に出す再生位置(秒)。最初の音までの猶予は含めない */
	public static double seconds(int time) {
		return Math.max(0, time - SongPlanner.LEAD_TICKS - 1) / 10.0;
	}

	public static synchronized boolean paused(SongRegistry.Entry e) {
		return e != null && PAUSED.containsKey(e);
	}

	/** いまの再生位置(幹のリピーターの時刻)。流れていなくて一時停止でもなければ -1 */
	public static synchronized int currentTime(ServerLevel level, SongRegistry.Entry e) {
		if (e == null || !e.canFollow()) {
			return -1;
		}
		int front = SongRegistry.frontIndex(level, e);
		if (front >= 0) {
			return e.times[front];
		}
		if (front == -1) {
			return 0;
		}
		Integer p = PAUSED.get(e);
		return p == null ? -1 : p;
	}

	/** その時刻以降で、最初の幹のリピーターの番号。なければ -1 */
	static int indexAtOrAfter(SongRegistry.Entry e, int time) {
		for (int i = 0; i < e.times.length; i++) {
			if (e.times[i] >= time) {
				return i;
			}
		}
		return -1;
	}

	/** その番号の幹のリピーターを通電させて、そこから先を流す */
	static void inject(ServerLevel level, SongRegistry.Entry e, int idx) {
		if (idx < 0 || idx >= e.repeaters.size()) {
			return;
		}
		BlockPos pos = e.repeaters.get(idx);
		if (!level.hasChunkAt(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.REPEATER)) {
			return;
		}
		level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
		// 入力が無いので、遅延ぶんの長さだけ通電して切れる(その間に、前のマスへ信号が出ていく)
		level.scheduleTick(pos, Blocks.REPEATER, 2 * state.getValue(BlockStateProperties.DELAY));
	}

	/** @return プレイヤーに見せるメッセージ */
	public static synchronized String handle(ServerLevel level, SongRegistry.Entry e, int action, int value) {
		if (e == null) {
			return "この台で建てた回路がありません(先に上のボタンで建ててください)";
		}
		if (!e.canFollow()) {
			return "この回路は古い形式なので、再生位置を操作できません(建て直してください)";
		}
		int front = SongRegistry.frontIndex(level, e);
		boolean playing = front > -2;
		Integer pausedAt = PAUSED.get(e);
		int target;
		String what;
		switch (action) {
			case RESTART -> {
				target = 0;
				what = "頭から再生します";
			}
			case SEEK -> {
				int base = playing ? (front >= 0 ? e.times[front] : 0) : pausedAt != null ? pausedAt : 0;
				target = Math.max(0, Math.min(e.endTime(), base + value * 10));
				what = (value >= 0 ? "+" : "") + value + " 秒: " + fmt(seconds(target)) + " から再生します";
			}
			case PAUSE -> {
				if (playing) {
					// 先頭のリピーターの出力は、すでに次の分岐点へ出ているので、再開はその次のリピーターから
					int next = front + 1;
					PAUSED.put(e, next < e.times.length ? e.times[next] : e.endTime() + 1);
					if (!SongRest.breakAround(level, e, front, -1)) {
						PAUSED.remove(e);
						return "止められませんでした(前後にリピーターがありません)";
					}
					return "一時停止しました(" + fmt(seconds(PAUSED.get(e))) + ")。もう一度押すと続きから再生します";
				}
				if (pausedAt == null) {
					return "いまは流れていません";
				}
				target = pausedAt;
				what = fmt(seconds(target)) + " から再開します";
			}
			default -> {
				return "不明な操作です";
			}
		}
		PAUSED.remove(e);
		int idx = indexAtOrAfter(e, target);
		if (idx < 0) {
			return "曲の終わりです";
		}
		if (playing && front >= 0 && SongRest.breakAround(level, e, front, idx)) {
			return what + "(信号を止めて、1 秒後に始まります)";
		}
		inject(level, e, idx);
		return what;
	}

	private static String fmt(double sec) {
		int s = (int) Math.round(sec);
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
