package com.recmod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.encode.RateControl;
import org.jcodec.codecs.h264.io.model.SliceType;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rect;
import org.jcodec.common.model.Size;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.encode.RateControl;
import org.jcodec.codecs.h264.io.model.SliceType;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rect;
import org.jcodec.common.model.Size;
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
 * 録画の本体。
 * 映像: 描画のたびに(GameRendererMixin から)呼ばれ、ホットバーなどの画面(GUI)を描く直前の、ワールドだけの絵を読み出す。
 * 読み出した絵は、別のスレッドで縮めて H.264 にし、順番どおりに MP4 へ書く。
 * 音: AudioMixer がゲームの音を混ぜて、MP4 へ書く。
 * 停止すると、ダウンロードフォルダーに保存する。
 */
public final class Recorder {

	public static final Logger LOGGER = LoggerFactory.getLogger("recmod");

	private static final int FPS = 30;
	/** 映像の幅の上限(これより大きい画面は、縦横の比を保って縮める)。ファイルが大きくなりすぎないように。 */
	private static final int MAX_WIDTH = 1280;
	/** H.264 の量子化の細かさ(小さいほどきれいで、ファイルが大きい)。 */
	private static final int H264_QP = 26;
	/** 同時に持てる、読み出した絵の数。エンコードが追いつかないときは、絵を飛ばす(同じ絵を続けて使う)。 */
	private static final int MAX_IN_FLIGHT = 6;

	private static volatile Recorder active;

	private final Path file;
	private final Mp4Writer mp4;
	private final AudioMixer mixer;
	private final AacSink audioSink;
	private final long startNanos = System.nanoTime();
	private final int outW;
	private final int outH;

