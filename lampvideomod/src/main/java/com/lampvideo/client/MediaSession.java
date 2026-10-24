package com.lampvideo.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

/**
 * 1つの再生。ffmpeg で、映像(w x h の RGB、30fps 固定)と音声(44.1kHz・16bit・ステレオ)をデコードする。
 * 音声が「時計」: スピーカーが実際に再生したサンプル数から、いまの再生位置を出し、その時刻の映像のコマを出す。
 * 映像は全速でデコードして、小さな待ち行列に貯める(いっぱいなら待つ)。遅れたコマは飛ばし、早すぎるコマは待つので、音と映像は、ずれ続けない。
 * 音声が使えないときは、開始からの実時間を時計にする。
 * snapshot が true のときは、映像を1枚だけ読んで終わる(一時停止の画面を出すため。音声なし)。
 * デコードは別スレッドで行う。テクスチャへの書き込みは、描画スレッド側(DisplayClient)が takeFrame で受け取って行う。
 */
public final class MediaSession implements AutoCloseable {

	public final int w;
	public final int h;
	public volatile int mode;
	public volatile boolean failed;
	public volatile String error = "";

	/** 音声が遅れて聞こえる分(ミリ秒)。映像をその分だけ遅らせて、耳に届く音と合わせる。端末ごとの設定(/lampvideoav)。 */
	public static volatile int audioLatencyMs;

	private static final int FPS = 30;

	private record Frame(long index, int[] px) {
	}

	private volatile boolean closed;
	private final BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(6);
	private final long seekMs;
	private final boolean snapshot;
	private final long startNanos = System.nanoTime();
	private boolean audioReady;
	private Process videoProc;
	private Process audioProc;
	private SourceDataLine line;

	public MediaSession(Path file, int w, int h, int mode, long seekMs, boolean snapshot) {
		this.w = w;
		this.h = h;
		this.mode = mode;
		this.seekMs = seekMs;
		this.snapshot = snapshot;
		String ss = String.format(Locale.ROOT, "%.3f", seekMs / 1000.0);
		try {
			String filter = "scale=" + w + ":" + h + ":force_original_aspect_ratio=decrease,pad=" + w + ":" + h
					+ ":(ow-iw)/2:(oh-ih)/2:black,fps=" + FPS;
			ProcessBuilder pb = snapshot
					? new ProcessBuilder("ffmpeg", "-v", "error", "-ss", ss, "-i", file.toString(), "-an", "-frames:v", "1",
							"-vf", filter, "-f", "rawvideo", "-pix_fmt", "rgb24", "-")
					: new ProcessBuilder("ffmpeg", "-v", "error", "-ss", ss, "-i", file.toString(), "-an",
							"-vf", filter, "-f", "rawvideo", "-pix_fmt", "rgb24", "-");
			videoProc = pb.redirectError(ProcessBuilder.Redirect.DISCARD).start();
			startThread("lampvideo-video", this::videoLoop);
		} catch (IOException e) {
			failed = true;
			error = "ffmpeg を実行できません。ffmpeg をインストールしてください(winget install ffmpeg)";
			return;
		}
		if (snapshot) {
			return;
		}
		try {
			audioProc = new ProcessBuilder("ffmpeg", "-v", "error", "-ss", ss, "-i", file.toString(), "-vn",
					"-f", "s16le", "-ar", "44100", "-ac", "2", "-")
					.redirectError(ProcessBuilder.Redirect.DISCARD).start();
			AudioFormat fmt = new AudioFormat(44100f, 16, 2, true, false);
			line = AudioSystem.getSourceDataLine(fmt);
			line.open(fmt, 44100 * 4 / 5);
			line.start();
			audioReady = true;
			startThread("lampvideo-audio", this::audioLoop);
		} catch (Exception e) {
			// 音声が使えなくても、映像は再生する
			error = "音声を開始できませんでした: " + e.getMessage();
		}
	}

