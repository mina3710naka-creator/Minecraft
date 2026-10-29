package com.povreplay;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;

/** 16bit にそろえた音のデータ(チャンネルは 1 か 2)。 */
public final class Pcm {
	final short[] data;
	final int channels;
	final float rate;

	Pcm(short[] data, int channels, float rate) {
		this.data = data;
		this.channels = channels;
		this.rate = rate;
	}

	int frames() {
		return data.length / channels;
	}

	/** ゲームの音のバッファから、変換して取り出す。バッファの位置などは変えない。 */
	public static Pcm of(ByteBuffer source, AudioFormat format) {
		ByteBuffer b = source.duplicate().order(source.order());
		int channels = Math.max(1, Math.min(2, format.getChannels()));
		short[] data;
		if (format.getSampleSizeInBits() == 8) {
			data = new short[b.remaining()];
			for (int i = 0; i < data.length; i++) {
				data[i] = (short) (((b.get() & 0xFF) - 128) << 8);
			}
		} else {
			data = new short[b.remaining() / 2];
			b.asShortBuffer().get(data);
		}
		if (data.length % channels != 0) {
			data = java.util.Arrays.copyOf(data, data.length - data.length % channels);
		}
		return new Pcm(data, channels, format.getSampleRate());
	}
}
