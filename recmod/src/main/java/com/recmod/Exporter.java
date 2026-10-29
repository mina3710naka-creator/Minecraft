package com.recmod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.encode.RateControl;
import org.jcodec.codecs.h264.io.model.SliceType;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rect;
import org.jcodec.common.model.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 録画が終わったあと、一時ファイル(圧縮していない映像と音)から、H.264 と AAC を作って MP4 にする。
 * 時間がかかるので、ゲームの邪魔にならないよう、優先度を下げた別のスレッドで、少なめのスレッド数で行う。
 */
final class Exporter {

	/** 何フレームごとに、映像を区切って(キーフレームを作って)、別々のスレッドで圧縮するか。 */
	private static final int GOP = 15;

	/** H.264 の量子化の数値(画質)。小さいほどきれいで、ファイルが大きい。18 で固定。 */
	private static final int QP = 18;

	private static final Object LOCK = new Object();
	private static boolean busy;

	private Exporter() {
	}

	static boolean isBusy() {
		synchronized (LOCK) {
			return busy;
		}
	}

	static void waitUntilDone() {
		synchronized (LOCK) {
			while (busy) {
				try {
					LOCK.wait();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	/** 音だけの録音: 一時ファイルの音を AAC にして、.m4a に保存する。成功したら、保存したファイルを返す。 */
	static Path runAudio(Path file, Path audioTemp) throws IOException {
		synchronized (LOCK) {
			busy = true;
		}
		int threads = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 2));
		ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
			Thread t = new Thread(r, "recmod-export-worker");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY + 1);
			return t;
		});
		Mp4Writer mp4 = null;
		boolean ok = false;
		try {
			mp4 = new Mp4Writer(file, 0, 0, Recorder.FPS, AudioMixer.RATE, false);
			for (byte[] frame : encodeAudio(pool, audioTemp)) {
				mp4.writeAudio(frame);
			}
			mp4.finish(0);
			ok = true;
			return file;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		} catch (ExecutionException e) {
			throw new IOException(e.getCause());
		} finally {
			pool.shutdownNow();
			if (mp4 != null && !ok) {
				try {
					mp4.close();
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
					// 後始末の失敗は、報告しない
				}
			}
			synchronized (LOCK) {
				busy = false;
				LOCK.notifyAll();
			}
		}
	}

	/** 成功したら、保存したファイルを返す。 */
	static Path run(Minecraft mc, Path file, Path videoTemp, Path audioTemp, int w, int h, int padW, int padH, int[] frameIndex, int totalFrames) throws IOException {
		synchronized (LOCK) {
			busy = true;
		}
		int threads = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 2));
		ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
			Thread t = new Thread(r, "recmod-export-worker");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY + 1);
			return t;
		});
		Mp4Writer mp4 = null;
		boolean ok = false;
		try (FileChannel video = FileChannel.open(videoTemp, StandardOpenOption.READ)) {
			mp4 = new Mp4Writer(file, w, h, Recorder.FPS, AudioMixer.RATE);
			byte[][] audio = encodeAudio(pool, audioTemp);
			int n = frameIndex.length;
			int gops = (n + GOP - 1) / GOP;
			int frameBytes = padW * padH * 3 / 2;
			Exporter exporter = new Exporter();
			Mp4Writer out = mp4;

			List<Future<List<byte[]>>> futures = new ArrayList<>();
			int submitted = 0;
			int audioWritten = 0;
			int lastReported = 0;
			for (int g = 0; g < gops; g++) {
				// 先に、数 GOP ぶんの仕事を出しておく(順番に取り出して書くので、メモリは、その分だけ)
				while (submitted < gops && submitted < g + threads + 1) {
					int first = submitted * GOP;
					int count = Math.min(GOP, n - first);
					futures.add(pool.submit(() -> exporter.encodeGop(video, out, w, h, padW, padH, frameBytes, first, count)));
					submitted++;
				}
				List<byte[]> samples = futures.get(g).get();
				futures.set(g, null);
				int first = g * GOP;
				for (int i = 0; i < samples.size(); i++) {
					mp4.writeVideo(samples.get(i), frameIndex[first + i]);
				}
				// この区切りまでの時間に当たる音も、一緒に書く(映像と音が、ファイルの中で近くに並ぶように)
				int nextFrame = first + samples.size() < n ? frameIndex[first + samples.size()] : totalFrames;
				long untilSample = (long) nextFrame * AudioMixer.RATE / Recorder.FPS;
				while (audioWritten < audio.length && (long) audioWritten * AacEncoder.FRAME < untilSample) {
					mp4.writeAudio(audio[audioWritten++]);
				}
				int percent = (g + 1) * 100 / gops;
				if (percent / 25 > lastReported / 25 && percent < 100) {
					lastReported = percent;
					Recorder.message(mc, Component.literal("[録画] 保存中… " + percent + "%"));
				}
			}
			while (audioWritten < audio.length) {
				mp4.writeAudio(audio[audioWritten++]);
			}
			if (!mp4.hasParameterSets()) {
				throw new IOException("H.264 のヘッダーが取れませんでした");
			}
			mp4.finish(totalFrames);
			ok = true;
			return file;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		} catch (ExecutionException e) {
			throw new IOException(e.getCause());
		} finally {
			pool.shutdownNow();
			if (mp4 != null && !ok) {
				try {
					mp4.close();
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
					// 後始末の失敗は、報告しない
				}
			}
			synchronized (LOCK) {
				busy = false;
				LOCK.notifyAll();
			}
		}
	}

	// ---- 音 ----

	/** 一時ファイルの音(16bit ステレオ)を、AAC の 1 フレームずつにする。フレームごとに独立しているので、並べて計算する。 */
	private static byte[][] encodeAudio(ExecutorService pool, Path audioTemp) throws IOException, InterruptedException, ExecutionException {
		byte[] bytes = Files.readAllBytes(audioTemp);
		short[] pcm = new short[bytes.length / 2];
		ByteBuffer.wrap(bytes, 0, pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
		int frames = pcm.length / (AacEncoder.FRAME * 2);
		byte[][] result = new byte[frames][];
		int chunk = 32;
		List<Future<?>> futures = new ArrayList<>();
		for (int start = 0; start < frames; start += chunk) {
			int a = start;
			int b = Math.min(frames, start + chunk);
			futures.add(pool.submit(() -> {
				for (int f = a; f < b; f++) {
					result[f] = AacEncoder.encode(pcm, f * AacEncoder.FRAME * 2, f > 0);
				}
			}));
		}
		for (Future<?> f : futures) {
			f.get();
		}
		return result;
	}

	// ---- 映像 ----

	private final ThreadLocal<Context> contexts = new ThreadLocal<>();

	/** スレッドごとの作業用の入れ物。 */
	private static final class Context {
		final Picture picture;
		final ByteBuffer buffer;

		/** pw x ph は、16 の倍数に切り上げた、保存してある絵の大きさ。w x h は、動画の大きさ(はみ出す分は、切り取る)。 */
		Context(int w, int h, int pw, int ph) {
			picture = new Picture(pw, ph, new byte[][] { new byte[pw * ph], new byte[pw * ph / 4], new byte[pw * ph / 4] }, null,
					ColorSpace.YUV420J, 0, new Rect(0, 0, w, h));
			buffer = ByteBuffer.allocate(pw * ph * 3);
		}
	}

	/** 一時ファイルの first 番目から count フレームを、先頭だけキーフレームで、残りは直前の絵との差で圧縮する。 */
	private List<byte[]> encodeGop(FileChannel video, Mp4Writer mp4, int w, int h, int padW, int padH, int frameBytes, int first, int count) throws IOException {
		Context ctx = contexts.get();
		if (ctx == null) {
			ctx = new Context(w, h, padW, padH);
			contexts.set(ctx);
		}
		H264Encoder encoder = new H264Encoder(new RateControl() {
			@Override
			public int startPicture(Size sz, int maxSize, SliceType sliceType) {
				return QP;
			}

			@Override
			public int initialQpDelta() {
				return 0;
			}

			@Override
			public int accept(int bits) {
				return 0;
			}
		});
		encoder.setKeyInterval(GOP);
		List<byte[]> samples = new ArrayList<>(count);
		byte[][] planes = ctx.picture.getData();
		for (int i = 0; i < count; i++) {
			long pos = (long) (first + i) * frameBytes;
			for (byte[] plane : planes) {
				ByteBuffer b = ByteBuffer.wrap(plane);
				while (b.hasRemaining()) {
					int read = video.read(b, pos + b.position());
					if (read < 0) {
						throw new IOException("一時ファイルが途中で終わっています");
					}
				}
				pos += plane.length;
			}
			ctx.buffer.clear();
			ByteBuffer annexB = encoder.encodeFrame(ctx.picture, ctx.buffer).getData();
			samples.add(toSample(annexB, mp4));
		}
		return samples;
	}

	/**
	 * エンコーダーの出力(00 00 00 01 で区切った NAL)を、MP4 の形(各 NAL の前に 4 バイトの長さ)にする。
	 * SPS と PPS は、フレームの中ではなく、ファイルの先頭の表(avcC)に入れるので、取り出しておく。
	 */
	private static byte[] toSample(ByteBuffer annexB, Mp4Writer mp4) {
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
}