	private void startThread(String name, Runnable r) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		t.start();
	}

	private void videoLoop() {
		byte[] rgb = new byte[w * h * 3];
		long index = 0;
		try (InputStream in = videoProc.getInputStream()) {
			while (!closed) {
				int off = 0;
				while (off < rgb.length) {
					int n = in.read(rgb, off, rgb.length - off);
					if (n < 0) {
						return;
					}
					off += n;
				}
				Frame f = new Frame(index++, convert(rgb, w, h, mode));
				// いっぱいなら、空くまで待つ(その間 ffmpeg も止まるので、先に進みすぎない)
				while (!closed && !queue.offer(f, 50, TimeUnit.MILLISECONDS)) {
					// 待つ
				}
			}
		} catch (IOException | InterruptedException e) {
			// 止めたときの終了
		}
	}

	private void audioLoop() {
		byte[] buf = new byte[4096];
		try (InputStream in = audioProc.getInputStream()) {
			while (!closed) {
				int n = in.read(buf);
				if (n < 0) {
					return;
				}
				line.write(buf, 0, n);
			}
		} catch (IOException e) {
			// 止めたときの終了
		}
	}

	/** いまの再生位置(ミリ秒)。音声が動いていれば、スピーカーが実際に再生した分から。なければ、開始からの実時間。 */
	public long clockMs() {
		SourceDataLine l = line;
		if (audioReady && l != null) {
			return seekMs + l.getLongFramePosition() * 1000L / 44100L - audioLatencyMs;
		}
		return seekMs + (System.nanoTime() - startNanos) / 1_000_000L - audioLatencyMs;
	}

	/** いまの時刻に出すべき映像(ABGR の w*h 個)が、前回から変わっていれば返す。なければ null。遅れたコマは飛ばす。 */
	public int[] takeFrame() {
		int[] chosen = null;
		if (snapshot) {
			Frame f = queue.poll();
			return f == null ? null : f.px();
		}
		long now = Math.max(0, (clockMs() - seekMs) * FPS / 1000L);
		for (Frame f = queue.peek(); f != null && f.index() <= now; f = queue.peek()) {
			chosen = queue.poll().px();
		}
		return chosen;
	}

	/** 音量(0〜1)。距離による減衰は呼ぶ側で計算する。 */
	public void setVolume(float v) {
		SourceDataLine l = line;
		if (l != null && l.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			FloatControl c = (FloatControl) l.getControl(FloatControl.Type.MASTER_GAIN);
			float db = v <= 0.001f ? c.getMinimum() : (float) (20.0 * Math.log10(v));
			c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), db)));
		}
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		if (videoProc != null) {
			videoProc.destroy();
		}
		if (audioProc != null) {
			audioProc.destroy();
		}
		if (line != null) {
			line.stop();
			line.flush();
			line.close();
		}
	}

	// ---- 映像の変換(モード) ----

	private static final int[][] TINTS = { { 51, 255, 89 }, { 255, 176, 31 }, { 255, 255, 255 } };

	static int[] convert(byte[] rgb, int w, int h, int mode) {
		int[] out = new int[w * h];
		if (mode <= 0) {
			for (int i = 0, j = 0; i < out.length; i++, j += 3) {
				out[i] = 0xFF000000 | (rgb[j + 2] & 0xFF) << 16 | (rgb[j + 1] & 0xFF) << 8 | (rgb[j] & 0xFF);
			}
		} else if (mode <= 3) {
			int[] t = TINTS[mode - 1];
			for (int y = 0; y < h; y++) {
				int scan = (y & 1) == 0 ? 256 : 230;
				for (int x = 0; x < w; x++) {
					int j = (y * w + x) * 3;
					int lum = ((rgb[j] & 0xFF) * 299 + (rgb[j + 1] & 0xFF) * 587 + (rgb[j + 2] & 0xFF) * 114) / 1000;
					int r = lum * t[0] / 255 * scan / 256;
					int g = lum * t[1] / 255 * scan / 256;
					int b = lum * t[2] / 255 * scan / 256;
					out[y * w + x] = 0xFF000000 | b << 16 | g << 8 | r;
				}
			}
		} else {
			final int cell = Math.max(4, w / 64);
			for (int cy = 0; cy < h / cell; cy++) {
				for (int cx = 0; cx < w / cell; cx++) {
					int sum = 0;
					for (int sy = 0; sy < 4; sy++) {
						for (int sx = 0; sx < 4; sx++) {
							int py = cy * cell + (sy * 2 + 1) * cell / 8;
							int px = cx * cell + (sx * 2 + 1) * cell / 8;
							int j = (py * w + px) * 3;
							sum += ((rgb[j] & 0xFF) * 299 + (rgb[j + 1] & 0xFF) * 587 + (rgb[j + 2] & 0xFF) * 114) / 1000;
						}
					}
					boolean lit = sum / 16 > 90;
					for (int py = 0; py < cell; py++) {
						for (int px = 0; px < cell; px++) {
							int c;
							if (px == 0 || py == 0) {
								c = 0xFF060810;
							} else if (lit) {
								boolean core = px > 1 && px < cell - 1 && py > 1 && py < cell - 1;
								c = core ? 0xFF8CE2FF : 0xFF50B4E6;
							} else {
								c = 0xFF0C1824;
							}
							out[(cy * cell + py) * w + cx * cell + px] = c;
						}
					}
				}
			}
		}
		return out;
	}

	public static List<String> extensions() {
		return List.of("mp4", "mkv", "webm", "mov", "avi", "mp3", "ogg", "wav", "flac", "m4a");
	}
}
