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
	private static final int MAX_IN_FLIGHT = 6;
	/** 書き込み待ちの YUV の絵の数(1 枚 約 3MB)。ディスクが追いつかない間、ここに溜める。 */
	private static final int MAX_YUV = 30;

	private static volatile Recorder active;

	private final Minecraft mc;
	/** 音だけの録音か(映像は取らない)。 */
	private final boolean audioOnly;
	// 色の変換(RecConfig で決まる)
	private int[] coef;
	private int lumaOffset;
	private int chromaMin;
	private int chromaMax;
	private final Path file;
	private final Path videoTemp;
	private final Path audioTemp;
	private final long startNanos = System.nanoTime();
	/** 録画する大きさ(画面の大きさを、16 の倍数に切り下げたもの)。 */
	/** 動画の大きさ(設定。標準は 1920x1080 で、画面の大きさによらず固定)。 */
	final int cropW;
	final int cropH;
	/** H.264 は 16 画素ごとの区画で作るので、保存する絵は、16 の倍数に切り上げた大きさ(はみ出す分は黒で、動画では切り取られる)。 */
	final int outW;
	final int outH;
	private final int frameBytes;

	private final FileChannel videoOut;   // 録音(音だけ)のときは null
	private final OutputStream audioOut;
	private final AudioMixer mixer;
	/** 画面の絵を YUV にする。 */
	private final ExecutorService converter = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "recmod-convert");
		t.setDaemon(true);
		return t;
	});
	/** YUV を一時ファイルに書く。ディスクが一時的に遅くなっても、変換が止まらないよう、別のスレッドにする。 */
	private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "recmod-write");
		t.setDaemon(true);
		return t;
	});
	private final BlockingQueue<byte[]> yuvPool = new ArrayBlockingQueue<>(MAX_YUV);
	private int yuvAllocated;
	private final BlockingQueue<byte[]> pool = new ArrayBlockingQueue<>(MAX_IN_FLIGHT);
	private int allocated;

	private int nextFrame;
	// 録画が終わったときに、記録として残す数字
	private long totalCostNanos;
	private int capturedFrames;
	private int droppedFrames;
	/** GPU から読み出す入れ物。毎回作り直すと重いので、使い回す。 */
	private final GpuBuffer[] ring = new GpuBuffer[4];
	private final boolean[] ringBusy = new boolean[4];
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

	/** 録音(音だけ)中か。 */
	public static boolean isAudioOnly() {
		Recorder r = active;
		return r != null && r.audioOnly;
	}

	/** 録画していなければ始め、していれば止める。描画のスレッドから呼ぶ。 */
	public static void toggle(Minecraft mc) {
		if (active == null) {
			start(mc);
		} else if (!active.audioOnly) {
			stop(mc);
		}
	}

	/** 録音(音だけ)を、同じように始める・止める。録画中は何もしない。 */
	public static void toggleAudio(Minecraft mc) {
		if (active == null) {
			startAudio(mc);
		} else if (active.audioOnly) {
			stop(mc);
		}
	}

	public static void startAudio(Minecraft mc) {
		if (active != null) {
			return;
		}
		if (Exporter.isBusy()) {
			message(mc, Component.literal("[録音] 前の録画・録音を保存している途中です。終わるまで待ってください").withStyle(ChatFormatting.YELLOW));
			return;
		}
		try {
			active = new Recorder(mc, 0, 0, true);
			message(mc, Component.literal("[録音] 録音を始めました(ゲームの音だけを録音します)").withStyle(ChatFormatting.GREEN));
		} catch (IOException | RuntimeException e) {
			LOGGER.error("録音を始められませんでした", e);
			message(mc, Component.literal("[録音] 始められませんでした: " + e.getMessage()).withStyle(ChatFormatting.RED));
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
			active = new Recorder(mc, target.width, target.height, false);
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
		message(mc, r.audioOnly
				? Component.literal("[録音] 録音を止めました。保存しています…")
				: Component.literal("[録画] 録画を止めました。動画に変換して保存しています(録画の長さによって、時間がかかります)…"));
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

	private Recorder(Minecraft mc, int width, int height, boolean audioOnly) throws IOException {
		this.mc = mc;
		this.audioOnly = audioOnly;
		if (!audioOnly) {
			RecConfig.load(mc.gameDirectory.toPath());
			coef = RecConfig.coefficients();
			lumaOffset = RecConfig.fullRange ? 0 : 16;
			chromaMin = RecConfig.fullRange ? 0 : 16;
			chromaMax = RecConfig.fullRange ? 255 : 240;
		}
		this.cropW = audioOnly ? 0 : RecConfig.width;
		this.cropH = audioOnly ? 0 : RecConfig.height;
		this.outW = (cropW + 15) & ~15;
		this.outH = (cropH + 15) & ~15;
		this.frameBytes = outW * outH * 3 / 2;
		this.file = chooseFile(mc, audioOnly);
		this.videoTemp = Path.of(file + ".video.tmp");
		this.audioTemp = Path.of(file + ".audio.tmp");
		this.videoOut = audioOnly ? null
				: FileChannel.open(videoTemp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
		this.audioOut = new BufferedOutputStream(Files.newOutputStream(audioTemp), 1 << 16);
		this.mixer = new AudioMixer(startNanos, this::writeAudio);
		this.mixer.start();
	}

	/** ダウンロードフォルダーに、日時のついた名前で保存する(フォルダーがなければ、ゲームのフォルダーの recordings)。 */
	private static Path chooseFile(Minecraft mc, boolean audioOnly) throws IOException {
		Path dir = Path.of(System.getProperty("user.home", "."), "Downloads");
		if (!Files.isDirectory(dir)) {
			dir = mc.gameDirectory.toPath().resolve("recordings");
			Files.createDirectories(dir);
		}
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		String name = audioOnly ? "minecraft-audio-" + stamp + ".m4a" : "minecraft-" + stamp + ".mp4";
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
		if (audioOnly) {
			return;
		}
		long now = System.nanoTime();
		long issueStart = now;
		if (!capturing || failed) {
			return;
		}
		// 30fps の枠(1/30 秒ごと)に、いちばん近い時刻の描画を取る。枠の半分より手前なら、次の描画まで待たずに取る
		double due = (now - startNanos) * (double) FPS / 1e9;
		if (due + 0.5 < nextFrame) {
			return;
		}
		int frame = Math.max(nextFrame, (int) Math.round(due));
		GpuTexture texture = target.getColorTexture();
		int w = target.width;
		int h = target.height;
		if (texture == null || w < 16 || h < 16) {
			return;
		}
		int size = w * h * texture.getFormat().blockSize();
		if (size != ringBytes) {
			if (anyBusy()) {
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
			droppedFrames++;
			return;   // 読み出しが追いついていない
		}
		byte[] raw = acquire(size);
		if (raw == null) {
			droppedFrames++;
			return;   // 変換が追いついていない
		}
		if (ring[slot] == null) {
			ring[slot] = RenderSystem.getDevice().createBuffer(() -> "Recording buffer", 9, size);
		}
		nextFrame = frame + 1;
		ringBusy[slot] = true;
		inFlight.incrementAndGet();

		GpuBuffer buffer = ring[slot];
		int usedSlot = slot;
		long issueNanos = System.nanoTime() - issueStart;
		RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
			long copyStart = System.nanoTime();
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
			long cost = issueNanos + (System.nanoTime() - copyStart);
			totalCostNanos += cost;
			capturedFrames++;
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

	private boolean anyBusy() {
		for (boolean b : ringBusy) {
			if (b) {
				return true;
			}
		}
		return false;
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
		if (!anyBusy()) {
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

	private void store(byte[] raw, int w, int h, int frame) {
		byte[] yuv = yuvPool.poll();
		if (yuv == null && yuvAllocated < MAX_YUV) {
			yuvAllocated++;
			yuv = new byte[frameBytes];
		}
		if (yuv == null) {
			droppedFrames++;   // ディスクへの書き込みが、ずっと追いついていない
			release(raw);
			return;
		}
		try {
			toYuv(raw, w, h, yuv);
		} finally {
			release(raw);
		}
		byte[] out = yuv;
		writer.execute(() -> write(out, frame));
	}

	/** 書き込みのスレッド。 */
	private void write(byte[] yuv, int frame) {
		try {
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
			yuvPool.offer(yuv);
		}
	}

	/**
	 * 読み出した絵(上下が逆で、1 画素が R,G,B,A の順)を、動画の大きさの YUV420 にする。上下も直す。
	 * 画面の大きさと動画の大きさが違っても、拡大も縮小もしない。画面が大きければ真ん中を切り取り、小さければ真ん中に置いて、まわりを黒にする。
	 * 色の式と範囲は、設定(RecConfig。標準は BT.709・16〜235)で、MP4 にも同じ指定を書く。
	 * 値は、JCodec がそのまま使えるよう、128 を引いた符号つきバイト。
	 */
	private void toYuv(byte[] raw, int w, int h, byte[] out) {
		int[] c = coef;
		int yr = c[0], yg = c[1], yb = c[2];
		int ur = c[3], ug = c[4], ub = c[5];
		int vr = c[6], vg = c[7], vb = c[8];
		int yo = lumaOffset - 128;
		int cMin = chromaMin;
		int cMax = chromaMax;
		int cw = outW / 2;
		int uOff = outW * outH;
		int vOff = uOff + cw * (outH / 2);
		// 画面を動画のどこに置くか(2 の倍数にそろえて、色の 2x2 の区切りをずらさない)
		int offX = Math.floorDiv(cropW - w, 2) & ~1;
		int offY = Math.floorDiv(cropH - h, 2) & ~1;
		for (int y = 0; y < outH; y += 2) {
			int sy = y - offY;
			boolean rowInside = y < cropH && sy >= 0 && sy + 1 < h;
			int row0 = (h - 1 - sy) * w * 4;
			int row1 = (h - 2 - sy) * w * 4;
			for (int x = 0; x < outW; x += 2) {
				int sx = x - offX;
				int ci = (y / 2) * cw + x / 2;
				if (!rowInside || x >= cropW || sx < 0 || sx + 1 >= w) {
					out[y * outW + x] = (byte) yo;
					out[y * outW + x + 1] = (byte) yo;
					out[(y + 1) * outW + x] = (byte) yo;
					out[(y + 1) * outW + x + 1] = (byte) yo;
					out[uOff + ci] = 0;
					out[vOff + ci] = 0;
					continue;
				}
				int sx0 = sx * 4;
				int sx1 = sx0 + 4;
				int r0 = raw[row0 + sx0] & 0xFF, g0 = raw[row0 + sx0 + 1] & 0xFF, b0 = raw[row0 + sx0 + 2] & 0xFF;
				int r1 = raw[row0 + sx1] & 0xFF, g1 = raw[row0 + sx1 + 1] & 0xFF, b1 = raw[row0 + sx1 + 2] & 0xFF;
				int r2 = raw[row1 + sx0] & 0xFF, g2 = raw[row1 + sx0 + 1] & 0xFF, b2 = raw[row1 + sx0 + 2] & 0xFF;
				int r3 = raw[row1 + sx1] & 0xFF, g3 = raw[row1 + sx1 + 1] & 0xFF, b3 = raw[row1 + sx1 + 2] & 0xFF;
				out[y * outW + x] = (byte) Math.max(-128, Math.min(127, yo + ((yr * r0 + yg * g0 + yb * b0 + 128) >> 8)));
				out[y * outW + x + 1] = (byte) Math.max(-128, Math.min(127, yo + ((yr * r1 + yg * g1 + yb * b1 + 128) >> 8)));
				out[(y + 1) * outW + x] = (byte) Math.max(-128, Math.min(127, yo + ((yr * r2 + yg * g2 + yb * b2 + 128) >> 8)));
				out[(y + 1) * outW + x + 1] = (byte) Math.max(-128, Math.min(127, yo + ((yr * r3 + yg * g3 + yb * b3 + 128) >> 8)));
				int r = (r0 + r1 + r2 + r3) >> 2;
				int g = (g0 + g1 + g2 + g3) >> 2;
				int b = (b0 + b1 + b2 + b3) >> 2;
				int u = 128 + ((ur * r + ug * g + ub * b + 128) >> 8);
				int v = 128 + ((vr * r + vg * g + vb * b + 128) >> 8);
				out[uOff + ci] = (byte) (Math.max(cMin, Math.min(cMax, u)) - 128);
				out[vOff + ci] = (byte) (Math.max(cMin, Math.min(cMax, v)) - 128);
			}
		}
	}

	// ---- 止める(別のスレッド) ----

	private void export(boolean wait) {
		capturing = false;
		long elapsed = System.nanoTime() - startNanos;
		String what = audioOnly ? "録音" : "録画";
		if (!audioOnly) {
			LOGGER.info(String.format("録画の記録: %.1f 秒、取った絵 %d 枚、描画スレッドの負荷 1 枚あたり平均 %.2f ms(全体の %.1f%%)、取れなかった絵 %d 枚(理想は %.0f 枚)",
					elapsed / 1e9, capturedFrames, capturedFrames == 0 ? 0 : totalCostNanos / 1e6 / capturedFrames,
					totalCostNanos * 100.0 / Math.max(1, elapsed), droppedFrames, elapsed / 1e9 * FPS));
		}
		Path saved = null;
		try {
			mixer.stop();
			audioOut.close();
			if (audioOnly) {
				saved = Exporter.runAudio(file, audioTemp);
			} else {
				for (int i = 0; i < 100 && inFlight.get() > 0; i++) {
					Thread.sleep(50);   // GPU から読み出し中の絵が届くのを待つ
				}
				converter.shutdown();
				converter.awaitTermination(30, TimeUnit.SECONDS);
				writer.shutdown();
				writer.awaitTermination(120, TimeUnit.SECONDS);
				videoOut.close();
				if (frameCount > 0) {
					int total = (int) (elapsed * FPS / 1_000_000_000L);
					saved = Exporter.run(mc, file, videoTemp, audioTemp, cropW, cropH, outW, outH, Arrays.copyOf(frameIndex, frameCount), total);
				} else {
					LOGGER.error("映像が 1 フレームも取れませんでした(描画への割り込みが効いていない、または画面を読み出せません)");
				}
			}
		} catch (IOException | InterruptedException | RuntimeException e) {
			LOGGER.error(what + "の保存に失敗しました", e);
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
				message(mc, Component.literal("[" + what + "] 保存できませんでした(ログを見てください)").withStyle(ChatFormatting.RED));
			} else {
				Component link = Component.literal(result.toString()).withStyle(ChatFormatting.UNDERLINE)
						.withStyle(s -> s.withClickEvent(new ClickEvent.OpenFile(result.toAbsolutePath().toFile())));
				message(mc, Component.literal("[" + what + "] 保存しました: ").withStyle(ChatFormatting.GREEN).append(link));
			}
		};
		if (wait) {
			LOGGER.info(result == null ? what + ": 保存できませんでした" : what + ": 保存しました " + result);
		} else {
			report.run();
		}
	}

	static void message(Minecraft mc, Component text) {
		mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(text));
	}
}
