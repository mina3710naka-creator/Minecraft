package com.recmod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * AAC-LC(44.1kHz・ステレオ)のエンコーダー。長い窓だけ、スペクトルのハフマン表は 11 番だけ、スケールファクターは
 * どのバンドも同じ、という最小の作りで、音質の細かい調整(聴覚モデル)はしない。ゲームの音を、ふつうのプレーヤーで
 * 聞ける形(AAC)にするのが目的。
 * ハフマン表は、JAAD(パブリックドメイン)の表から取り出したもの(resources/recmod/aac_tables.txt)。
 */
final class AacEncoder {

	static final int FRAME = 1024;
	/** 44.1kHz の、スケールファクターバンドの区切り(長い窓)。 */
	private static final int[] SWB = { 0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 48, 56, 64, 72, 80, 88, 96, 108, 120, 132, 144,
			160, 176, 196, 216, 240, 264, 292, 320, 352, 384, 416, 448, 480, 512, 544, 576, 608, 640, 672, 704, 736, 768, 800,
			832, 864, 896, 928, 1024 };
	private static final int BANDS = SWB.length - 1;

	/** 取り出せる最大の量子化値(ハフマン表 11 の、エスケープで表せる範囲)。 */
	private static final int MAX_Q = 8191;

	// ハフマン表: 値のペア(0〜16)→ {長さ, 符号}
	private static final int[][] PAIR_CODE = new int[17 * 17][];
	// スケールファクター(差 0)の符号
	private static final int[] SF_ZERO;

