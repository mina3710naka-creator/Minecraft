package com.lampvideo.trolley;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import net.minecraft.world.level.material.MapColor;

/**
 * 動画を、マップ(128x128)の色の並び(1コマ = 16384 バイト)にして、順に渡す。ffmpeg で、決まったコマ数(fps)に間引いて読む。
 * 読むのは別スレッドで、待ち行列がいっぱいなら待つ(ffmpeg も止まる)。マップの色は、ゲームのマップの色の表(MapColor)から、いちばん近い色を選ぶ。
 */
final class MapFrames implements AutoCloseable {

	static final int SIZE = 128;
	private static final int PIXELS = SIZE * SIZE;

	/** RGB(各 5 ビット)から、マップの色の番号への表。最初に使うときに作る。 */
	private static volatile byte[] lut;

	private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(48);
	private volatile boolean eof;
	private volatile boolean closed;
	private volatile int decoded;
	private Process proc;

	MapFrames(Path file, double fps) throws IOException {
		byte[] table = table();
		String filter = "scale=" + SIZE + ":" + SIZE + ":flags=bilinear:force_original_aspect_ratio=decrease,pad=" + SIZE + ":" + SIZE
				+ ":(ow-iw)/2:(oh-ih)/2:black,fps=" + String.format(Locale.ROOT, "%.4f", fps);
		proc = new ProcessBuilder("ffmpeg", "-v", "error", "-i", file.toString(), "-an", "-vf", filter,
				"-f", "rawvideo", "-pix_fmt", "rgb24", "-").redirectError(ProcessBuilder.Redirect.DISCARD).start();
		Thread t = new Thread(() -> readLoop(table), "lampvideo-trolley-decode");
		t.setDaemon(true);
		t.start();
	}

	private void readLoop(byte[] table) {
		byte[] rgb = new byte[PIXELS * 3];
		try (InputStream in = proc.getInputStream()) {
			while (!closed) {
				int off = 0;
				while (off < rgb.length) {
					int n = in.read(rgb, off, rgb.length - off);
					if (n < 0) {
						return;
					}
					off += n;
				}
				byte[] out = new byte[PIXELS];
				for (int i = 0, j = 0; i < PIXELS; i++, j += 3) {
					int r = rgb[j] & 0xFF;
					int g = rgb[j + 1] & 0xFF;
					int b = rgb[j + 2] & 0xFF;
					out[i] = table[(r >> 3) << 10 | (g >> 3) << 5 | (b >> 3)];
				}
				decoded++;
				while (!closed && !queue.offer(out, 50, TimeUnit.MILLISECONDS)) {
					// 待つ
				}
			}
		} catch (IOException | InterruptedException e) {
			// 止めたときの終了
		} finally {
			eof = true;
		}
	}

	/** 次のコマ。まだ読めていなければ null。 */
	byte[] poll() {
		return queue.poll();
	}

	/** 読み終えて、待ち行列も空(もう、次のコマは来ない)。 */
	boolean finished() {
		return eof && queue.isEmpty();
	}

	int ready() {
		return queue.size();
	}

	boolean endOfStream() {
		return eof;
	}

	@Override
	public void close() {
		closed = true;
		if (proc != null) {
			proc.destroy();
		}
	}

	/** 黒のマップの色(動画の外や、動画が終わったあとに使う)。 */
	static byte black() {
		return table()[0];
	}

	private static byte[] table() {
		byte[] t = lut;
		if (t != null) {
			return t;
		}
		java.util.List<int[]> pal = new java.util.ArrayList<>();
		java.util.List<Byte> ids = new java.util.ArrayList<>();
		for (int i = 1; i < 64; i++) {
			MapColor base = MapColor.byId(i);
			if (base == null || base.id != i) {
				continue;
			}
			for (MapColor.Brightness br : MapColor.Brightness.values()) {
				int argb = base.calculateARGBColor(br);
				pal.add(new int[] { argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF });
				ids.add(base.getPackedId(br));
			}
		}
		t = new byte[32768];
		for (int c = 0; c < t.length; c++) {
			int r = (c >> 10 & 31) * 8 + 4;
			int g = (c >> 5 & 31) * 8 + 4;
			int b = (c & 31) * 8 + 4;
			long best = Long.MAX_VALUE;
			byte pick = ids.get(0);
			for (int k = 0; k < pal.size(); k++) {
				int[] p = pal.get(k);
				long rm = (r + p[0]) / 2;
				long dr = r - p[0];
				long dg = g - p[1];
				long db = b - p[2];
				// 人の目に合わせた、色の距離(赤の平均で重みを変える)
				long d = ((512 + rm) * dr * dr >> 8) + 4 * dg * dg + ((767 - rm) * db * db >> 8);
				if (d < best) {
					best = d;
					pick = ids.get(k);
				}
			}
			t[c] = pick;
		}
		lut = t;
		return t;
	}
}
