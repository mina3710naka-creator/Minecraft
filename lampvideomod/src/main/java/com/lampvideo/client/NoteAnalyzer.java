package com.lampvideo.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/**
 * 曲を解析して、「いつ、どんな音(音の高さ・打楽器)が鳴ったか」のイベントの並びにする。
 * ffmpeg で、モノラル 22.05kHz にデコードして、FFT(窓 4096・間隔 1024 = 約 46ms ごと)で、
 * ・音の出だし(スペクトルの増え方が、直近の平均より大きい)に、音の高さ(スペクトルの山。倍音は除く)を、最大 3 つ
 * ・低音の出だし = バスドラム、高音の出だし = ハイハット、中域も同時に増える = スネア
 * を、拾う。ゲーム内の音(音符ブロック・効果音)で、再合成するための元になる。画面にも、Minecraft にも依存しない。
 */
public final class NoteAnalyzer {

	public static final byte PITCHED = 0;
	public static final byte KICK = 1;
	public static final byte SNARE = 2;
	public static final byte HAT = 3;

	public static final class Events {
		public int[] timeMs = new int[1024];
		public byte[] kind = new byte[1024];
		/** 音の高さ(MIDI ノート番号。69 = A4 = 440Hz)。打楽器では使わない。 */
		public byte[] note = new byte[1024];
		/** 強さ(0〜127)。 */
		public byte[] vel = new byte[1024];
		public int size;

		public void add(int ms, byte k, int n, int v) {
			if (size == timeMs.length) {
				int cap = size * 2;
				timeMs = Arrays.copyOf(timeMs, cap);
				kind = Arrays.copyOf(kind, cap);
				note = Arrays.copyOf(note, cap);
				vel = Arrays.copyOf(vel, cap);
			}
			timeMs[size] = ms;
			kind[size] = k;
			note[size] = (byte) Math.max(0, Math.min(127, n));
			vel[size] = (byte) Math.max(1, Math.min(127, v));
			size++;
		}

		/** ms 以降の、最初のイベントの番号。 */
		public int firstIndexAtOrAfter(long ms) {
			int lo = 0;
			int hi = size;
			while (lo < hi) {
				int mid = (lo + hi) >>> 1;
				if (timeMs[mid] < ms) {
					lo = mid + 1;
				} else {
					hi = mid;
				}
			}
			return lo;
		}
	}

	private static final int RATE = 22050;
	private static final int N = 4096;
	private static final int HOP = 1024;
	private static final double BIN_HZ = (double) RATE / N;

	private NoteAnalyzer() {
	}

	private static int bin(double hz) {
		return (int) Math.round(hz / BIN_HZ);
	}