	static {
		try (InputStream in = AacEncoder.class.getResourceAsStream("/recmod/aac_tables.txt")) {
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			String section = "";
			int[] sfZero = null;
			while ((line = r.readLine()) != null) {
				if (line.startsWith("#")) {
					section = line.split(" ")[1];
					continue;
				}
				String[] p = line.trim().split(" ");
				if (section.equals("HCB11")) {
					int a = Integer.parseInt(p[2]);
					int b = Integer.parseInt(p[3]);
					PAIR_CODE[a * 17 + b] = new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]) };
				} else if (section.equals("HCB_SF") && Integer.parseInt(p[2]) == 60) {
					sfZero = new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]) };
				}
			}
			SF_ZERO = sfZero;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// MDCT(2048 点 → 1024 本)の、cos の表と窓
	private static final float[] COS = new float[FRAME * 2 * FRAME];
	private static final float[] WINDOW = new float[FRAME * 2];

	static {
		double n0 = (FRAME + 1) / 2.0;       // (N/2 + 1)/2 で N = 2048
		for (int n = 0; n < FRAME * 2; n++) {
			WINDOW[n] = (float) Math.sin(Math.PI * (n + 0.5) / (FRAME * 2));
		}
		for (int k = 0; k < FRAME; k++) {
			for (int n = 0; n < FRAME * 2; n++) {
				COS[k * FRAME * 2 + n] = (float) Math.cos(2 * Math.PI / (FRAME * 2) * (n + n0) * (k + 0.5));
			}
		}
	}

	/** MDCT の結果を、デコーダーの音量に合わせるための係数(ffmpeg で復号して、合わせた値)。 */
	private static final double SCALE = 2.0;

	private final float[][] previous = new float[2][FRAME];

	/** 1024 サンプルぶんのステレオ(左右交互の 16bit)を、AAC の 1 フレーム(raw_data_block)にする。 */
	byte[] encode(short[] pcm, int offset) {
		float[][] spectrum = new float[2][FRAME];
		float[] block = new float[FRAME * 2];
		for (int ch = 0; ch < 2; ch++) {
			float[] prev = previous[ch];
			float[] cur = new float[FRAME];
			for (int i = 0; i < FRAME; i++) {
				cur[i] = pcm[offset + i * 2 + ch];
			}
			for (int i = 0; i < FRAME; i++) {
				block[i] = prev[i] * WINDOW[i];
				block[FRAME + i] = cur[i] * WINDOW[FRAME + i];
			}
			for (int k = 0; k < FRAME; k++) {
				float sum = 0;
				int base = k * FRAME * 2;
				for (int n = 0; n < FRAME * 2; n++) {
					sum += block[n] * COS[base + n];
				}
				spectrum[ch][k] = (float) (sum * SCALE);
			}
			previous[ch] = cur;
		}

		int[][] q = new int[2][FRAME];
		int[] gain = new int[2];
		int maxSfb = 0;
		for (int ch = 0; ch < 2; ch++) {
			gain[ch] = chooseGain(spectrum[ch]);
			quantize(spectrum[ch], gain[ch], q[ch]);
			for (int b = BANDS - 1; b >= 0; b--) {
				if (!bandIsZero(q[ch], b)) {
					maxSfb = Math.max(maxSfb, b + 1);
					break;
				}
			}
		}

		BitWriter w = new BitWriter();
		w.put(1, 3);         // CPE
		w.put(0, 4);         // element_instance_tag
		w.put(1, 1);         // common_window
		w.put(0, 1);         // ics_reserved_bit
		w.put(0, 2);         // ONLY_LONG_SEQUENCE
		w.put(0, 1);         // window_shape(サイン窓)
		w.put(maxSfb, 6);
		w.put(0, 1);         // predictor_data_present
		w.put(0, 2);         // ms_mask_present
		for (int ch = 0; ch < 2; ch++) {
			writeChannel(w, q[ch], gain[ch], maxSfb);
		}
		w.put(7, 3);         // END
		return w.toBytes();
	}

	/** 音の大きさに合わせて、量子化の細かさ(global_gain)を決める。大きい音のときは粗く、小さい音のときは細かく。 */
	private static int chooseGain(float[] spec) {
		double energy = 0;
		for (float v : spec) {
			energy += (double) v * v;
		}
		double rms = Math.sqrt(energy / FRAME);
		// 誤差の大きさを、音の大きさの約 1/80(-38dB)にする。静かな音でも、極端に細かくしない
		double step = Math.max(rms / 80.0, 0.5);
		int g = (int) Math.round(100 + 4 * Math.log(step) / Math.log(2));
		return Math.max(40, Math.min(200, g));
	}

	private static void quantize(float[] spec, int gain, int[] out) {
		double step = Math.pow(2, (gain - 100) / 4.0);
		for (int k = 0; k < FRAME; k++) {
			double a = Math.abs(spec[k]) / step;
			int v = (int) Math.min(MAX_Q, Math.floor(Math.pow(a, 0.75) + 0.4054));
			out[k] = spec[k] < 0 ? -v : v;
		}
	}

	private static boolean bandIsZero(int[] q, int band) {
		for (int k = SWB[band]; k < SWB[band + 1]; k++) {
			if (q[k] != 0) {
				return false;
			}
		}
		return true;
	}

	private static void writeChannel(BitWriter w, int[] q, int gain, int maxSfb) {
		w.put(gain, 8);
		// セクション: 全部 0 のバンドはコードブック 0、それ以外は 11。同じものが続く間を 1 つにまとめる
		List<int[]> sections = new ArrayList<>();   // {コードブック, バンド数}
		for (int b = 0; b < maxSfb; b++) {
			int cb = bandIsZero(q, b) ? 0 : 11;
			if (!sections.isEmpty() && sections.get(sections.size() - 1)[0] == cb) {
				sections.get(sections.size() - 1)[1]++;
			} else {
				sections.add(new int[] { cb, 1 });
			}
		}
		for (int[] s : sections) {
			w.put(s[0], 4);
			int len = s[1];
			while (len >= 31) {
				w.put(31, 5);
				len -= 31;
			}
			w.put(len, 5);
		}
		// スケールファクター: どのバンドも global_gain と同じ(差 0)
		for (int[] s : sections) {
			if (s[0] != 0) {
				for (int i = 0; i < s[1]; i++) {
					w.put(SF_ZERO[1], SF_ZERO[0]);
				}
			}
		}
		w.put(0, 1);         // pulse_data_present
		w.put(0, 1);         // tns_data_present
		w.put(0, 1);         // gain_control_data_present
		for (int b = 0; b < maxSfb; b++) {
			if (bandIsZero(q, b)) {
				continue;
			}
			for (int k = SWB[b]; k < SWB[b + 1]; k += 2) {
				writePair(w, q[k], q[k + 1]);
			}
		}
	}

	private static void writePair(BitWriter w, int a, int b) {
		int ma = Math.abs(a);
		int mb = Math.abs(b);
		int[] code = PAIR_CODE[Math.min(ma, 16) * 17 + Math.min(mb, 16)];
		w.put(code[1], code[0]);
		if (ma != 0) {
			w.put(a < 0 ? 1 : 0, 1);
		}
		if (mb != 0) {
			w.put(b < 0 ? 1 : 0, 1);
		}
		if (ma >= 16) {
			escape(w, ma);
		}
		if (mb >= 16) {
			escape(w, mb);
		}
	}

	private static void escape(BitWriter w, int v) {
		int n = 31 - Integer.numberOfLeadingZeros(v);     // v の最上位ビットの位置(4 以上)
		for (int i = 4; i < n; i++) {
			w.put(1, 1);
		}
		w.put(0, 1);
		w.put(v - (1 << n), n);
	}

	static final class BitWriter {
		private byte[] buf = new byte[4096];
		private int bits;

		void put(int value, int count) {
			for (int i = count - 1; i >= 0; i--) {
				if ((bits >> 3) >= buf.length) {
					buf = java.util.Arrays.copyOf(buf, buf.length * 2);
				}
				if (((value >> i) & 1) != 0) {
					buf[bits >> 3] |= (byte) (0x80 >> (bits & 7));
				}
				bits++;
			}
		}

		byte[] toBytes() {
			return java.util.Arrays.copyOf(buf, (bits + 7) >> 3);
		}
	}
}
