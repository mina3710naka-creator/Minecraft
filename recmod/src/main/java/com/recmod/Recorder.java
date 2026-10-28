package com.recmod;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/**
 * 録画の本体。プレイ中は、ゲームを重くしないために、ほとんど何もしない。
 *  - 映像: 描画のたびに(GameRendererMixin から)呼ばれ、ホットバーなどの画面(GUI)を描く直前の、ワールドだけの絵を読み出して、
 *    別のスレッドで YUV にそろえて、一時ファイルへ書く(圧縮はしない)。
 *  - 音: AudioMixer がゲームの音を混ぜて、一時ファイルへ書く(圧縮はしない)。
 * 停止すると、一時ファイルから H.264 と AAC を作って MP4 にする(Exporter。時間がかかる)。終われば一時ファイルを消す。
 */
public final class Recorder {

	public static final Logger LOGGER = LoggerFactory.getLogger("recmod");

	static final int FPS = 30;

	/** 画面の絵を溜めておく入れ物の数。書き込みが追いつかないときは、絵を飛ばす(前の絵が続く)。 */
	private static final int MAX_IN_FLIGHT = 4;

	private static volatile Recorder active;

	private final Minecraft mc;
	private final Path file;
	private final Path videoTemp;
	private final Path audioTemp;
	private final long startNanos = System.nanoTime();
	/** 録画する大きさ(画面の大きさを、16 の倍数に切り下げたもの)。 */
	final int outW;
	final int outH;
	private final int frameBytes;

