package com.songmod;

/** 選曲台で建てるときの設定の選択肢(一覧画面のボタンで、順に切り替える)。 */
final class SongOptions {

	static final int[] SPEEDS = { 50, 75, 90, 100, 110, 125, 150, 200 };
	static final int[] TRANSPOSES = { -12, -7, -5, -2, -1, 0, 1, 2, 5, 7, 12 };
	/** 音色: 空 = 自動(低音はベース、中音はハープ、高音はベル)。ほかは、音程のある音をすべてその楽器にする */
	static final String[] INSTS = { "", "harp", "guitar", "bell", "flute", "xylophone", "iron_xylophone", "bit", "banjo" };
	static final String[] INST_LABELS = { "自動", "ハープ", "ギター", "ベル", "フルート", "シロフォン", "鉄琴", "ピコピコ", "バンジョー" };

	private SongOptions() {
	}

	/** 一覧の中の次の値(いまの値が一覧にないときは、いちばん近い値の次) */
	static int next(int[] list, int cur) {
		int best = 0;
		for (int i = 0; i < list.length; i++) {
			if (Math.abs(list[i] - cur) < Math.abs(list[best] - cur)) {
				best = i;
			}
		}
		return list[(best + 1) % list.length];
	}

	static String nextInst(String cur) {
		for (int i = 0; i < INSTS.length; i++) {
			if (INSTS[i].equals(cur)) {
				return INSTS[(i + 1) % INSTS.length];
			}
		}
		return INSTS[0];
	}

	static String label(String inst) {
		for (int i = 0; i < INSTS.length; i++) {
			if (INSTS[i].equals(inst)) {
				return INST_LABELS[i];
			}
		}
		return INST_LABELS[0];
	}
}
