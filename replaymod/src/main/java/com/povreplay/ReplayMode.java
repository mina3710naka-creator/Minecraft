package com.povreplay;

/** リプレイの再生中(書き出し中)かどうか。再生中は、通信の記録をしない・ゲームの時計を仮想の時計にする、などの切り替えに使う。 */
public final class ReplayMode {
	private static volatile boolean active;
	private static volatile long virtualMillis;

	private ReplayMode() {
	}

	public static boolean active() {
		return active;
	}

	public static void setActive(boolean value) {
		active = value;
	}

	/** 再生中のゲームの時計(ミリ秒)。 */
	public static long virtualMillis() {
		return virtualMillis;
	}

	public static void setVirtualMillis(long value) {
		virtualMillis = value;
	}
}