	/** ファイルを解析する。cancelled が true を返したら、途中でやめて、そこまでを返す。 */
	public static Events analyze(Path file, BooleanSupplier cancelled) throws IOException {
		Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-i", file.toString(), "-vn", "-ac", "1", "-ar", Integer.toString(RATE),
				"-f", "s16le", "-").redirectError(ProcessBuilder.Redirect.DISCARD).start();
		try (InputStream in = p.getInputStream()) {
			return analyze(in, cancelled);
		} finally {
			p.destroy();
		}
	}

	/** 16bit モノラル 22.05kHz のサンプルの流れを解析する(テストしやすいよう、分けてある)。 */
	static Events analyze(InputStream in, BooleanSupplier cancelled) throws IOException {
		Events ev = new Events();
		final int half = N / 2;
		float[] window = new float[N];
		for (int i = 0; i < N; i++) {
			window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)));
		}
		float[] samples = new float[N];
		float[] re = new float[N];
		float[] im = new float[N];
		float[] mag = new float[half + 1];
		float[] prev = new float[half + 1];
		int lowLo = bin(35);
		int lowHi = bin(150);
		int midLo = bin(150);
		int midHi = bin(2000);
		int highLo = bin(4000);
		int highHi = Math.min(half, bin(10000));
		int pitchLo = bin(80);

		double avgLow = 0;
		double avgMid = 0;
		double avgHigh = 0;
		double globalPeak = 1e-6;
		int frame = 0;
		int lastKick = -100;
		int lastSnare = -100;
		int lastHat = -100;
		int lastPitched = -100;
		int lastSustain = -100;

		byte[] buf = new byte[HOP * 2];
		int filled = 0;   // samples の、埋まっている数
		boolean first = true;
		while (cancelled == null || !cancelled.getAsBoolean()) {
			int need = first ? N : HOP;
			byte[] chunk = need == buf.length ? buf : new byte[need * 2];
			int got = readFully(in, chunk, need * 2);
			if (got < need * 2) {
				break;
			}
			if (first) {
				for (int i = 0; i < N; i++) {
					samples[i] = ((chunk[2 * i + 1] << 8) | (chunk[2 * i] & 0xFF)) / 32768f;
				}
				first = false;
			} else {
				System.arraycopy(samples, HOP, samples, 0, N - HOP);
				for (int i = 0; i < HOP; i++) {
					samples[N - HOP + i] = ((chunk[2 * i + 1] << 8) | (chunk[2 * i] & 0xFF)) / 32768f;
				}
			}
			for (int i = 0; i < N; i++) {
				re[i] = samples[i] * window[i];
				im[i] = 0f;
			}
			fft(re, im);
			for (int k = 0; k <= half; k++) {
				mag[k] = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
			}
			// 周波数の帯ごとの、増え方(スペクトルフラックス)
			double fLow = flux(mag, prev, lowLo, lowHi);
			double fMid = flux(mag, prev, midLo, midHi);
			double fHigh = flux(mag, prev, highLo, highHi);
			System.arraycopy(mag, 0, prev, 0, half + 1);

			int ms = (int) ((long) frame * HOP * 1000L / RATE) + (int) (N / 2 * 1000L / RATE);
			boolean midOnset = fMid > Math.max(4.0, 1.7 * avgMid) && frame - lastPitched >= 2;
			boolean lowOnset = fLow > Math.max(3.0, 1.9 * avgLow) && frame - lastKick >= 3;
			boolean highOnset = fHigh > Math.max(1.5, 1.9 * avgHigh);

			// 音の高さの山を拾う
			double peakMax = 0;
			for (int k = pitchLo; k < midHi; k++) {
				peakMax = Math.max(peakMax, mag[k]);
			}
			globalPeak = Math.max(globalPeak * 0.9995, peakMax);

			boolean sustain = !midOnset && frame - lastSustain >= 8 && frame - lastPitched >= 6 && peakMax > 0.35 * globalPeak
					&& peakMax > 6.0;
			if ((midOnset || sustain) && peakMax > 3.0) {
				int[] picked = new int[3];
				double[] pickedHz = new double[3];
				double[] pickedMag = new double[3];
				int count = 0;
				for (int round = 0; round < 3; round++) {
					int best = -1;
					double bestMag = 0.3 * peakMax;
					for (int k = pitchLo + 1; k < midHi - 1; k++) {
						if (mag[k] > mag[k - 1] && mag[k] >= mag[k + 1] && mag[k] > bestMag) {
							double hz = interpolate(mag, k);
							if (!isHarmonic(hz, pickedHz, count) && !alreadyPicked(hz, pickedHz, count)) {
								best = k;
								bestMag = mag[k];
							}
						}
					}
					if (best < 0) {
						break;
					}
					pickedHz[count] = interpolate(mag, best);
					pickedMag[count] = mag[best];
					picked[count] = (int) Math.round(69 + 12 * Math.log(pickedHz[count] / 440.0) / Math.log(2));
					count++;
				}
				for (int i = 0; i < count; i++) {
					double v = Math.min(1.0, pickedMag[i] / globalPeak);
					ev.add(ms, PITCHED, picked[i], (int) ((midOnset ? 40 : 28) + 87 * v));
				}
				if (count > 0) {
					lastPitched = frame;
					if (!midOnset) {
						lastSustain = frame;
					}
				}
			}
			if (lowOnset && fLow > 0.8 * fMid * 0.3) {
				ev.add(ms, KICK, 0, (int) Math.min(127, 50 + fLow * 3));
				lastKick = frame;
			}
			if (highOnset) {
				if (fMid > 1.3 * Math.max(avgMid, 2.0) && frame - lastSnare >= 3) {
					ev.add(ms, SNARE, 0, (int) Math.min(127, 50 + fHigh * 3));
					lastSnare = frame;
				} else if (frame - lastHat >= 2) {
					ev.add(ms, HAT, 0, (int) Math.min(110, 40 + fHigh * 3));
					lastHat = frame;
				}
			}
			avgLow = 0.95 * avgLow + 0.05 * fLow;
			avgMid = 0.95 * avgMid + 0.05 * fMid;
			avgHigh = 0.95 * avgHigh + 0.05 * fHigh;
			frame++;
		}
		return ev;
	}

	private static double flux(float[] mag, float[] prev, int lo, int hi) {
		double s = 0;
		for (int k = lo; k < hi; k++) {
			double d = mag[k] - prev[k];
			if (d > 0) {
				s += d;
			}
		}
		return s;
	}

	/** 山の頂点の周波数(3点の放物線で、ビンの間まで細かく)。 */
	private static double interpolate(float[] mag, int k) {
		double a = mag[k - 1];
		double b = mag[k];
		double c = mag[k + 1];
		double denom = a - 2 * b + c;
		double shift = Math.abs(denom) < 1e-9 ? 0 : 0.5 * (a - c) / denom;
		return (k + Math.max(-0.5, Math.min(0.5, shift))) * BIN_HZ;
	}

	/** すでに拾った音の、2倍・3倍・4倍の周波数(倍音)か。 */
	private static boolean isHarmonic(double hz, double[] picked, int count) {
		for (int i = 0; i < count; i++) {
			for (int m = 2; m <= 4; m++) {
				if (Math.abs(hz - picked[i] * m) < picked[i] * m * 0.03) {
					return true;
				}
			}
		}
		return false;
	}

	/** すでに拾った音と、ほぼ同じ高さ(半音の 1/2 以内)か。 */
	private static boolean alreadyPicked(double hz, double[] picked, int count) {
		for (int i = 0; i < count; i++) {
			if (Math.abs(Math.log(hz / picked[i]) / Math.log(2) * 12) < 0.7) {
				return true;
			}
		}
		return false;
	}

	private static int readFully(InputStream in, byte[] b, int len) throws IOException {
		int off = 0;
		while (off < len) {
			int n = in.read(b, off, len - off);
			if (n < 0) {
				break;
			}
			off += n;
		}
		return off;
	}

	/** 基数2の FFT(その場で)。 */
	private static void fft(float[] re, float[] im) {
		int n = re.length;
		for (int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for (; (j & bit) != 0; bit >>= 1) {
				j ^= bit;
			}
			j ^= bit;
			if (i < j) {
				float t = re[i];
				re[i] = re[j];
				re[j] = t;
				t = im[i];
				im[i] = im[j];
				im[j] = t;
			}
		}
		for (int len = 2; len <= n; len <<= 1) {
			double ang = -2 * Math.PI / len;
			float wr = (float) Math.cos(ang);
			float wi = (float) Math.sin(ang);
			for (int i = 0; i < n; i += len) {
				float cr = 1f;
				float ci = 0f;
				for (int k = 0; k < len / 2; k++) {
					int a = i + k;
					int b = i + k + len / 2;
					float tr = re[b] * cr - im[b] * ci;
					float ti = re[b] * ci + im[b] * cr;
					re[b] = re[a] - tr;
					im[b] = im[a] - ti;
					re[a] += tr;
					im[a] += ti;
					float ncr = cr * wr - ci * wi;
					ci = cr * wi + ci * wr;
					cr = ncr;
				}
			}
		}
	}
}
