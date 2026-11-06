package com.songmod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 曲(音符のリスト)から、リピーター・赤石粉・音符ブロックの配置図を作る。マイクラのクラスは使わない純粋な計算。
 *
 * <p>座標は (u=前, w=右, h=上)。床(h=0)に平らに並べる。
 * <pre>
 *   幹(w=0 の一直線): ボタン → [石] → R(4) R(4) R(3) → 粉粉粉 … R(4) → 粉粉粉 … R(4) → …
 *   枝(分岐点の粉から左右へ):  粉 → R(遅延 d+1) → [ブロック] → 音符ブロック(前・手前・奥・上)
 * </pre>
 * 幹のリピーターは最大の遅延 4(0.4 秒)で固定。幹の粉のマスが分岐点で、同じ時刻の枝を何本でも出せる。
 * 音符の時刻 t は「幹の区間 k = t / 4」と「区間の中のずれ d = t % 4」に分け、枝のリピーターの遅延 d+1 で
 * 0.1 秒単位に合わせる(幹の分岐点は、区間の始まりの 1 目盛り前に置いてある)。
 * 最初のボタンから最初の分岐点までは、リピーター 3 個(1.1 秒)の猶予がある。
 */
public final class SongPlanner {

	/** 始点(石)の前後位置。手前にボタンを置く */
	public static final int START_U = 2;
	/** 幹のリピーターの遅延(最大) */
	public static final int SEGMENT = 4;
	/** ボタンから最初の分岐点までの猶予: リピーター 4+4+3 = 11 目盛り */
	public static final int[] LEAD = { 4, 4, 3 };
	public static final int LEAD_TICKS = 11;
	/** 1 つの枝の取り付け位置が使う、幹のマス数(隣の枝の音符ブロックとぶつからない間隔) */
	public static final int SLOT = 3;
	/** 1 つの区間(0.4 秒)に出せる枝の数。幹の粉は 15 マスまでしか信号を運べないので、4 か所(12 マス)× 左右 */
	public static final int MAX_BRANCHES = 8;

	public enum Kind { SOLID, SUPPORT, REPEATER, SPINE_REPEATER, DUST, BUTTON, NOTE_BASE, NOTE, AIR }

	/**
	 * 置くブロック 1 個。
	 * REPEATER / SPINE_REPEATER は a,b=進む向き(u,w) c=遅延 t=(幹のみ)通電する時刻。
	 * NOTE は a=音符値。DUST は a=つながる向きのビット(1=+u 2=-u 4=+w 8=-w)。
	 */
	public record Cell(int u, int w, int h, Kind kind, int a, int b, int c, int t, Inst inst) { }

	public record Note(int tick, int midi, Inst forced) { }

	public static final class Plan {
		public final List<Cell> cells = new ArrayList<>();
		/** 幹の長さ(マス) */
		public int rowLen;
		public int rows = 1;
		public int placed;
		public int dropped;
		public int folded;
		public int pairs;
		/** 曲の長さ(リピーター 1 目盛り単位) */
		public int lengthTicks;
	}

	private record P(int u, int w, int h) { }

	/** 枝 1 本ぶんの音符(最大 4 つ: 前・手前・奥の 3 つと、ブロックの上の 1 つ) */
	private record Branch(int d, List<Placed> notes) { }

	private record Placed(Inst inst, int value) { }

	public static Plan plan(List<Note> notes, int ignoredRowLength) {
		Plan plan = new Plan();
		Set<P> used = new HashSet<>();

		// 1. 区間 k とずれ d に分ける
		TreeMap<Integer, TreeMap<Integer, List<Placed>>> seg = new TreeMap<>();
		for (Note n : notes) {
			int t = Math.max(0, n.tick());
			Inst inst = n.forced() != null ? n.forced() : Inst.auto(n.midi());
			int v = inst.percussion() ? 0 : n.midi() - inst.baseMidi; // 打楽器は音の高さがない
			while (v < 0) {
				v += 12;
				plan.folded++;
			}
			while (v > 24) {
				v -= 12;
				plan.folded++;
			}
			seg.computeIfAbsent(t / SEGMENT, k -> new TreeMap<>()).computeIfAbsent(t % SEGMENT, d -> new ArrayList<>())
					.add(new Placed(inst, v));
		}
		int lastK = seg.isEmpty() ? 0 : seg.lastKey();
		plan.lengthTicks = SEGMENT * lastK + SEGMENT;

		// 2. 始点: 石 + 手前のボタン、続けて猶予のリピーター
		int u = START_U;
		put(plan, used, new Cell(u, 0, 0, Kind.SOLID, 0, 0, 0, 0, Inst.HARP));
		put(plan, used, new Cell(u - 1, 0, 0, Kind.BUTTON, 0, 0, 0, 0, null));
		int time = 0;
		for (int d : LEAD) {
			u++;
			time += d;
			support(plan, used, u, 0);
			put(plan, used, new Cell(u, 0, 0, Kind.SPINE_REPEATER, 1, 0, d, time, null));
			plan.pairs++;
		}

		// 3. 区間ごとに、分岐点(粉のマス)と、区間をつなぐ幹のリピーター
		for (int k = 0; k <= lastK; k++) {
			List<Branch> branches = branchesOf(seg.get(k));
			// 幹の粉が 15 マスより長く続くと信号が切れるので、1 区間の枝は MAX_BRANCHES(= 4 か所 × 左右)まで
			while (branches.size() > MAX_BRANCHES) {
				plan.dropped += branches.remove(branches.size() - 1).notes().size();
			}
			int slots = (branches.size() + 1) / 2;
			for (int j = 0; j < slots; j++) {
				int u0 = u + 1;
				for (int i = 0; i < SLOT; i++) {
					support(plan, used, u0 + i, 0);
					put(plan, used, new Cell(u0 + i, 0, 0, Kind.DUST, 0, 0, 0, 0, null));
				}
				u = u0 + SLOT - 1;
				for (int side = 0; side < 2 && 2 * j + side < branches.size(); side++) {
					branch(plan, used, u0, side == 0 ? 1 : -1, branches.get(2 * j + side));
				}
			}
			if (k < lastK) {
				u++;
				time = LEAD_TICKS + SEGMENT * (k + 1);
				support(plan, used, u, 0);
				put(plan, used, new Cell(u, 0, 0, Kind.SPINE_REPEATER, 1, 0, SEGMENT, time, null));
				plan.pairs++;
			}
		}
		plan.rowLen = u - START_U + 1;
		connectDust(plan);
		return plan;
	}

