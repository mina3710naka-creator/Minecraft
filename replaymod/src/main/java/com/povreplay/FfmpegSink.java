package com.povreplay;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ffmpeg(別に入れてあるもの)に、描いた絵を 1 枚ずつ渡して、圧縮してもらう。内蔵のエンコーダー(JCodec)より、同じ画質で、
 * ファイルが 1/5〜1/8 ほど小さくなる。音も、録音しておいたファイルを渡して、ffmpeg が AAC にする。
 * 絵は、標準入力から、生のまま(YUV420)渡す。ffmpeg が遅いと、書き込みが止まるので、そのぶん、再生の時計も待つ。
 */
final class FfmpegSink {

	private final Process process;
	private final OutputStream stdin;
	private final ArrayDeque<String> errors = new ArrayDeque<>();
	private final Path out;
	private boolean closed;

	private FfmpegSink(Process process, Path out) {
		this.process = process;
		this.stdin = new BufferedOutputStream(process.getOutputStream(), 1 << 20);
		this.out = out;
		Thread reader = new Thread(() -> {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					synchronized (errors) {
						errors.add(line);
						if (errors.size() > 30) {
							errors.poll();
						}
					}
				}
			} catch (IOException ignored) {
				// ffmpeg が終わった
			}
		}, "povreplay-ffmpeg-log");
		reader.setDaemon(true);
		reader.start();
	}

	// ---- ffmpeg を探す ----

	/** 設定に従って、使える ffmpeg を探す。見つからない・使えないときは null(内蔵のエンコーダーを使う)。 */
	static String find(Path gameDirectory) {
		String setting = RecConfig.ffmpeg;
		if (setting.equalsIgnoreCase("off")) {
			return null;
		}
		List<String> candidates = new ArrayList<>();
		boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
		String exe = windows ? "ffmpeg.exe" : "ffmpeg";
		if (!setting.equalsIgnoreCase("auto") && !setting.isEmpty()) {
			Path p = Path.of(setting);
			candidates.add(Files.isDirectory(p) ? p.resolve(exe).toString() : p.toString());
		} else {
			candidates.add(gameDirectory.resolve("povreplay").resolve(exe).toString());
			candidates.add("ffmpeg");
			if (windows) {
				String local = System.getenv("LOCALAPPDATA");
				if (local != null) {
					candidates.add(Path.of(local, "Microsoft", "WinGet", "Links", "ffmpeg.exe").toString());
				}
				candidates.add("C:\\ffmpeg\\bin\\ffmpeg.exe");
				candidates.add("C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe");
				candidates.add("C:\\ProgramData\\chocolatey\\bin\\ffmpeg.exe");
			} else {
				candidates.add("/opt/homebrew/bin/ffmpeg");
				candidates.add("/usr/local/bin/ffmpeg");
				candidates.add("/usr/bin/ffmpeg");
			}
		}
		boolean needX264 = RecConfig.videoArgs.isEmpty();
		for (String candidate : candidates) {
			if (works(candidate, needX264)) {
				return candidate;
			}
		}
		return null;
	}

	/** その ffmpeg が動くか(必要なら、libx264 が入っているかも)を、実際に動かして確かめる。 */
	private static boolean works(String command, boolean needX264) {
		try {
			Process p = new ProcessBuilder(command, "-hide_banner", "-encoders").redirectErrorStream(true).start();
			StringBuilder text = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					text.append(line).append('\n');
				}
			}
			if (!p.waitFor(15, TimeUnit.SECONDS)) {
				p.destroyForcibly();
				return false;
			}
			return p.exitValue() == 0 && (!needX264 || text.indexOf("libx264") >= 0);
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	// ---- 始める・書く・終わる ----

	static FfmpegSink start(String ffmpeg, Path outPart, Path pcm, int width, int height) throws IOException {
		List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-loglevel", "warning", "-y",
				"-f", "rawvideo", "-pix_fmt", "yuv420p", "-s", width + "x" + height, "-r", Integer.toString(ReplayEngine.FPS), "-i", "pipe:0",
				"-f", "s16le", "-ar", Integer.toString(AudioMixer.RATE), "-ac", "2", "-i", pcm.toString()));
		if (RecConfig.videoArgs.isEmpty()) {
			cmd.addAll(List.of("-c:v", "libx264", "-preset", RecConfig.preset, "-crf", Integer.toString(RecConfig.crf)));
		} else {
			cmd.addAll(List.of(RecConfig.videoArgs.split("\\s+")));
		}
		String space = RecConfig.bt709 ? "bt709" : "smpte170m";
		cmd.addAll(List.of("-pix_fmt", "yuv420p", "-colorspace", space, "-color_primaries", space, "-color_trc", space,
				"-color_range", RecConfig.fullRange ? "pc" : "tv",
				"-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-f", "mp4", outPart.toString()));
		PovReplay.LOGGER.info("ffmpeg を使います: " + String.join(" ", cmd));
		Process process = new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
		return new FfmpegSink(process, outPart);
	}

	/** 1 フレームを渡す。planes は、unsigned の YUV420(幅は stride、動画の大きさは w x h)。ffmpeg が詰まっていれば、ここで待つ。 */
	void write(byte[][] planes, int stride, int w, int h) throws IOException {
		byte[] y = planes[0];
		byte[] u = planes[1];
		byte[] v = planes[2];
		for (int row = 0; row < h; row++) {
			stdin.write(y, row * stride, w);
		}
		int cs = stride / 2;
		for (int row = 0; row < h / 2; row++) {
			stdin.write(u, row * cs, w / 2);
		}
		for (int row = 0; row < h / 2; row++) {
			stdin.write(v, row * cs, w / 2);
		}
	}

	/** 絵を渡し終えて、ffmpeg が仕上げる(音を混ぜて、ファイルを閉じる)のを待つ。 */
	void finish() throws IOException, InterruptedException {
		if (closed) {
			return;
		}
		closed = true;
		stdin.close();
		if (!process.waitFor(30, TimeUnit.MINUTES)) {
			process.destroyForcibly();
			throw new IOException("ffmpeg が終わりません");
		}
		if (process.exitValue() != 0) {
			throw new IOException("ffmpeg が失敗しました(終了コード " + process.exitValue() + "): " + tail());
		}
	}

	/** 失敗・中止のとき: ffmpeg を止める。 */
	void abort() {
		closed = true;
		process.destroyForcibly();
		try {
			stdin.close();
		} catch (IOException ignored) {
			// 閉じるだけ
		}
	}

	/** ffmpeg が最後に出した文(エラーの手がかり)。 */
	String tail() {
		synchronized (errors) {
			return String.join(" / ", errors);
		}
	}

	Path out() {
		return out;
	}
}
