package com.songmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 曲(音符のリスト)から、リピーターと音符ブロックの配置図を作る。マイクラのクラスは使わない純粋な計算。
 *
 * <p>座標は (u=前, w=右, h=上)。経路は「石ブロック B → リピーター R → B → R …」と交互に並ぶ 1 本の線で、
 * R の遅延(1〜4)の合計が時刻になる。B は R に強く駆動されるので、B の隣の音符ブロックは B と同時に鳴る。
 * 長い線は 1 列 {@code rowLen} マスで折り返す(列の間隔は 4 マス)。
 */
public final class SongPlanner {

	public static final int ROW_PITCH = 4;
	/** 始点(B0)の前後位置。手前にボタンを置く */
	public static final int START_U = 2;

	public enum Kind { SOLID, SUPPORT, REPEATER, BUTTON, NOTE_BASE, NOTE, AIR }

	/** 置くブロック 1 個。REPEATER は a,b=進む向き(u,w) c=遅延、NOTE は a=音符値 */
	public record Cell(int u, int w, int h, Kind kind, int a, int b, int c, Inst inst) { }

	public record Note(int tick, int midi, Inst forced) { }

	public static final class Plan {
		public final List<Cell> cells = new ArrayList<>();
		public int rowLen;
		public int rows;
		public int placed;
		public int dropped;
		public int folded;
		public int pairs;
		public int lengthTicks;
	}

	private record P(int u, int w, int h) { }

	/** 経路の c 番目のマス(0 始まり)の座標 {u, w} */
	static int[] pathPos(int c, int len) {
		int turn = ROW_PITCH - 1;
		int block = 2 * (len + turn); // 2 列(行き・帰り)と折り返し 2 回ぶん
		int w0 = c / block * 2 * ROW_PITCH;
		int r = c % block;
		if (r < len) {
			return new int[] { START_U + r, w0 };
		}
		r -= len;
		if (r < turn) {
			return new int[] { START_U + len - 1, w0 + 1 + r };
		}
		r -= turn;
		if (r < len) {
			return new int[] { START_U + len - 1 - r, w0 + ROW_PITCH };
		}
		r -= len;
		return new int[] { START_U, w0 + ROW_PITCH + 1 + r };
	}

	/** 1 列の長さ。幅と奥行きがほぼ正方形になるように自動で決める(奇数)。 */
	public static int autoRowLen(int cells) {
		int len = (int) Math.round(2 * Math.sqrt(cells));
		len = Math.max(21, Math.min(101, len));
		return len | 1;
	}

