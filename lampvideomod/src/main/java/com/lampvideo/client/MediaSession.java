package com.lampvideo.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * 1台のディスプレイの再生。ffmpeg で、映像(512x512のRGB)と音声(44.1kHz・16bit・ステレオ)をデコードして、
 * 映像はテクスチャに、音声はそのままスピーカーに流す。どちらも ffmpeg の -re で、実時間の速さで届く。
 * デコードは別スレッドで、テクスチャへの書き込みだけを描画スレッドでやる。
 */
public final class MediaSession implements AutoCloseable {

	public static final int W = 512;
	public static final int H = 512;
	private static int counter;

	public final String media;
	public final int generation;
	public volatile int mode;
	public volatile boolean failed;
	public volatile String error = "";

	private final Path file;
	private volatile boolean closed;
	private volatile int[] latest;
	private volatile boolean fresh;
	private Process videoProc;
	private Process audioProc;
	private SourceDataLine line;
	private DynamicTexture texture;
	public Identifier textureId;
	private boolean hasFrame;

	public MediaSession(Path file, String media, int generation, int mode, long seekMs) {
		this.file = file;
		this.media = media;
		this.generation = generation;
		this.mode = mode;
		texture = new DynamicTexture(() -> "lampvideo display", W, H, false);
		textureId = Identifier.fromNamespaceAndPath("lampvideo", "display/session" + (counter++));
		Minecraft.getInstance().getTextureManager().register(textureId, texture);
		String ss = String.format(java.util.Locale.ROOT, "%.3f", seekMs / 1000.0);
		try {
			videoProc = new ProcessBuilder("ffmpeg", "-v", "error", "-re", "-ss", ss, "-i", file.toString(), "-an",
					"-vf", "scale=" + W + ":" + H + ":force_original_aspect_ratio=decrease,pad=" + W + ":" + H
							+ ":(ow-iw)/2:(oh-ih)/2:black,fps=30",
					"-f", "rawvideo", "-pix_fmt", "rgb24", "-")
					.redirectError(ProcessBuilder.Redirect.DISCARD).start();
			startThread("lampvideo-video", this::videoLoop);
		} catch (IOException e) {
			fail("ffmpeg を実行できません。ffmpeg をインストールしてください(winget install ffmpeg)");
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
			startThread("lampvideo-audio", this::audioLoop);
		} catch (Exception e) {
			// 音声が使えなくても、映像は再生する
			error = "音声を開始できませんでした: " + e.getMessage();
		}
	}

	private void fail(String message) {
		failed = true;
		error = message;
	}

	private void startThread(String name, Runnable r) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		t.start();
	}

	private void videoLoop() {
		byte[] rgb = new byte[W * H * 3];
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
				latest = convert(rgb, mode);
				fresh = true;
			}
		} catch (IOException e) {
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

	/** 描画スレッドから呼ぶ: 新しい映像があればテクスチャに書く。映像が1枚でも来ていれば true。 */
	public boolean update() {
		if (fresh) {
			int[] px = latest;
			fresh = false;
			if (px != null && !closed) {
				NativeImage img = texture.getPixels();
				MemoryUtil.memIntBuffer(img.getPointer(), W * H).put(0, px);
				texture.upload();
				hasFrame = true;
			}
		}
		return hasFrame;
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
		Minecraft.getInstance().getTextureManager().release(textureId);
	}

	// ---- 映像の変換(モード) ----

	private static final int[][] TINTS = { { 51, 255, 89 }, { 255, 176, 31 }, { 255, 255, 255 } };

	static int[] convert(byte[] rgb, int mode) {
		int[] out = new int[W * H];
		if (mode <= 0) {
			for (int i = 0, j = 0; i < out.length; i++, j += 3) {
				out[i] = 0xFF000000 | (rgb[j + 2] & 0xFF) << 16 | (rgb[j + 1] & 0xFF) << 8 | (rgb[j] & 0xFF);
			}
		} else if (mode <= 3) {
			int[] t = TINTS[mode - 1];
			for (int y = 0; y < H; y++) {
				int scan = (y & 1) == 0 ? 256 : 230;
				for (int x = 0; x < W; x++) {
					int j = (y * W + x) * 3;
					int lum = ((rgb[j] & 0xFF) * 299 + (rgb[j + 1] & 0xFF) * 587 + (rgb[j + 2] & 0xFF) * 114) / 1000;
					int r = lum * t[0] / 255 * scan / 256;
					int g = lum * t[1] / 255 * scan / 256;
					int b = lum * t[2] / 255 * scan / 256;
					out[y * W + x] = 0xFF000000 | b << 16 | g << 8 | r;
				}
			}
		} else {
			final int cell = 8;
			for (int cy = 0; cy < H / cell; cy++) {
				for (int cx = 0; cx < W / cell; cx++) {
					int sum = 0;
					for (int sy = 0; sy < 4; sy++) {
						for (int sx = 0; sx < 4; sx++) {
							int j = ((cy * cell + sy * 2 + 1) * W + cx * cell + sx * 2 + 1) * 3;
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
							out[(cy * cell + py) * W + cx * cell + px] = c;
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
