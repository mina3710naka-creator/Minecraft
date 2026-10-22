package com.lampvideo;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * 動画ファイルを .lvf に変換する。Minecraft には依存しない。
 * 動画の読み込み(デコード)には ffmpeg / ffprobe を使うので、PC にインストールされている必要がある。
 *
 * 変換の内容は lampvideo/make_lvf.py と同じ: 白黒に二値化(暗い=点灯)、ちらつき抑制(ヒステリシス)、
 * 前フレームとの差分を長方形に分割、100フレームで全面を1周描き直し(帯状)。
 */
public final class VideoConverter {

	public static final class Info {
		public final int width;
		public final int height;
		/** ffprobe が返したフレームレート(例 "24/1")。ffmpeg の fps フィルタにそのまま渡す。 */
		public final String rate;
		public final double fps;
		public final boolean hasAudio;

		Info(int width, int height, String rate, double fps, boolean hasAudio) {
			this.width = width;
			this.height = height;
			this.rate = rate;
			this.fps = fps;
			this.hasAudio = hasAudio;
		}
	}

	public interface Progress {
		void onFrames(int frames);
	}

	private VideoConverter() {
	}

	/** ffmpeg と ffprobe を実行できるか。 */
	public static boolean toolsAvailable() {
		return runOk("ffmpeg", "-version") && runOk("ffprobe", "-version");
	}

	private static boolean runOk(String... cmd) {
		try {
			Process p = new ProcessBuilder(cmd)
					.redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.start();
			return p.waitFor() == 0;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	private static String output(String... cmd) throws IOException, InterruptedException {
		Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
		if (p.waitFor() != 0) {
			throw new IOException(cmd[0] + " が失敗しました");
		}
		return out;
	}

	public static Info probe(Path input) throws IOException, InterruptedException {
		String v = output("ffprobe", "-v", "error", "-select_streams", "v:0",
				"-show_entries", "stream=width,height,r_frame_rate", "-of", "csv=p=0", input.toString());
		String[] t = v.split("\\r?\\n")[0].split(",");
		if (t.length < 3) {
			throw new IOException("動画の情報を読み取れませんでした");
		}
		int w = Integer.parseInt(t[0].trim());
		int h = Integer.parseInt(t[1].trim());
		String rate = t[2].trim();
		String[] fr = rate.split("/");
		double fps = fr.length == 2 ? Double.parseDouble(fr[0]) / Double.parseDouble(fr[1]) : Double.parseDouble(rate);
		if (w <= 0 || h <= 0 || !(fps > 0)) {
			throw new IOException("動画の情報が不正です");
		}
		String a = output("ffprobe", "-v", "error", "-select_streams", "a:0",
				"-show_entries", "stream=codec_type", "-of", "csv=p=0", input.toString());
		return new Info(w, h, rate, fps, a.contains("audio"));
	}

	/**
	 * 動画を変換して .lvf を書く。戻り値はフレーム数。
	 *
	 * @param width     ランプの横の数(縦は動画の縦横比から決める)
	 * @param hold      各フレームを表示する tick 数
	 * @param maxFrames 0 以外なら、先頭からこの枚数だけ(テスト用)
	 */
	public static int convert(Path input, Path outLvf, Info info, int width, int hold, int threshold,
			int hysteresis, int keyframe, int maxFrames, Progress progress)
			throws IOException, InterruptedException {
		int w = width;
		int h = Math.max(1, Math.round((float) w * info.height / info.width));
		if (w > 65535 || h > 65535 || hold < 1 || hold > 255) {
			throw new IOException("幅・高さ・hold が範囲外です");
		}
		Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-i", input.toString(),
				"-vf", "fps=" + info.rate + ",scale=" + w + ":" + h + ":flags=area,format=gray",
				"-f", "rawvideo", "-")
				.redirectError(ProcessBuilder.Redirect.DISCARD).start();

		final int bands = 10;
		int bandStep = Math.max(1, keyframe / bands);
		byte[] buf = new byte[w * h];
		boolean[] state = null; // 前フレームの二値化結果 (true = 明るい)
		boolean[] prev = null;
		int n = 0;
		ByteArrayOutputStream body = new ByteArrayOutputStream(1 << 20);
		DataOutputStream out = new DataOutputStream(body);

		try (InputStream in = new BufferedInputStream(p.getInputStream(), 1 << 20)) {
			while (in.readNBytes(buf, 0, buf.length) == buf.length) {
				boolean[] cur = new boolean[w * h];
				for (int i = 0; i < cur.length; i++) {
					int g = buf[i] & 0xFF;
					if (hysteresis > 0 && state != null) {
						// 閾値付近のちらつきで切り替わらないよう、十分変化したときだけ反転する
						cur[i] = g >= threshold + hysteresis ? true : (g < threshold - hysteresis ? false : state[i]);
					} else {
						cur[i] = g >= threshold;
					}
				}
				state = cur;

				boolean[] base = null;
				if (n > 0) {
					base = prev;
					if (keyframe > 0 && n % bandStep == 0) {
						// 全面を帯状に少しずつ描き直す(書き込みに失敗しても自己修復する)
						int b = (n / bandStep) % bands;
						base = prev.clone();
						for (int y = b * h / bands; y < (b + 1) * h / bands; y++) {
							for (int x = 0; x < w; x++) {
								base[y * w + x] = !cur[y * w + x];
							}
						}
					}
				}
				boolean[] on = new boolean[w * h];
				boolean[] off = new boolean[w * h];
				for (int i = 0; i < cur.length; i++) {
					boolean changed = base == null || base[i] != cur[i];
					on[i] = changed && !cur[i];   // 暗い画素 -> 点灯
					off[i] = changed && cur[i];   // 明るい画素 -> 消灯
				}
				writeRects(out, bestRects(on, w, h));
				writeRects(out, bestRects(off, w, h));

				prev = cur;
				n++;
				if (progress != null && n % 200 == 0) {
					progress.onFrames(n);
				}
				if (maxFrames > 0 && n >= maxFrames) {
					break;
				}
			}
		} finally {
			p.destroy();
		}
		p.waitFor();
		if (n == 0) {
			throw new IOException("フレームを読み込めませんでした");
		}

		Path tmp = outLvf.resolveSibling(outLvf.getFileName() + ".tmp");
		try (OutputStream raw = Files.newOutputStream(tmp);
				DataOutputStream gz = new DataOutputStream(new GZIPOutputStream(raw, 1 << 16))) {
			gz.write(new byte[] { 'L', 'V', 'F', '1' });
			gz.writeShort(w);
			gz.writeShort(h);
			gz.writeByte(hold);
			gz.writeInt(n);
			body.writeTo(gz);
		}
		Files.move(tmp, outLvf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		return n;
	}

	/** 音声を取り出して、映像と同じ倍率に伸縮した ogg にする。 */
	public static void extractAudio(Path input, Path outOgg, Info info, int hold)
			throws IOException, InterruptedException {
		double rate = 20.0 / (info.fps * hold);
		List<String> filters = new ArrayList<>();
		while (rate < 0.5) {
			filters.add("atempo=0.5");
			rate /= 0.5;
		}
		while (rate > 2.0) {
			filters.add("atempo=2.0");
			rate /= 2.0;
		}
		filters.add(String.format(java.util.Locale.ROOT, "atempo=%.6f", rate));
		String af = String.join(",", filters);
		for (String codec : new String[] { "libvorbis", "vorbis" }) {
			List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-v", "error", "-y", "-i", input.toString(),
					"-vn", "-ac", "1", "-af", af, "-c:a", codec));
			if (codec.equals("vorbis")) {
				cmd.addAll(List.of("-strict", "-2"));
			}
			cmd.add(outOgg.toString());
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			if (p.waitFor() == 0) {
				return;
			}
		}
		throw new IOException("音声の変換に失敗しました(ffmpeg が Vorbis に対応していない可能性があります)");
	}