	/** rowLenOrZero: 0=自動、正=その長さで折り返す、負=折り返さず 1 列 */
	public static Plan plan(List<Note> notes, int rowLenOrZero) {
		Plan plan = new Plan();
		TreeMap<Integer, List<Note>> byTime = new TreeMap<>();
		for (Note n : notes) {
			byTime.computeIfAbsent(Math.max(0, n.tick()), k -> new ArrayList<>()).add(n);
		}
		// 1. 時間軸: 各時刻に B を割り当てる。pathIndex[i] = その時刻の B の経路番号。
		List<Integer> delays = new ArrayList<>(); // 各 R の遅延
		Map<Integer, Integer> tapIndex = new HashMap<>(); // 時刻 -> B の経路番号
		int idx = 0;
		int now = 0;
		for (int t : byTime.keySet()) {
			int gap = t - now;
			if (gap > 0) {
				int n = (gap + 3) / 4;
				int q = gap / n;
				int rem = gap % n;
				for (int i = 0; i < n; i++) {
					delays.add(q + (i < rem ? 1 : 0));
					idx += 2;
				}
				now = t;
			}
			tapIndex.put(t, idx);
		}
		int total = idx + 1;
		int len = rowLenOrZero < 0 ? (total | 1) : rowLenOrZero > 0 ? (rowLenOrZero | 1) : autoRowLen(total);
		plan.rowLen = len;
		plan.pairs = delays.size();
		plan.lengthTicks = now;
		plan.rows = (total + len - 1) / len;

		// 2. 経路を置く
		Map<P, Integer> pathIdx = new HashMap<>();
		for (int c = 0; c < total; c++) {
			int[] p = pathPos(c, len);
			pathIdx.put(new P(p[0], p[1], 0), c);
		}
		Map<P, Boolean> solid = new HashMap<>(); // 何かを置くマス
		Map<Integer, Integer> solidCell = new HashMap<>(); // B の経路番号 -> plan.cells の位置(上の音符の楽器に合わせて素材を替える)
		for (int c = 0; c < total; c++) {
			int[] p = pathPos(c, len);
			solid.put(new P(p[0], p[1], 0), true);
			if (c % 2 == 1) {
				int[] f = pathPos(c + 1, len);
				plan.cells.add(new Cell(p[0], p[1], -1, Kind.SUPPORT, 0, 0, 0, null));
				plan.cells.add(new Cell(p[0], p[1], 0, Kind.REPEATER, f[0] - p[0], f[1] - p[1], delays.get(c / 2), null));
				solid.put(new P(p[0], p[1], -1), true);
			} else {
				solidCell.put(c, plan.cells.size());
				plan.cells.add(new Cell(p[0], p[1], 0, Kind.SOLID, 0, 0, 0, Inst.HARP));
			}
		}
		// 始点のボタン(B0 の後ろ側)
		int[] p0 = pathPos(0, len);
		plan.cells.add(new Cell(p0[0] - 1, p0[1], 0, Kind.BUTTON, 0, 0, 0, null));
		solid.put(new P(p0[0] - 1, p0[1], 0), true);

		// 3. 音符ブロックを B の隣へ置く(横 4 か所 → 上)
		int[][] sides = { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } };
		for (Map.Entry<Integer, List<Note>> e : byTime.entrySet()) {
			int c = tapIndex.get(e.getKey());
			int[] bp = pathPos(c, len);
			for (Note n : e.getValue()) {
				Inst inst = n.forced() != null ? n.forced() : Inst.auto(n.midi());
				int note = n.midi() - inst.baseMidi;
				while (note < 0) {
					note += 12;
					plan.folded++;
				}
				while (note > 24) {
					note -= 12;
					plan.folded++;
				}
				boolean ok = false;
				for (int s = 0; s < 5 && !ok; s++) {
					if (s < 4) {
						int nu = bp[0] + sides[s][0];
						int nw = bp[1] + sides[s][1];
						// 置けるのは、このマスと下(土台)と上が空いているとき
						if (solid.containsKey(new P(nu, nw, 0)) || solid.containsKey(new P(nu, nw, -1))
								|| solid.containsKey(new P(nu, nw, 1))) {
							continue;
						}
						// 隣に別の B があると、その時刻にも鳴ってしまうので不可
						boolean spurious = false;
						for (int[] d : sides) {
							Integer other = pathIdx.get(new P(nu + d[0], nw + d[1], 0));
							if (other != null && other % 2 == 0 && other != c) {
								spurious = true;
							}
						}
						if (spurious) {
							continue;
						}
						solid.put(new P(nu, nw, -1), true);
						solid.put(new P(nu, nw, 0), true);
						plan.cells.add(new Cell(nu, nw, -1, Kind.NOTE_BASE, 0, 0, 0, inst));
						plan.cells.add(new Cell(nu, nw, 0, Kind.NOTE, note, 0, 0, inst));
						ok = true;
					} else if (!solid.containsKey(new P(bp[0], bp[1], 1))) {
						// 上に置くと土台は B 自身になるので、B をその楽器のブロックにする
						solid.put(new P(bp[0], bp[1], 1), true);
						Cell old = plan.cells.get(solidCell.get(c));
						plan.cells.set(solidCell.get(c), new Cell(old.u(), old.w(), 0, Kind.SOLID, 0, 0, 0, inst));
						plan.cells.add(new Cell(bp[0], bp[1], 1, Kind.NOTE, note, 0, 0, inst));
						ok = true;
					}
				}
				if (ok) {
					plan.placed++;
				} else {
					plan.dropped++;
				}
			}
		}
		return plan;
	}
}