	private final FileChannel videoOut;
	private final OutputStream audioOut;
	private final AudioMixer mixer;
	private final ExecutorService converter = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "recmod-store");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private final BlockingQueue<byte[]> pool = new ArrayBlockingQueue<>(MAX_IN_FLIGHT);
	private int allocated;

	private int nextFrame;
	/** 何フレームに 1 枚、絵を取るか(1 = 30fps)。ゲームが重くなったら、増やして、ゲームの fps を守る。 */
	private int step = 1;
	private long lastRenderNanos;
	/** 描画 1 回あたりの時間の移動平均(ナノ秒)。 */
	private double renderEma;
	/** GPU から読み出す入れ物。毎回作り直すと重いので、使い回す。 */
	private final GpuBuffer[] ring = new GpuBuffer[3];
	private final boolean[] ringBusy = new boolean[3];
	private int ringBytes;
	/** GPU から読み出し中(コールバック待ち)の絵の数。止めるときは、これが 0 になるのを待つ。 */
	private final AtomicInteger inFlight = new AtomicInteger();
	/** 一時ファイルに書いたフレームの、録画の始まりからの番号(fps 単位)。 */
	private int[] frameIndex = new int[1024];
	private int frameCount;
	private volatile boolean capturing = true;
	private volatile boolean failed;

	// ---- 外から使うところ ----

	public static boolean isRecording() {
		return active != null;
	}

	/** 録画の長さ(秒)。録画していなければ 0。 */
	public static long seconds() {
		Recorder r = active;
		return r == null ? 0 : (System.nanoTime() - r.startNanos) / 1_000_000_000L;
	}

	/** 録画していなければ始め、していれば止める。描画のスレッドから呼ぶ。 */
	public static void toggle(Minecraft mc) {
		if (active == null) {
			start(mc);
		} else {
			stop(mc);
		}
	}

	public static void start(Minecraft mc) {
		if (active != null) {
			return;
		}
		if (Exporter.isBusy()) {
			message(mc, Component.literal("[録画] 前の録画を保存している途中です。終わるまで待ってください").withStyle(ChatFormatting.YELLOW));
			return;
		}
		RenderTarget target = mc.gameRenderer.mainRenderTarget();
		if (target == null || target.width < 16 || target.height < 16) {
			message(mc, Component.literal("[録画] 画面の大きさが分からないので、始められません").withStyle(ChatFormatting.RED));
			return;
		}
		try {
			active = new Recorder(mc, target.width, target.height);
			message(mc, Component.literal("[録画] 録画を始めました(ホットバーなどの表示は映りません)").withStyle(ChatFormatting.GREEN));
		} catch (IOException | RuntimeException e) {
			LOGGER.error("録画を始められませんでした", e);
			message(mc, Component.literal("[録画] 始められませんでした: " + e.getMessage()).withStyle(ChatFormatting.RED));
		}
	}

	public static void stop(Minecraft mc) {
		Recorder r = active;
		if (r == null) {
			return;
		}
		active = null;
		r.capturing = false;
		r.closeRingIfIdle();
		message(mc, Component.literal("[録画] 録画を止めました。動画に変換して保存しています(録画の長さによって、時間がかかります)…"));
		Thread t = new Thread(() -> r.export(false), "recmod-export");
		t.setDaemon(true);
		t.start();
	}

	/** ゲームを終えるときなど: 保存を終えるまで待つ。 */
	public static void stopAndWait() {
		Recorder r = active;
		if (r != null) {
			active = null;
			r.capturing = false;
			r.closeRingIfIdle();
			r.export(true);
		}
		Exporter.waitUntilDone();
	}

	/** 描画のスレッドから、ホットバーなどを描く直前に、毎回呼ばれる。 */
	public static void onFrame(RenderTarget target) {
		Recorder r = active;
		if (r != null) {
			r.capture(target);
		}
	}

	// ---- 始める ----

	private Recorder(Minecraft mc, int width, int height) throws IOException {
		this.mc = mc;
		this.outW = width & ~15;
		this.outH = height & ~15;
		this.frameBytes = outW * outH * 3 / 2;
		this.file = chooseFile(mc);
		this.videoTemp = Path.of(file + ".video.tmp");
		this.audioTemp = Path.of(file + ".audio.tmp");
		this.videoOut = FileChannel.open(videoTemp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
		this.audioOut = new BufferedOutputStream(Files.newOutputStream(audioTemp), 1 << 16);
		this.mixer = new AudioMixer(startNanos, this::writeAudio);
		this.mixer.start();
	}

	/** ダウンロードフォルダーに、日時のついた名前で保存する(フォルダーがなければ、ゲームのフォルダーの recordings)。 */
	private static Path chooseFile(Minecraft mc) throws IOException {
		Path dir = Path.of(System.getProperty("user.home", "."), "Downloads");
		if (!Files.isDirectory(dir)) {
			dir = mc.gameDirectory.toPath().resolve("recordings");
			Files.createDirectories(dir);
		}
		String name = "minecraft-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".mp4";
		return dir.resolve(name);
	}

	private void writeAudio(byte[] pcm, int length) {
		try {
			audioOut.write(pcm, 0, length);
		} catch (IOException e) {
			fail(e);
		}
	}

	private void fail(IOException e) {
		if (!failed) {
			failed = true;
			LOGGER.error("録画の一時ファイルに書けませんでした(ディスクの空きは足りていますか)", e);
			Minecraft.getInstance().execute(() -> {
				if (active == this) {
					message(mc, Component.literal("[録画] 一時ファイルに書けないので、ここで止めます").withStyle(ChatFormatting.RED));
					stop(mc);
				}
			});
		}
	}

	// ---- 映像を読み出す(描画のスレッド。ここでするのは、GPU から読み出した絵のコピーだけ) ----

	private void capture(RenderTarget target) {
		long now = System.nanoTime();
		if (lastRenderNanos != 0) {
			double dt = now - lastRenderNanos;
			renderEma = renderEma == 0 ? dt : renderEma * 0.9 + dt * 0.1;
			// ゲームの fps を守る: 38fps を下回ったら、絵を取る間隔を広げ、45fps を超えて戻ったら、狭める
			if (renderEma > 1e9 / 32 && step < 3) {
				step = 3;
			} else if (renderEma > 1e9 / 38 && step < 2) {
				step = 2;
			} else if (renderEma < 1e9 / 45 && step > 1) {
				step--;
				renderEma = 1e9 / 45;
			}
		}
		lastRenderNanos = now;
		if (!capturing || failed) {
			return;
		}
		int frame = (int) ((now - startNanos) * FPS / 1_000_000_000L);
		if (frame < nextFrame) {
			return;
		}
		GpuTexture texture = target.getColorTexture();
		int w = target.width;
		int h = target.height;
		if (texture == null || w < 16 || h < 16) {
			return;
		}
		int size = w * h * texture.getFormat().blockSize();
		if (size != ringBytes) {
			if (ringBusy[0] || ringBusy[1] || ringBusy[2]) {
				return;   // 画面の大きさが変わった。読み出し中のものが終わるまで待つ
			}
			closeRing();
			ringBytes = size;
		}
		int slot = -1;
		for (int i = 0; i < ring.length; i++) {
			if (!ringBusy[i]) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			return;   // 読み出しが追いついていない
		}
		byte[] raw = acquire(size);
		if (raw == null) {
			return;   // 書き込みが追いついていない。この描画では、絵を取らない
		}
		if (ring[slot] == null) {
			ring[slot] = RenderSystem.getDevice().createBuffer(() -> "Recording buffer", 9, size);
		}
		nextFrame = frame + step;
		ringBusy[slot] = true;
		inFlight.incrementAndGet();

		GpuBuffer buffer = ring[slot];
		int usedSlot = slot;
		RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
			try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
				view.data().get(0, raw, 0, size);
			} catch (RuntimeException e) {
				LOGGER.warn("画面の絵を読み出せませんでした", e);
				release(raw);
				ringBusy[usedSlot] = false;
				inFlight.decrementAndGet();
				return;
			}
			ringBusy[usedSlot] = false;
			if (!capturing) {
				closeRingIfIdle();
			}
			try {
				converter.execute(() -> store(raw, w, h, frame));
			} catch (RejectedExecutionException e) {
				release(raw);   // 止める途中で来た絵は、捨てる
			} finally {
				inFlight.decrementAndGet();
			}
		}, 0);
	}

	private void closeRing() {
		for (int i = 0; i < ring.length; i++) {
			if (ring[i] != null) {
				ring[i].close();
				ring[i] = null;
			}
		}
	}

	private void closeRingIfIdle() {
		if (!ringBusy[0] && !ringBusy[1] && !ringBusy[2]) {
			closeRing();
		}
	}

	private byte[] acquire(int size) {
		byte[] b = pool.poll();
		if (b == null) {
			if (allocated >= MAX_IN_FLIGHT) {
				return null;
			}
			allocated++;
			return new byte[size];
		}
		if (b.length != size) {
			return new byte[size];   // 画面の大きさが変わったときは、作り直す
		}
		return b;
	}

	private void release(byte[] b) {
		pool.offer(b);
	}

	// ---- 一時ファイルへ(store のスレッド。優先度は低い) ----

	private byte[] yuv;

	private void store(byte[] raw, int w, int h, int frame) {
		try {
			if (yuv == null) {
				yuv = new byte[frameBytes];
			}
			toYuv(raw, w, h, yuv);
			ByteBuffer buf = ByteBuffer.wrap(yuv);
			while (buf.hasRemaining()) {
				videoOut.write(buf);
			}
			if (frameCount == frameIndex.length) {
				frameIndex = Arrays.copyOf(frameIndex, frameCount * 2);
			}
			frameIndex[frameCount++] = frame;
		} catch (IOException e) {
			fail(e);
		} finally {
			release(raw);
		}
	}

	/**
	 * 読み出した絵(上下が逆で、1 画素が R,G,B,A の順)を、YUV420(BT.601、16〜235 の範囲)にする。上下も直す。
	 * 値は、JCodec がそのまま使えるよう、128 を引いた符号つきバイト。画面が録画の大きさと違っていれば、近い画素で埋める。
	 */
	private void toYuv(byte[] raw, int w, int h, byte[] out) {
		int cw = outW / 2;
		int uOff = outW * outH;
		int vOff = uOff + cw * (outH / 2);
		for (int y = 0; y < outH; y += 2) {
			int sy0 = Math.min(h - 1, y);
			int sy1 = Math.min(h - 1, y + 1);
			int row0 = (h - 1 - sy0) * w * 4;
			int row1 = (h - 1 - sy1) * w * 4;
			for (int x = 0; x < outW; x += 2) {
				int sx0 = Math.min(w - 1, x) * 4;
				int sx1 = Math.min(w - 1, x + 1) * 4;
				int r0 = raw[row0 + sx0] & 0xFF, g0 = raw[row0 + sx0 + 1] & 0xFF, b0 = raw[row0 + sx0 + 2] & 0xFF;
				int r1 = raw[row0 + sx1] & 0xFF, g1 = raw[row0 + sx1 + 1] & 0xFF, b1 = raw[row0 + sx1 + 2] & 0xFF;
				int r2 = raw[row1 + sx0] & 0xFF, g2 = raw[row1 + sx0 + 1] & 0xFF, b2 = raw[row1 + sx0 + 2] & 0xFF;
				int r3 = raw[row1 + sx1] & 0xFF, g3 = raw[row1 + sx1 + 1] & 0xFF, b3 = raw[row1 + sx1 + 2] & 0xFF;
				out[y * outW + x] = (byte) (16 + ((66 * r0 + 129 * g0 + 25 * b0 + 128) >> 8) - 128);
				out[y * outW + x + 1] = (byte) (16 + ((66 * r1 + 129 * g1 + 25 * b1 + 128) >> 8) - 128);
				out[(y + 1) * outW + x] = (byte) (16 + ((66 * r2 + 129 * g2 + 25 * b2 + 128) >> 8) - 128);
				out[(y + 1) * outW + x + 1] = (byte) (16 + ((66 * r3 + 129 * g3 + 25 * b3 + 128) >> 8) - 128);
				int r = (r0 + r1 + r2 + r3) >> 2;
				int g = (g0 + g1 + g2 + g3) >> 2;
				int b = (b0 + b1 + b2 + b3) >> 2;
				int u = 128 + ((-38 * r - 74 * g + 112 * b + 128) >> 8);
				int v = 128 + ((112 * r - 94 * g - 18 * b + 128) >> 8);
				int ci = (y / 2) * cw + x / 2;
				out[uOff + ci] = (byte) (Math.max(16, Math.min(240, u)) - 128);
				out[vOff + ci] = (byte) (Math.max(16, Math.min(240, v)) - 128);
			}
		}
	}

	// ---- 止める(別のスレッド) ----

	private void export(boolean wait) {
		capturing = false;
		long elapsed = System.nanoTime() - startNanos;
		Path saved = null;
		try {
			mixer.stop();
			audioOut.close();
			for (int i = 0; i < 100 && inFlight.get() > 0; i++) {
				Thread.sleep(50);   // GPU から読み出し中の絵が届くのを待つ
			}
			converter.shutdown();
			converter.awaitTermination(30, TimeUnit.SECONDS);
			videoOut.close();
			if (frameCount > 0) {
				int total = (int) (elapsed * FPS / 1_000_000_000L);
				saved = Exporter.run(mc, file, videoTemp, audioTemp, outW, outH, Arrays.copyOf(frameIndex, frameCount), total);
			} else {
				LOGGER.error("映像が 1 フレームも取れませんでした(描画への割り込みが効いていない、または画面を読み出せません)");
			}
		} catch (IOException | InterruptedException | RuntimeException e) {
			LOGGER.error("録画の保存に失敗しました", e);
		} finally {
			try {
				Files.deleteIfExists(videoTemp);
				Files.deleteIfExists(audioTemp);
			} catch (IOException e) {
				LOGGER.warn("一時ファイルを消せませんでした: " + videoTemp, e);
			}
		}
		Path result = saved;
		Runnable report = () -> {
			if (result == null) {
				message(mc, Component.literal("[録画] 保存できませんでした(ログを見てください)").withStyle(ChatFormatting.RED));
			} else {
				Component link = Component.literal(result.toString()).withStyle(ChatFormatting.UNDERLINE)
						.withStyle(s -> s.withClickEvent(new ClickEvent.OpenFile(result.toAbsolutePath().toFile())));
				message(mc, Component.literal("[録画] 保存しました: ").withStyle(ChatFormatting.GREEN).append(link));
			}
		};
		if (wait) {
			LOGGER.info(result == null ? "録画: 保存できませんでした" : "録画: 保存しました " + result);
		} else {
			report.run();
		}
	}

	static void message(Minecraft mc, Component text) {
		mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(text));
	}
}