	// ---- 長方形への分割 (lampvideo/make_lampvideo.py の rects / best_rects と同じ) ----

	private static void writeRects(DataOutputStream out, List<int[]> rects) throws IOException {
		out.writeInt(rects.size());
		for (int[] r : rects) {
			out.writeShort(r[0]);
			out.writeShort(r[1]);
			out.writeShort(r[2]);
			out.writeShort(r[3]);
		}
	}

	/** 横方向優先と縦方向優先の分割を比べて、長方形が少ない方を使う。各要素は {x1, x2, y0, y1}。 */
	static List<int[]> bestRects(boolean[] m, int w, int h) {
		List<int[]> hr = rects(m, w, h);
		boolean[] t = new boolean[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				t[x * h + y] = m[y * w + x]; // 転置: 行 = 元の列
			}
		}
		List<int[]> vr = new ArrayList<>();
		for (int[] r : rects(t, h, w)) {
			vr.add(new int[] { r[2], r[3], r[0], r[1] });
		}
		return hr.size() <= vr.size() ? hr : vr;
	}

	/** True の画素を長方形に分割する。横の連続区間を作り、同じ区間が縦に続く行を1つにまとめる。 */
	static List<int[]> rects(boolean[] m, int w, int h) {
		List<int[]> out = new ArrayList<>();
		Map<Long, Integer> open = new LinkedHashMap<>(); // (x1,x2) -> 縦に伸びている区間の開始行
		List<long[]> now = new ArrayList<>();
		for (int y = 0; y <= h; y++) {
			now.clear();
			if (y < h) {
				int x = 0;
				while (x < w) {
					if (m[y * w + x]) {
						int s = x;
						while (x + 1 < w && m[y * w + x + 1]) {
							x++;
						}
						now.add(new long[] { s, x });
					}
					x++;
				}
			}
			java.util.HashSet<Long> nowKeys = new java.util.HashSet<>();
			for (long[] r : now) {
				nowKeys.add(r[0] << 20 | r[1]);
			}
			for (Iterator<Map.Entry<Long, Integer>> it = open.entrySet().iterator(); it.hasNext();) {
				Map.Entry<Long, Integer> e = it.next();
				if (!nowKeys.contains(e.getKey())) {
					long k = e.getKey();
					out.add(new int[] { (int) (k >> 20), (int) (k & 0xFFFFF), e.getValue(), y - 1 });
					it.remove();
				}
			}
			for (long[] r : now) {
				open.putIfAbsent(r[0] << 20 | r[1], y);
			}
		}
		return out;
	}
}