	/** 同じずれ d の音符を、4 つずつの枝に分ける */
	private static List<Branch> branchesOf(TreeMap<Integer, List<Placed>> byOffset) {
		List<Branch> out = new ArrayList<>();
		if (byOffset == null) {
			return out;
		}
		for (Map.Entry<Integer, List<Placed>> e : byOffset.entrySet()) {
			List<Placed> rest = new ArrayList<>(e.getValue());
			while (!rest.isEmpty()) {
				List<Placed> chunk = new ArrayList<>();
				while (chunk.size() < 3 && !rest.isEmpty()) {
					chunk.add(rest.remove(0));
				}
				// 4 つ目は、ブロックの上に置く(ブロックがその楽器の素材になるので、レッドストーンを通す楽器だけ)
				for (int i = 0; i < rest.size(); i++) {
					if (rest.get(i).inst().conductor) {
						chunk.add(rest.remove(i));
						break;
					}
				}
				out.add(new Branch(e.getKey(), chunk));
			}
		}
		return out;
	}

	/** 分岐点 (u0, 0) から side(+1 右 / -1 左)へ: 粉 → リピーター(d+1) → ブロック → 音符ブロック */
	private static void branch(Plan plan, Set<P> used, int u0, int side, Branch b) {
		support(plan, used, u0, side);
		put(plan, used, new Cell(u0, side, 0, Kind.DUST, 0, 0, 0, 0, null));
		support(plan, used, u0, 2 * side);
		put(plan, used, new Cell(u0, 2 * side, 0, Kind.REPEATER, 0, side, b.d() + 1, 0, null));
		int[][] flat = { { u0, 4 * side }, { u0 - 1, 3 * side }, { u0 + 1, 3 * side } };
		Inst top = Inst.HARP;
		for (int i = 0; i < b.notes().size(); i++) {
			Placed n = b.notes().get(i);
			if (i < 3) {
				put(plan, used, new Cell(flat[i][0], flat[i][1], -1, Kind.NOTE_BASE, 0, 0, 0, 0, n.inst()));
				if (n.inst().falls) {
					// 砂は、下に支えがないと落ちるので、土台の下にも 1 段入れる
					put(plan, used, new Cell(flat[i][0], flat[i][1], -2, Kind.SUPPORT, 0, 0, 0, 0, null));
				}
				put(plan, used, new Cell(flat[i][0], flat[i][1], 0, Kind.NOTE, n.value(), 0, 0, 0, n.inst()));
			} else {
				top = n.inst();
				put(plan, used, new Cell(u0, 3 * side, 1, Kind.NOTE, n.value(), 0, 0, 0, n.inst()));
			}
			plan.placed++;
		}
		put(plan, used, new Cell(u0, 3 * side, 0, Kind.SOLID, 0, 0, 0, 0, top));
	}

	/** 粉・リピーターの床(石) */
	private static void support(Plan plan, Set<P> used, int u, int w) {
		put(plan, used, new Cell(u, w, -1, Kind.SUPPORT, 0, 0, 0, 0, null));
	}

	private static void put(Plan plan, Set<P> used, Cell c) {
		if (!used.add(new P(c.u(), c.w(), c.h()))) {
			throw new IllegalStateException("配置が重なりました: " + c);
		}
		plan.cells.add(c);
	}

	/** 粉は、隣の粉、または向きの合うリピーターへつながる(見た目と、つながりの状態を合わせる) */
	private static void connectDust(Plan plan) {
		java.util.Map<P, Cell> at = new java.util.HashMap<>();
		for (Cell c : plan.cells) {
			if (c.h() == 0) {
				at.put(new P(c.u(), c.w(), 0), c);
			}
		}
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		for (int i = 0; i < plan.cells.size(); i++) {
			Cell c = plan.cells.get(i);
			if (c.kind() != Kind.DUST) {
				continue;
			}
			int mask = 0;
			for (int d = 0; d < 4; d++) {
				Cell n = at.get(new P(c.u() + dirs[d][0], c.w() + dirs[d][1], 0));
				if (n == null) {
					continue;
				}
				boolean rep = n.kind() == Kind.REPEATER || n.kind() == Kind.SPINE_REPEATER;
				// リピーターは、進む向きが同じ軸のときだけつながる
				if (n.kind() == Kind.DUST || rep && (n.a() != 0) == (dirs[d][0] != 0)) {
					mask |= 1 << d;
				}
			}
			plan.cells.set(i, new Cell(c.u(), c.w(), c.h(), c.kind(), mask, 0, 0, 0, null));
		}
	}
}
