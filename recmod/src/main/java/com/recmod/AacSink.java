package com.recmod;

import java.io.IOException;

/** AudioMixer が混ぜた音(16bit ステレオ)を、AAC の 1 フレーム(1024 サンプル)ずつにして、MP4 へ渡す。 */
final class AacSink {

	private final Mp4Writer mp4;
	private final AacEncoder encoder = new AacEncoder();
	private final short[] pending = new short[AacEncoder.FRAME * 2];
	private int filled;

	AacSink(Mp4Writer mp4) {
		this.mp4 = mp4;
	}

	/** pcm は、リトルエンディアン 16bit の左右交互。length はバイト数。音のスレッドから呼ばれる。 */
	synchronized void accept(byte[] pcm, int length) {
		for (int i = 0; i + 1 < length; i += 2) {
			pending[filled++] = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
			if (filled == pending.length) {
				emit();
			}
		}
	}

	/** 端数が残っていれば、無音でうめて、最後の 1 フレームにする。 */
	synchronized void flush() {
		if (filled > 0) {
			java.util.Arrays.fill(pending, filled, pending.length, (short) 0);
			emit();
		}
	}

	private void emit() {
		filled = 0;
		try {
			mp4.writeAudio(encoder.encode(pending, 0));
		} catch (IOException | RuntimeException e) {
			Recorder.LOGGER.error("音を AAC にできませんでした", e);
		}
	}
}