	private final ExecutorService encoders = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "recmod-encode");
		t.setDaemon(true);
		return t;
	});
	private final BlockingQueue<Pending> pending = new LinkedBlockingQueue<>();
	private final BlockingQueue<byte[]> pool = new ArrayBlockingQueue<>(MAX_IN_FLIGHT);
	private final AtomicInteger allocated = new AtomicInteger();
	private final Thread writer;

	private int nextFrame;
	private volatile boolean capturing = true;
	private volatile boolean failed;

	private record Pending(int index, CompletableFuture<byte[]> sample) {
	}

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
		RenderTarget target = mc.gameRenderer.mainRenderTarget();
		if (target == null || target.width <= 0 || target.height <= 0) {
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
		message(mc, Component.literal("[録画] 保存しています…"));
		r.finish(mc);
	}

	/** ゲームを終えるときなど、メッセージを出さずに止めて、保存を終えるまで待つ。 */
	public static void stopAndWait() {
		Recorder r = active;
		if (r == null) {
			return;
		}
		active = null;
		r.finishNow();
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
		this.file = chooseFile(mc);
		int w = Math.min(width, MAX_WIDTH);
		int h = Math.round(height * (w / (float) width));
		// H.264 は 16 画素ごとの区画で作るので、幅と高さを 16 の倍数にそろえる(縦横の比は、1% ほどしか変わらない)
		this.outW = Math.max(16, Math.round(w / 16F) * 16);
		this.outH = Math.max(16, Math.round(h / 16F) * 16);
		this.mp4 = new Mp4Writer(file, outW, outH, FPS, AudioMixer.RATE);
		this.audioSink = new AacSink(mp4);
		this.mixer = new AudioMixer(startNanos, audioSink::accept);
		this.mixer.start();
		this.writer = new Thread(() -> writeLoop(mc), "recmod-writer");
		this.writer.setDaemon(true);
		this.writer.start();
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

	// ---- 映像を読み出す(描画のスレッド) ----

	private void capture(RenderTarget target) {
		if (!capturing || failed) {
			return;
		}
		int frame = (int) ((System.nanoTime() - startNanos) * FPS / 1_000_000_000L);
		if (frame < nextFrame) {
			return;
		}
		GpuTexture texture = target.getColorTexture();
		int w = target.width;
		int h = target.height;
		if (texture == null || w <= 0 || h <= 0) {
			return;
		}
		int size = w * h * texture.getFormat().blockSize();
		byte[] raw = acquire(size);
		if (raw == null) {
			return;   // エンコードが追いついていない。この描画では、絵を取らない
		}
		nextFrame = frame + 1;
		CompletableFuture<byte[]> sample = new CompletableFuture<>();
		pending.add(new Pending(frame, sample));

		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Recording buffer", 9, size);
		RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
			try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
				ByteBuffer data = view.data();
				data.get(0, raw, 0, size);
			} catch (RuntimeException e) {
				sample.completeExceptionally(e);
				release(raw);
				buffer.close();
				return;
			}
			buffer.close();
			encoders.execute(() -> {
				try {
					sample.complete(encode(raw, w, h));
				} catch (Throwable t) {
					sample.completeExceptionally(t);
				} finally {
					release(raw);
				}
			});
		}, 0);
	}

	private byte[] acquire(int size) {
		byte[] b = pool.poll();
		if (b == null) {
			if (allocated.get() >= MAX_IN_FLIGHT) {
				return null;
			}
			allocated.incrementAndGet();
			return new byte[size];
		}
		return b.length == size ? b : new byte[size];   // 画面の大きさが変わったときは、作り直す
	}

	private void release(byte[] b) {
		pool.offer(b);
	}

	// ---- 縮めて H.264 にする(エンコードのスレッド) ----

	/** スレッドごとのエンコーダーと作業用の入れ物。すべてのフレームを単独で見られるフレーム(IDR)にするので、スレッドで分けても困らない。 */
	private final class EncoderContext {
		final H264Encoder encoder;
		final Picture picture;
		final ByteBuffer buffer;
		final int[] rgb = new int[outW * outH];

		EncoderContext() {
			RateControl fixed = new RateControl() {
				@Override
				public int startPicture(Size sz, int maxSize, SliceType sliceType) {
					return H264_QP;
				}

				@Override
				public int initialQpDelta() {
					return 0;
				}

				@Override
				public int accept(int bits) {
					return 0;
				}
			};
			encoder = new H264Encoder(fixed);
			encoder.setKeyInterval(1);
			picture = new Picture(outW, outH, new byte[][] { new byte[outW * outH], new byte[outW * outH / 4], new byte[outW * outH / 4] },
					null, ColorSpace.YUV420J, 0, new Rect(0, 0, outW, outH));
			buffer = ByteBuffer.allocate(outW * outH * 3);
		}
	}

	private final ThreadLocal<EncoderContext> contexts = ThreadLocal.withInitial(EncoderContext::new);

	private byte[] encode(byte[] raw, int w, int h) {
		EncoderContext ctx = contexts.get();
		int[] rgb = ctx.rgb;
		// 読み出した絵は、上下が逆で、1 画素が R,G,B,A の順。縮めながら、上下を直す
		for (int y = 0; y < outH; y++) {
			float sy = (y + 0.5F) * h / outH - 0.5F;
			int y0 = Math.max(0, Math.min(h - 1, (int) Math.floor(sy)));
			int y1 = Math.min(h - 1, y0 + 1);
			int fy = Math.max(0, Math.min(256, Math.round((sy - (int) Math.floor(sy)) * 256F)));
			int row0 = (h - 1 - y0) * w * 4;
			int row1 = (h - 1 - y1) * w * 4;
			for (int x = 0; x < outW; x++) {
				float sx = (x + 0.5F) * w / outW - 0.5F;
				int x0 = Math.max(0, Math.min(w - 1, (int) Math.floor(sx)));
				int x1 = Math.min(w - 1, x0 + 1);
				int fx = Math.max(0, Math.min(256, Math.round((sx - (int) Math.floor(sx)) * 256F)));
				int packed = 0;
				for (int c = 0; c < 3; c++) {
					int a = raw[row0 + x0 * 4 + c] & 0xFF;
					int bb = raw[row0 + x1 * 4 + c] & 0xFF;
					int cc = raw[row1 + x0 * 4 + c] & 0xFF;
					int d = raw[row1 + x1 * 4 + c] & 0xFF;
					int top = a * (256 - fx) + bb * fx;
					int bottom = cc * (256 - fx) + d * fx;
					int v = (top * (256 - fy) + bottom * fy) >> 16;
					packed = (packed << 8) | v;
				}
				rgb[y * outW + x] = packed;
			}
		}
		toYuv(rgb, ctx.picture.getData());

		ctx.buffer.clear();
		ByteBuffer annexB = ctx.encoder.encodeFrame(ctx.picture, ctx.buffer).getData();
		return toSample(annexB);
	}

	/** RGB を、YUV(BT.601、16〜235 の範囲)にする。プレーヤーが、ふつうの動画として扱ってくれる形。色の差は、2x2 の平均。 */
	private void toYuv(int[] rgb, byte[][] planes) {
		byte[] yPlane = planes[0];
		byte[] uPlane = planes[1];
		byte[] vPlane = planes[2];
		for (int y = 0; y < outH; y++) {
			for (int x = 0; x < outW; x++) {
				int p = rgb[y * outW + x];
				int r = (p >> 16) & 0xFF;
				int g = (p >> 8) & 0xFF;
				int b = p & 0xFF;
				int luma = 16 + ((66 * r + 129 * g + 25 * b + 128) >> 8);
				yPlane[y * outW + x] = (byte) (luma - 128);
			}
		}
		int cw = outW / 2;
		for (int y = 0; y < outH / 2; y++) {
			for (int x = 0; x < cw; x++) {
				int r = 0;
				int g = 0;
				int b = 0;
				for (int dy = 0; dy < 2; dy++) {
					for (int dx = 0; dx < 2; dx++) {
						int p = rgb[(y * 2 + dy) * outW + x * 2 + dx];
						r += (p >> 16) & 0xFF;
						g += (p >> 8) & 0xFF;
						b += p & 0xFF;
					}
				}
				r >>= 2;
				g >>= 2;
				b >>= 2;
				int u = 128 + ((-38 * r - 74 * g + 112 * b + 128) >> 8);
				int v = 128 + ((112 * r - 94 * g - 18 * b + 128) >> 8);
				uPlane[y * cw + x] = (byte) (Math.max(16, Math.min(240, u)) - 128);
				vPlane[y * cw + x] = (byte) (Math.max(16, Math.min(240, v)) - 128);
			}
		}
	}

	/**
	 * エンコーダーの出力(00 00 00 01 で区切った NAL)を、MP4 の形(各 NAL の前に 4 バイトの長さ)にする。
	 * SPS と PPS は、フレームの中ではなく、ファイルの先頭の表(avcC)に入れるので、取り出しておく。
	 */
	private byte[] toSample(ByteBuffer annexB) {
		byte[] data = new byte[annexB.remaining()];
		annexB.get(data);
		ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 16);
		byte[] sps = null;
		byte[] pps = null;
		int i = 0;
		while (i + 3 < data.length) {
			// NAL の始まり(00 00 01 か 00 00 00 01)を探す
			int start = -1;
			for (int j = i; j + 2 < data.length; j++) {
				if (data[j] == 0 && data[j + 1] == 0 && data[j + 2] == 1) {
					start = j + 3;
					break;
				}
			}
			if (start < 0) {
				break;
			}
			int end = data.length;
			for (int j = start; j + 2 < data.length; j++) {
				if (data[j] == 0 && data[j + 1] == 0 && (data[j + 2] == 1 || (data[j + 2] == 0 && j + 3 < data.length && data[j + 3] == 1))) {
					end = j;
					break;
				}
			}
			while (end > start && data[end - 1] == 0) {
				end--;
			}
			int type = data[start] & 0x1F;
			byte[] nal = java.util.Arrays.copyOfRange(data, start, end);
			if (type == 7) {
				sps = nal;
			} else if (type == 8) {
				pps = nal;
			} else {
				out.write(nal.length >> 24);
				out.write(nal.length >> 16);
				out.write(nal.length >> 8);
				out.write(nal.length);
				out.writeBytes(nal);
			}
			i = end;
		}
		if (sps != null && pps != null) {
			mp4.setParameterSets(sps, pps);
		}
		return out.toByteArray();
	}

	// ---- MP4 に書く(書き込みのスレッド) ----

	private void writeLoop(Minecraft mc) {
		try {
			while (true) {
				Pending p = pending.poll(100, TimeUnit.MILLISECONDS);
				if (p == null) {
					if (!capturing && pending.isEmpty()) {
						return;
					}
					continue;
				}
				byte[] sample;
				try {
					sample = p.sample.get(10, TimeUnit.SECONDS);
				} catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
					LOGGER.warn("1 フレームを飛ばしました", e);
					continue;
				}
				mp4.writeVideo(sample, p.index);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			failed = true;
			LOGGER.error("録画ファイルに書けませんでした", e);
		}
	}

	// ---- 止める ----

	private void finish(Minecraft mc) {
		Thread t = new Thread(() -> {
			Path saved = finishNow();
			mc.execute(() -> {
				if (saved == null) {
					message(mc, Component.literal("[録画] 保存できませんでした(ログを見てください)").withStyle(ChatFormatting.RED));
				} else {
					Component link = Component.literal(saved.toString()).withStyle(ChatFormatting.UNDERLINE)
							.withStyle(s -> s.withClickEvent(new ClickEvent.OpenFile(saved.toAbsolutePath().toFile())));
					message(mc, Component.literal("[録画] 保存しました: ").withStyle(ChatFormatting.GREEN).append(link));
				}
			});
		}, "recmod-finish");
		t.setDaemon(true);
		t.start();
	}

	/** 残りを書き終えて、ファイルを閉じる。成功したら、ファイルの場所を返す。 */
	private Path finishNow() {
		try {
			capturing = false;
			long elapsed = System.nanoTime() - startNanos;
			mixer.stop();
			audioSink.flush();
			writer.join(30_000);
			encoders.shutdown();
			encoders.awaitTermination(10, TimeUnit.SECONDS);
			if (mp4.videoFrames() == 0 || !mp4.hasParameterSets()) {
				LOGGER.error("映像が 1 フレームも取れませんでした(描画への割り込みが効いていない、または画面を読み出せません)");
				mp4.close();
				Files.deleteIfExists(file);
				return null;
			}
			mp4.finish((int) (elapsed * FPS / 1_000_000_000L));
			return file;
		} catch (IOException | InterruptedException | RuntimeException e) {
			LOGGER.error("録画の保存に失敗しました", e);
			return null;
		}
	}

	static void message(Minecraft mc, Component text) {
		mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(text));
	}
}
