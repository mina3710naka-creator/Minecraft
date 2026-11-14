package com.povreplay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;

/**
 * リプレイの再生中に、描いた画面を 1 枚ずつ読み出して、その場で、H.264 に圧縮して、MP4 に書いていく。
 * (圧縮していない絵を、ディスクに溜めない。長い録画でも、ディスクを使いきらない。)
 *
 * 流れ: GPU から読み出す(描画のスレッド) → YUV にする(変換のスレッド) → 15 フレームごとに区切って、別々のスレッドで圧縮
 *       → 描画のスレッドが、できた順に、MP4 に書く(pump)。
 * 実時間の録画ではないので、絵は飛ばさない。圧縮が追いつかないときは、呼び出し側が時計を止めて待つ(backlog が多い間)。
 */
final class FrameCapture {

	private static final int RING = 3;
	/** 圧縮待ち・書き込み待ちのフレームが、これだけ溜まったら、時計を止めて待つ。 */
	static final int MAX_BACKLOG = Exporter.GOP * 4;

	private final int cropW;
	private final int cropH;
	/** 16 の倍数に切り上げた、圧縮する絵の大きさ。 */
	private final int padW;
	private final int padH;
	/** 内蔵のエンコーダーで書くときの MP4(ffmpeg を使うときは null)。 */
	private final Mp4Writer mp4;
	/** ffmpeg を使うときの出力(内蔵のエンコーダーのときは null)。 */
	private final FfmpegSink ffmpeg;
	private final ExecutorService converter = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "povreplay-convert");
		t.setDaemon(true);
		return t;
	});
	private final ExecutorService encoders;   // 内蔵のエンコーダーのときだけ
	private final ThreadLocal<ByteBuffer> encodeBuffers;

	private final GpuBuffer[] ring = new GpuBuffer[RING];
	private final boolean[] busy = new boolean[RING];
	private int ringBytes;
	private final BlockingQueue<byte[]> pool = new ArrayBlockingQueue<>(RING + 1);
	private int allocated;
	private final AtomicInteger inFlight = new AtomicInteger();
	/** 読み出しを始めてから、MP4 に書き終わるまでのフレームの数。 */
	private final AtomicInteger backlog = new AtomicInteger();
	private volatile boolean failed;

	// 15 フレームぶんを集めて、圧縮に出す(変換のスレッドだけが触る)
	private final List<byte[][]> gopFrames = new ArrayList<>();
	private int gopFirstIndex;
	private final ArrayDeque<Gop> gops = new ArrayDeque<>();
	private volatile int written;
	private byte[][] ffmpegPlanes;
	private final int totalFrames;

	// 音
	private final Future<byte[][]> audioFuture;   // 内蔵のエンコーダーのときだけ
	private final ExecutorService audioPool;
	private int audioWritten;

	private volatile boolean flushed;

	private record Gop(int firstIndex, int count, Future<List<byte[]>> samples) {
	}

	// 色の変換
	private final int[] coef = RecConfig.coefficients();
	private final int lumaOffset = RecConfig.fullRange ? 0 : 16;
	private final int chromaMin = RecConfig.fullRange ? 0 : 16;
	private final int chromaMax = RecConfig.fullRange ? 255 : 240;

	/** ffmpeg が null なら、内蔵のエンコーダー(JCodec)で、MP4 に書く。ffmpeg があれば、絵を ffmpeg に渡す。 */
	FrameCapture(Path outPart, Path pcm, int cropW, int cropH, int totalFrames, FfmpegSink ffmpeg) throws IOException {
		this.cropW = cropW;
		this.cropH = cropH;
		this.padW = (cropW + 15) & ~15;
		this.padH = (cropH + 15) & ~15;
		this.totalFrames = totalFrames;
		this.ffmpeg = ffmpeg;
		if (ffmpeg != null) {
			this.mp4 = null;
			this.encoders = null;
			this.encodeBuffers = null;
			this.audioPool = null;
			this.audioFuture = null;
			return;
		}
		this.mp4 = new Mp4Writer(outPart, cropW, cropH, ReplayEngine.FPS, AudioMixer.RATE);
		int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
		this.encoders = Executors.newFixedThreadPool(threads, r -> {
			Thread t = new Thread(r, "povreplay-encode");
			t.setDaemon(true);
			return t;
		});
		this.encodeBuffers = ThreadLocal.withInitial(() -> ByteBuffer.allocate(padW * padH * 3));
		// 音は、先に、別のスレッドで、全部 AAC にしておく(小さいので、メモリに持てる)。映像の圧縮のスレッドとは、別にする
		this.audioPool = Executors.newFixedThreadPool(2, r -> {
			Thread t = new Thread(r, "povreplay-audio-chunk");
			t.setDaemon(true);
			return t;
		});
		FutureTask<byte[][]> audioTask = new FutureTask<>(() -> Exporter.encodeAudio(audioPool, pcm));
		Thread audioThread = new Thread(audioTask, "povreplay-audio");
		audioThread.setDaemon(true);
		audioThread.start();
		this.audioFuture = audioTask;
	}

	boolean failed() {
		return failed;
	}

	/** 読み出し中・変換中・圧縮中・書き込み待ちの、フレームの数。 */
	int backlog() {
		return backlog.get();
	}

	/** GPU から読み出し中(まだ届いていない)の絵の数。 */
	int pending() {
		return inFlight.get();
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
		backlog.incrementAndGet();
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

	/** 描画のスレッドで、入れ物を片づける(読み出しが全部届いたあとに呼ぶ)。 */
	void release() {
		closeRing();
	}

	// ---- 変換と圧縮に出す(変換のスレッド) ----

	private void store(byte[] raw, int w, int h, int index) {
		try {
			if (ffmpeg != null) {
				// ffmpeg に渡す: 符号なしの YUV(同じ入れ物を使い回して、順番に書く)
				if (ffmpegPlanes == null) {
					ffmpegPlanes = new byte[][] { new byte[padW * padH], new byte[padW * padH / 4], new byte[padW * padH / 4] };
				}
				toYuv(raw, w, h, ffmpegPlanes, 0);
				ffmpeg.write(ffmpegPlanes, padW, cropW, cropH);
				written++;
				backlog.decrementAndGet();
				return;
			}
			byte[][] planes = { new byte[padW * padH], new byte[padW * padH / 4], new byte[padW * padH / 4] };
			toYuv(raw, w, h, planes, -128);
			if (gopFrames.isEmpty()) {
				gopFirstIndex = index;
			}
			gopFrames.add(planes);
			if (gopFrames.size() >= Exporter.GOP) {
				submitGop();
			}
		} catch (IOException e) {
			failed = true;
			PovReplay.LOGGER.error("ffmpeg に絵を渡せませんでした: " + (ffmpeg == null ? "" : ffmpeg.tail()), e);
		} catch (RuntimeException e) {
			failed = true;
			PovReplay.LOGGER.error("絵を変換できませんでした", e);
		} finally {
			pool.offer(raw);
		}
	}

	private void submitGop() {
		List<byte[][]> frames = new ArrayList<>(gopFrames);
		gopFrames.clear();
		int first = gopFirstIndex;
		Future<List<byte[]>> samples = encoders.submit(() -> encodeGop(frames));
		synchronized (gops) {
			gops.add(new Gop(first, frames.size(), samples));
		}
	}

	/** 先頭だけキーフレームで、残りは直前の絵との差で、圧縮する(圧縮のスレッド)。 */
	private List<byte[]> encodeGop(List<byte[][]> frames) {
		H264Encoder encoder = Exporter.newEncoder(Exporter.GOP);
		ByteBuffer buffer = encodeBuffers.get();
		List<byte[]> samples = new ArrayList<>(frames.size());
		for (byte[][] planes : frames) {
			Picture picture = new Picture(padW, padH, planes, null, ColorSpace.YUV420J, 0, new Rect(0, 0, cropW, cropH));
			buffer.clear();
			ByteBuffer annexB = encoder.encodeFrame(picture, buffer).getData();
			samples.add(Exporter.toSample(annexB, mp4));
		}
		return samples;
	}

	// ---- MP4 に書く(描画のスレッドが、毎フレーム呼ぶ) ----

	/** 圧縮が終わった分を、順番に、MP4 に書く。 */
	void pump() {
		if (ffmpeg != null) {
			return;
		}
		try {
			while (true) {
				Gop head;
				synchronized (gops) {
					head = gops.peek();
					if (head == null || !head.samples().isDone()) {
						break;
					}
					gops.poll();
				}
				List<byte[]> samples = head.samples().get();
				for (int i = 0; i < samples.size(); i++) {
					mp4.writeVideo(samples.get(i), head.firstIndex() + i);
				}
				written += samples.size();
				backlog.addAndGet(-samples.size());
				writeAudioUpTo(head.firstIndex() + samples.size());
			}
		} catch (IOException | ExecutionException e) {
			failed = true;
			PovReplay.LOGGER.error("動画に書けませんでした", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			failed = true;
		}
	}

	/** この時間(フレーム番号)までの音を、一緒に書く(映像と音が、ファイルの中で近くに並ぶように)。 */
	private void writeAudioUpTo(int frame) throws IOException, InterruptedException, ExecutionException {
		if (!audioFuture.isDone()) {
			return;
		}
		byte[][] audio = audioFuture.get();
		long untilSample = (long) frame * AudioMixer.RATE / ReplayEngine.FPS;
		while (audioWritten < audio.length && (long) audioWritten * AacEncoder.FRAME < untilSample) {
			mp4.writeAudio(audio[audioWritten++]);
		}
	}

	/** 最後の、15 フレームに満たない分を、圧縮に出す。 */
	void flush() {
		if (flushed) {
			return;
		}
		flushed = true;
		try {
			converter.execute(() -> {
				if (ffmpeg != null) {
					try {
						ffmpeg.finish();   // ffmpeg が、音を混ぜて、ファイルを閉じるのを待つ
					} catch (IOException | InterruptedException e) {
						failed = true;
						PovReplay.LOGGER.error("ffmpeg の仕上げに失敗しました", e);
					}
				} else if (!gopFrames.isEmpty()) {
					submitGop();
				}
				flushedDone = true;
			});
		} catch (RejectedExecutionException e) {
			failed = true;
		}
	}

	private volatile boolean flushedDone;

	/** 全部のフレームを、MP4 に書き終えたか(flush のあとで、pump を呼びながら待つ)。 */
	boolean complete() {
		synchronized (gops) {
			return flushedDone && gops.isEmpty();
		}
	}

	/** 書いたフレームの数(進み具合の表示用)。 */
	int written() {
		return written;
	}

	/** 残りの音を書いて、MP4 を閉じる。 */
	void close() throws IOException {
		if (ffmpeg != null) {
			converter.shutdownNow();
			return;
		}
		try {
			byte[][] audio = audioFuture.get();
			while (audioWritten < audio.length) {
				mp4.writeAudio(audio[audioWritten++]);
			}
		} catch (ExecutionException e) {
			throw new IOException(e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		} finally {
			converter.shutdownNow();
			encoders.shutdownNow();
			audioPool.shutdownNow();
		}
		if (!mp4.hasParameterSets() || mp4.videoFrames() == 0) {
			mp4.close();
			throw new IOException("映像が 1 フレームもありません");
		}
		mp4.finish(totalFrames);
	}

	/** 失敗・中止のとき: 書きかけのファイルを閉じて、処理を止める。 */
	void abort() {
		converter.shutdownNow();
		if (ffmpeg != null) {
			ffmpeg.abort();
			return;
		}
		encoders.shutdownNow();
		audioPool.shutdownNow();
		try {
			mp4.close();
		} catch (IOException ignored) {
			// 閉じるだけ
		}
	}

	/**
	 * 読み出した絵(上下が逆で、1 画素が R,G,B,A の順)を、動画の大きさの YUV420 にする。上下も直す。
	 * 画面の大きさと動画の大きさが違っても、拡大も縮小もしない。画面が大きければ真ん中を切り取り、小さければ真ん中に置いて、まわりを黒にする。
	 * 値は、JCodec がそのまま使えるよう、128 を引いた符号つきバイト。
	 */
	private void toYuv(byte[] raw, int w, int h, byte[][] planes, int bias) {
		byte[] yPlane = planes[0];
		byte[] uPlane = planes[1];
		byte[] vPlane = planes[2];
		int[] c = coef;
		int yr = c[0], yg = c[1], yb = c[2];
		int ur = c[3], ug = c[4], ub = c[5];
		int vr = c[6], vg = c[7], vb = c[8];
		int yo = lumaOffset;
		int cMin = chromaMin;
		int cMax = chromaMax;
		int outW = padW;
		int outH = padH;
		int cw = outW / 2;
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
					yPlane[y * outW + x] = (byte) (yo + bias);
					yPlane[y * outW + x + 1] = (byte) (yo + bias);
					yPlane[(y + 1) * outW + x] = (byte) (yo + bias);
					yPlane[(y + 1) * outW + x + 1] = (byte) (yo + bias);
					uPlane[ci] = (byte) (128 + bias);
					vPlane[ci] = (byte) (128 + bias);
					continue;
				}
				int sx0 = sx * 4;
				int sx1 = sx0 + 4;
				int r0 = raw[row0 + sx0] & 0xFF, g0 = raw[row0 + sx0 + 1] & 0xFF, b0 = raw[row0 + sx0 + 2] & 0xFF;
				int r1 = raw[row0 + sx1] & 0xFF, g1 = raw[row0 + sx1 + 1] & 0xFF, b1 = raw[row0 + sx1 + 2] & 0xFF;
				int r2 = raw[row1 + sx0] & 0xFF, g2 = raw[row1 + sx0 + 1] & 0xFF, b2 = raw[row1 + sx0 + 2] & 0xFF;
				int r3 = raw[row1 + sx1] & 0xFF, g3 = raw[row1 + sx1 + 1] & 0xFF, b3 = raw[row1 + sx1 + 2] & 0xFF;
				yPlane[y * outW + x] = (byte) (Math.max(0, Math.min(255, yo + ((yr * r0 + yg * g0 + yb * b0 + 128) >> 8))) + bias);
				yPlane[y * outW + x + 1] = (byte) (Math.max(0, Math.min(255, yo + ((yr * r1 + yg * g1 + yb * b1 + 128) >> 8))) + bias);
				yPlane[(y + 1) * outW + x] = (byte) (Math.max(0, Math.min(255, yo + ((yr * r2 + yg * g2 + yb * b2 + 128) >> 8))) + bias);
				yPlane[(y + 1) * outW + x + 1] = (byte) (Math.max(0, Math.min(255, yo + ((yr * r3 + yg * g3 + yb * b3 + 128) >> 8))) + bias);
				int r = (r0 + r1 + r2 + r3) >> 2;
				int g = (g0 + g1 + g2 + g3) >> 2;
				int b = (b0 + b1 + b2 + b3) >> 2;
				int u = 128 + ((ur * r + ug * g + ub * b + 128) >> 8);
				int v = 128 + ((vr * r + vg * g + vb * b + 128) >> 8);
				uPlane[ci] = (byte) (Math.max(cMin, Math.min(cMax, u)) + bias);
				vPlane[ci] = (byte) (Math.max(cMin, Math.min(cMax, v)) + bias);
			}
		}
	}
}
