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
import javax.sound.sampled.SourceDataLine;

/**
 * 1つの再生。ffmpeg で、映像(w x h の RGB、元の動画のコマ数(最大30fps))と音声(44.1kHz・16bit・ステレオ)をデコードする。
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

	/** 映像のコマ数の上限(1秒あたり)。元の動画がこれ以下なら、元のコマ数のまま(引き伸ばさない)。 */
	private static final double MAX_FPS = 30.0;
	/** この再生で使うコマ数(元の動画に合わせる)。 */
	private volatile double fps = MAX_FPS;

	private record Frame(long index, int[] px) {
	}

	/** 使い終わった映像の配列(1枚が数MBあるので、毎フレーム作り直さず、使い回す)。 */
	private final java.util.concurrent.ConcurrentLinkedQueue<int[]> pool = new java.util.concurrent.ConcurrentLinkedQueue<>();
	/** 最後にデコードできたコマの番号。映像のデコードが止まっていないかの確認に使う。 */
	private volatile long lastDecodedIndex = -1;

	private volatile boolean closed;
	private final BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(6);
	private final long seekMs;
	private final boolean snapshot;
	private final long startNanos = System.nanoTime();
	private volatile boolean audioReady;
	/** 左右の音量(0〜1)。距離とスピーカーの位置から、描画側が毎 tick 決める。 */
	private volatile float gainL;
	private volatile float gainR;
	private volatile Process videoProc;
	private volatile Process audioProc;
	private volatile SourceDataLine line;
	private volatile Thread audioThread;
	private final java.util.function.LongSupplier expected;
	/** 音声の出力に書き込んだフレーム数(再生位置の計算に使う)。 */
	private volatile long writtenFrames;

	/**
	 * @param expected いまの本来の再生位置(ミリ秒。サーバーの時刻から出した位置)。ffmpeg の起動に時間がかかった分の音声を飛ばして、
	 *                 始めから、本来の位置に合わせるために使う。null なら、合わせない
	 */
	public MediaSession(Path file, int w, int h, int mode, long seekMs, boolean snapshot, boolean playAudio,
			java.util.function.LongSupplier expected) {
		this.w = w;
		this.h = h;
		this.mode = mode;
		this.seekMs = seekMs;
		this.snapshot = snapshot;
		this.expected = expected;
		// プロセスの起動(Windows では数百ミリ秒かかる)は、描画のスレッドを止めないように、別のスレッドでやる
		startThread("lampvideo-start", () -> start(file, playAudio));
	}

	private void start(Path file, boolean playAudio) {
		String ss = String.format(Locale.ROOT, "%.3f", seekMs / 1000.0);
		try {
			fps = snapshot ? MAX_FPS : probeFps(file);
			String filter = "scale=" + w + ":" + h + ":flags=bilinear:force_original_aspect_ratio=decrease,pad=" + w + ":" + h
					+ ":(ow-iw)/2:(oh-ih)/2:black,fps=" + String.format(Locale.ROOT, "%.5f", fps);
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
		if (!snapshot && playAudio && !closed) {   // 一時停止の1コマ、または、音声を出さない再生(音は、効果音・音符ブロックのスピーカーや、なし)には、音声なし
			try {
				audioProc = new ProcessBuilder("ffmpeg", "-v", "error", "-ss", ss, "-i", file.toString(), "-vn",
						"-f", "s16le", "-ar", "44100", "-ac", "2", "-")
						.redirectError(ProcessBuilder.Redirect.DISCARD).start();
				AudioFormat fmt = new AudioFormat(44100f, 16, 2, true, false);
				SourceDataLine l = AudioSystem.getSourceDataLine(fmt);
				l.open(fmt, 44100 * 4 / 5);
				l.start();
				line = l;
				Thread t = new Thread(this::audioLoop, "lampvideo-audio");
				t.setDaemon(true);
				audioThread = t;
				t.start();
			} catch (Exception e) {
				// 音声が使えなくても、映像は再生する
				error = "音声を開始できませんでした: " + e.getMessage();
			}
		}
		if (closed) {
			// 起動している間に止められた: 作ったものを片付ける
			close();
			if (audioThread == null) {
				closeLine();
			}
		}
	}

	/** 元の動画のコマ数(ffprobe)。分からなければ、上限。上限より多ければ、上限。 */
	private static final java.util.Map<String, Double> FPS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	private static double probeFps(Path file) {
		Double cached = FPS_CACHE.get(file.toString());
		if (cached != null) {
			return cached;
		}
		double v = probeFpsNow(file);
		FPS_CACHE.put(file.toString(), v);
		return v;
	}

	private static double probeFpsNow(Path file) {
		try {
			Process pr = new ProcessBuilder("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=avg_frame_rate",
					"-of", "default=nw=1:nk=1", file.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			String out = new String(pr.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
			pr.waitFor(3, TimeUnit.SECONDS);
			String[] parts = out.split("/");
			double v = parts.length == 2 ? Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]) : Double.parseDouble(parts[0]);
			if (v >= 5.0 && v <= 240.0) {
				return Math.min(MAX_FPS, v);
			}
		} catch (IOException | RuntimeException | InterruptedException e) {
			// 分からなければ、上限
		}
		return MAX_FPS;
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
				int[] buffer = pool.poll();
				if (buffer == null) {
					buffer = new int[w * h];
				}
				Frame f = new Frame(index, convert(rgb, w, h, mode, buffer));
				lastDecodedIndex = index++;
				// いっぱいなら、空くまで待つ(その間 ffmpeg も止まるので、先に進みすぎない)
				while (!closed && !queue.offer(f, 50, TimeUnit.MILLISECONDS)) {
					// 待つ
				}
			}
		} catch (IOException | InterruptedException e) {
			// 止めたときの終了
		}
	}

	/**
	 * 音声のスレッド。音声の出力(line)は、このスレッドだけが書き込み、止めるときも、このスレッドが片付ける
	 * (別のスレッドから止めると、書き込み中のスレッドと衝突して、止まらなくなることがある)。
	 * 書き込みは、空きがあるぶんだけ。空きがなければ、少し待って、止める合図(closed)を見る。
	 */
	private void audioLoop() {
		byte[] buf = new byte[4096];
		float prevL = 0f;
		float prevR = 0f;
		try (InputStream in = audioProc.getInputStream()) {
			boolean first = true;
			long skip = 0;   // 起動に時間がかかった分の音声(バイト)。飛ばして、本来の位置に合わせる
			while (!closed) {
				// 4 の倍数(1フレーム = 左右 2 バイトずつ)ずつ読む。中途半端な長さで書くと、左右がずれてしまう
				int n = readFully(in, buf, buf.length);
				n -= n % 4;
				if (n <= 0) {
					break;
				}
				if (first) {
					first = false;
					if (expected != null) {
						long lag = expected.getAsLong() - seekMs;
						if (lag > 30) {
							skip = Math.min(lag, 10000L) * 44100L / 1000L * 4L;
							skip -= skip % 4;
						}
					}
				}
				int off = 0;
				if (skip > 0) {
					off = (int) Math.min(skip, n);
					skip -= off;
					writtenFrames += off / 4;
				}
				if (skip == 0 && !audioReady) {
					audioReady = true;   // 時計を、実時間から、音声の再生位置に切り替える(飛ばした分は、書き込み済みとして数えている)
				}
				if (off >= n) {
					continue;
				}
				// 16bit ステレオ(リトルエンディアン)の左右に、音量を掛ける。1回分の間で、少しずつ変えて、ぷつっという音を防ぐ
				float tl = gainL;
				float tr = gainR;
				int startFrame = off / 4;
				int frames = n / 4;
				for (int i = startFrame; i < frames; i++) {
					float t = (i - startFrame + 1) / (float) (frames - startFrame);
					float gl = prevL + (tl - prevL) * t;
					float gr = prevR + (tr - prevR) * t;
					int o = i * 4;
					int l = (short) ((buf[o + 1] << 8) | (buf[o] & 0xFF));
					int r = (short) ((buf[o + 3] << 8) | (buf[o + 2] & 0xFF));
					l = Math.max(-32768, Math.min(32767, Math.round(l * gl)));
					r = Math.max(-32768, Math.min(32767, Math.round(r * gr)));
					buf[o] = (byte) l;
					buf[o + 1] = (byte) (l >> 8);
					buf[o + 2] = (byte) r;
					buf[o + 3] = (byte) (r >> 8);
				}
				prevL = tl;
				prevR = tr;
				while (off < n && !closed) {
					int free = line.available();
					free -= free % 4;
					int chunk = Math.min(free, n - off);
					if (chunk <= 0) {
						Thread.sleep(3);
						continue;
					}
					line.write(buf, off, chunk);
					off += chunk;
					writtenFrames += chunk / 4;
				}
			}
		} catch (IOException | InterruptedException e) {
			// 止めたときの終了
		} finally {
			closeLine();
		}
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

	private synchronized void closeLine() {
		SourceDataLine l = line;
		if (l != null) {
			line = null;
			audioReady = false;
			try {
				l.stop();
				l.flush();
				l.close();
			} catch (RuntimeException e) {
				// 片付けに失敗しても、続ける
			}
		}
	}

	/**
	 * 音声が実際に再生した分から出した、いまの再生位置(ミリ秒)。書き込んだフレーム数から、まだ出力の中に残っている分を引いて数える
	 * (出力装置の「位置」の値は、開き直したときに、おかしくなる環境があるので、使わない)。音声が動いていなければ、開始からの実時間。
	 */
	public long rawClockMs() {
		SourceDataLine l = line;
		if (audioReady && l != null) {
			try {
				long queuedFrames = Math.max(0, (l.getBufferSize() - l.available()) / 4);
				long played = Math.max(0, writtenFrames - queuedFrames);
				return seekMs + played * 1000L / 44100L;
			} catch (RuntimeException e) {
				// 出力が閉じられた直後。実時間に切り替える
			}
		}
		return seekMs + (System.nanoTime() - startNanos) / 1_000_000L;
	}

	/** 補正(audioLatencyMs)を含む、いまの再生位置(ミリ秒)。映像のコマを選ぶ時計。 */
	public long clockMs() {
		return rawClockMs() - audioLatencyMs;
	}

	/** いまの時刻に出すべき映像(ABGR の w*h 個)が、前回から変わっていれば返す。なければ null。遅れたコマは飛ばす。 */
	public int[] takeFrame() {
		int[] chosen = null;
		if (snapshot) {
			Frame f = queue.poll();
			return f == null ? null : f.px();
		}
		long now = Math.max(0, (long) ((clockMs() - seekMs) * fps / 1000.0));
		for (Frame f = queue.peek(); f != null && f.index() <= now; f = queue.peek()) {
			recycle(chosen);   // 飛ばしたコマの配列は、使い回す
			chosen = queue.poll().px();
		}
		return chosen;
	}

	/** テクスチャに書き終えた映像の配列を、使い回しに戻す。 */
	public void recycle(int[] px) {
		if (px != null && px.length == w * h && pool.size() < 10) {
			pool.offer(px);
		}
	}

	/** 映像のデコードが、時計に追いつけなくなっているか(待ち行列が空で、最後のコマが 0.5 秒以上前の時刻のもの)。 */
	public boolean videoStalled() {
		if (snapshot || !queue.isEmpty()) {
			return false;
		}
		long now = Math.max(0, (long) ((clockMs() - seekMs) * fps / 1000.0));
		return now - lastDecodedIndex > fps / 2;
	}

	/** 左右の音量(0〜1)。距離やスピーカーの位置による変化は、呼ぶ側で計算する。 */
	public void setGains(float left, float right) {
		gainL = Math.max(0f, Math.min(1f, left));
		gainR = Math.max(0f, Math.min(1f, right));
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
		// 音声の出力は、音声のスレッドが片付ける(プロセスを止めると、読み込みが終わって、スレッドが抜ける)。
		// スレッドが始まっていないときだけ、ここで片付ける。
		if (audioThread == null) {
			closeLine();
		}
	}

	// ---- 映像の変換(モード) ----

	private static final int[][] TINTS = { { 51, 255, 89 }, { 255, 176, 31 }, { 255, 255, 255 } };

	static int[] convert(byte[] rgb, int w, int h, int mode, int[] out) {
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
			java.util.Arrays.fill(out, 0xFF000000);   // 使い回した配列の、前の絵を消す(セルに入りきらない端の分)
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
