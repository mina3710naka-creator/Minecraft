package com.recmod;

import java.awt.image.BufferedImage;
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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

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
 * 読み出した絵は、別のスレッドで縮めて JPEG にし、順番どおりに AVI へ書く。
 * 音: AudioMixer がゲームの音を混ぜて、AVI へ書く。
 * 停止すると、ダウンロードフォルダーに保存する。
 */
public final class Recorder {

	public static final Logger LOGGER = LoggerFactory.getLogger("recmod");

	private static final int FPS = 30;
	/** 映像の幅の上限(これより大きい画面は、縦横の比を保って縮める)。ファイルが大きくなりすぎないように。 */
	private static final int MAX_WIDTH = 1280;
	private static final float JPEG_QUALITY = 0.75F;
	/** 同時に持てる、読み出した絵の数。エンコードが追いつかないときは、絵を飛ばす(同じ絵を続けて使う)。 */
	private static final int MAX_IN_FLIGHT = 6;

	private static volatile Recorder active;

	private final Path file;
	private final AviWriter avi;
	private final AudioMixer mixer;
	private final long startNanos = System.nanoTime();
	private final int outW;
	private final int outH;

	private final ExecutorService encoders = Executors.newFixedThreadPool(2, r -> {
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

	private record Pending(int index, CompletableFuture<byte[]> jpeg) {
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
		int h = Math.max(2, Math.round(height * (w / (float) width)));
		this.outW = w & ~1;
		this.outH = h & ~1;
		this.avi = new AviWriter(file, outW, outH, FPS, AudioMixer.RATE);
		this.mixer = new AudioMixer(startNanos, avi::writeAudioQuietly);
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
		String name = "minecraft-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".avi";
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
		CompletableFuture<byte[]> jpeg = new CompletableFuture<>();
		pending.add(new Pending(frame, jpeg));

		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Recording buffer", 9, size);
		RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
			try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
				ByteBuffer data = view.data();
				data.get(0, raw, 0, size);
			} catch (RuntimeException e) {
				jpeg.completeExceptionally(e);
				release(raw);
				buffer.close();
				return;
			}
			buffer.close();
			encoders.execute(() -> {
				try {
					jpeg.complete(encode(raw, w, h));
				} catch (Throwable t) {
					jpeg.completeExceptionally(t);
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

	// ---- 縮めて JPEG にする(エンコードのスレッド) ----

	private byte[] encode(byte[] raw, int w, int h) throws IOException {
		int[] rgb = new int[outW * outH];
		// 読み出した絵は、上下が逆で、1 画素が R,G,B,A の順。縮めながら、上下を直す
		for (int y = 0; y < outH; y++) {
			float sy = (y + 0.5F) * h / outH - 0.5F;
			int y0 = Math.max(0, Math.min(h - 1, (int) Math.floor(sy)));
			int y1 = Math.min(h - 1, y0 + 1);
			int fy = Math.round((sy - (int) Math.floor(sy)) * 256F);
			fy = Math.max(0, Math.min(256, fy));
			int row0 = (h - 1 - y0) * w * 4;
			int row1 = (h - 1 - y1) * w * 4;
			for (int x = 0; x < outW; x++) {
				float sx = (x + 0.5F) * w / outW - 0.5F;
				int x0 = Math.max(0, Math.min(w - 1, (int) Math.floor(sx)));
				int x1 = Math.min(w - 1, x0 + 1);
				int fx = Math.max(0, Math.min(256, Math.round((sx - (int) Math.floor(sx)) * 256F)));
				int r = 0;
				int g = 0;
				int b = 0;
				for (int c = 0; c < 3; c++) {
					int a = raw[row0 + x0 * 4 + c] & 0xFF;
					int bb = raw[row0 + x1 * 4 + c] & 0xFF;
					int cc = raw[row1 + x0 * 4 + c] & 0xFF;
					int d = raw[row1 + x1 * 4 + c] & 0xFF;
					int top = a * (256 - fx) + bb * fx;
					int bottom = cc * (256 - fx) + d * fx;
					int v = (top * (256 - fy) + bottom * fy) >> 16;
					if (c == 0) {
						r = v;
					} else if (c == 1) {
						g = v;
					} else {
						b = v;
					}
				}
				rgb[y * outW + x] = (r << 16) | (g << 8) | b;
			}
		}
		BufferedImage image = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
		image.getRaster().setDataElements(0, 0, outW, outH, rgb);

		ImageWriter jpegWriter = ImageIO.getImageWritersByFormatName("jpeg").next();
		ImageWriteParam param = jpegWriter.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(JPEG_QUALITY);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
		try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
			jpegWriter.setOutput(out);
			jpegWriter.write(null, new IIOImage(image, null, null), param);
		} finally {
			jpegWriter.dispose();
		}
		return bytes.toByteArray();
	}

	// ---- AVI に書く(書き込みのスレッド) ----

	private byte[] lastJpeg;
	private int lastIndex = -1;

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
				byte[] jpeg;
				try {
					jpeg = p.jpeg.get(10, TimeUnit.SECONDS);
				} catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
					LOGGER.warn("1 フレームを飛ばしました", e);
					continue;
				}
				writeFrame(p.index, jpeg);
				if (avi.nearLimit()) {
					// AVI の大きさの上限(4GB)に近づいた。ここで保存して、止める
					mc.execute(() -> {
						if (active == this) {
							message(mc, Component.literal("[録画] ファイルが大きくなったので、ここで止めます").withStyle(ChatFormatting.YELLOW));
							stop(mc);
						}
					});
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			failed = true;
			LOGGER.error("録画ファイルに書けませんでした", e);
		}
	}

	/** index 番目の絵として書く。前の絵との間があいていたら、前の絵を繰り返して、時間をそろえる。 */
	private void writeFrame(int index, byte[] jpeg) throws IOException {
		byte[] fill = lastJpeg != null ? lastJpeg : jpeg;
		for (int i = lastIndex + 1; i < index; i++) {
			avi.writeVideo(fill, fill.length);
		}
		avi.writeVideo(jpeg, jpeg.length);
		lastJpeg = jpeg;
		lastIndex = index;
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
			writer.join(30_000);
			encoders.shutdown();
			encoders.awaitTermination(10, TimeUnit.SECONDS);
			// 最後の絵を、録画の長さまで繰り返す
			int total = (int) (elapsed * FPS / 1_000_000_000L);
			if (lastJpeg != null) {
				for (int i = lastIndex + 1; i < total; i++) {
					avi.writeVideo(lastJpeg, lastJpeg.length);
				}
			}
			avi.close();
			if (avi.videoFrames() == 0) {
				LOGGER.error("映像が 1 フレームも取れませんでした(描画への割り込みが効いていない、または画面を読み出せません)");
				Files.deleteIfExists(file);
				return null;
			}
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
