package com.povreplay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;

/**
 * リプレイの再生中に、描いた画面を 1 枚ずつ読み出して、YUV にして、一時ファイルに書く。
 * 実時間の録画ではないので、絵は飛ばさない: 入れ物が空くまで、呼び出し側が待つ(capture が false を返す間は、時計を進めない)。
 */
final class FrameCapture {

	private static final int RING = 3;

	private final int cropW;
	private final int cropH;
	/** 16 の倍数に切り上げた、保存する絵の大きさ。 */
	final int padW;
	final int padH;
	private final int frameBytes;
	private final FileChannel out;
	private final ExecutorService converter = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "povreplay-convert");
		t.setDaemon(true);
		return t;
	});

	private final GpuBuffer[] ring = new GpuBuffer[RING];
	private final boolean[] busy = new boolean[RING];
	private int ringBytes;
	private final BlockingQueue<byte[]> pool = new ArrayBlockingQueue<>(RING + 1);
	private int allocated;
	private final AtomicInteger inFlight = new AtomicInteger();
	private volatile boolean failed;

	private int[] frameIndex = new int[1024];
	private int frameCount;
	private byte[] yuv;

	// 色の変換
	private final int[] coef = RecConfig.coefficients();
	private final int lumaOffset = RecConfig.fullRange ? 0 : 16;
	private final int chromaMin = RecConfig.fullRange ? 0 : 16;
	private final int chromaMax = RecConfig.fullRange ? 255 : 240;

	FrameCapture(Path videoTemp, int cropW, int cropH) throws IOException {
		this.cropW = cropW;
		this.cropH = cropH;
		this.padW = (cropW + 15) & ~15;
		this.padH = (cropH + 15) & ~15;
		this.frameBytes = padW * padH * 3 / 2;
		this.out = FileChannel.open(videoTemp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	boolean failed() {
		return failed;
	}

	/** 描画のスレッドから呼ぶ。読み出しを始められたら true。入れ物がなければ false(あとでもう一度呼ぶ)。 */
	boolean capture(RenderTarget target, int index) {
		GpuTexture texture = target.getColorTexture();
		int w = target.width;
		int h = target.height;
		if (texture == null || w < 16 || h < 16) {
			return false;
		}
		int size = w * h * texture.getFormat().blockSize();
		if (size != ringBytes) {
			if (anyBusy()) {
				return false;
			}
			closeRing();
			ringBytes = size;
		}
		int slot = -1;
		for (int i = 0; i < RING; i++) {
			if (!busy[i]) {
				slot = i;
				break;
			}
		}
		if (slot < 0) {
			return false;
		}
		byte[] raw = pool.poll();
		if (raw == null) {
			if (allocated >= RING + 1) {
				return false;
			}
			allocated++;
			raw = new byte[size];
		} else if (raw.length != size) {
			raw = new byte[size];
		}
		if (ring[slot] == null) {
			ring[slot] = RenderSystem.getDevice().createBuffer(() -> "Replay capture buffer", 9, size);
		}
		busy[slot] = true;
		inFlight.incrementAndGet();
		GpuBuffer buffer = ring[slot];
		int usedSlot = slot;
		byte[] rawBuffer = raw;
		RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
			try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
				view.data().get(0, rawBuffer, 0, size);
			} catch (RuntimeException e) {
				PovReplay.LOGGER.warn("画面の絵を読み出せませんでした", e);
				failed = true;
				pool.offer(rawBuffer);
				busy[usedSlot] = false;
				inFlight.decrementAndGet();
				return;
			}
			busy[usedSlot] = false;
			try {
				converter.execute(() -> store(rawBuffer, w, h, index));
			} catch (RejectedExecutionException e) {
				pool.offer(rawBuffer);
			} finally {
				inFlight.decrementAndGet();
			}
		}, 0);
		return true;
	}

	private boolean anyBusy() {
		for (boolean b : busy) {
			if (b) {
				return true;
			}
		}
		return false;
	}

	private void closeRing() {
		for (int i = 0; i < RING; i++) {
			if (ring[i] != null) {
				ring[i].close();
				ring[i] = null;
			}
		}
	}

	/** 読み出し中の絵が、まだ残っているか(残っている間は、時計を進めすぎない)。 */
	int pending() {
		return inFlight.get();
	}

	private void store(byte[] raw, int w, int h, int index) {
		try {
			if (yuv == null) {
				yuv = new byte[frameBytes];
			}
			toYuv(raw, w, h, yuv);
			ByteBuffer buf = ByteBuffer.wrap(yuv);
			while (buf.hasRemaining()) {
				out.write(buf);
			}
			if (frameCount == frameIndex.length) {
				frameIndex = Arrays.copyOf(frameIndex, frameCount * 2);
			}
			frameIndex[frameCount++] = index;
		} catch (IOException e) {
			failed = true;
			PovReplay.LOGGER.error("一時ファイルに書けませんでした(ディスクの空きは足りていますか)", e);
		} finally {
			pool.offer(raw);
		}
	}

	/** 読み出しと書き込みが終わるのを待って、一時ファイルを閉じる。書いたフレームの番号の並びを返す。 */
	int[] finish() throws IOException, InterruptedException {
		for (int i = 0; i < 200 && inFlight.get() > 0; i++) {
			Thread.sleep(50);
		}
		converter.shutdown();
		converter.awaitTermination(120, TimeUnit.SECONDS);
		out.close();
		closeRing();
		return Arrays.copyOf(frameIndex, frameCount);
	}

	/** 描画のスレッドで、入れ物を片づける(finish のあとに呼ぶ)。 */
	void release() {
		closeRing();
	}

	/**
	 * 読み出した絵(上下が逆で、1 画素が R,G,B,A の順)を、動画の大きさの YUV420 にする。上下も直す。
	 * 画面の大きさと動画の大きさが違っても、拡大も縮小もしない。画面が大きければ真ん中を切り取り、小さければ真ん中に置いて、まわりを黒にする。
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
		int outW = padW;
		int outH = padH;
		int cw = outW / 2;
		int uOff = outW * outH;
		int vOff = uOff + cw * (outH / 2);
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
}
