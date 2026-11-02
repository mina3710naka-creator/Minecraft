package com.povreplay;

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
	static final int GOP = 15;

	/** H.264 の量子化の数値(画質)。小さいほどきれいで、ファイルが大きい。18 で固定。 */
	private static final int QP = 18;

	private static final Object LOCK = new Object();
	private static boolean busy;
	/** 変換の進み具合(0〜1)。画面の表示に使う。 */
	private static volatile float progress;

	static float progress() {
		return progress;
	}

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
			Thread t = new Thread(r, "povreplay-export-worker");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY + 1);
			return t;
		});
		Mp4Writer mp4 = null;
		boolean ok = false;
		try {
			mp4 = new Mp4Writer(file, 0, 0, ReplayEngine.FPS, AudioMixer.RATE, false);
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

	// ---- 音 ----

	/** 一時ファイルの音(16bit ステレオ)を、AAC の 1 フレームずつにする。フレームごとに独立しているので、並べて計算する。 */
	static byte[][] encodeAudio(ExecutorService pool, Path audioTemp) throws IOException, InterruptedException, ExecutionException {
		// 音のファイルは、まるごとメモリに読まずに、必要な所だけ読む(長い録画でも、メモリが足りなくならないように)
		try (FileChannel channel = FileChannel.open(audioTemp, StandardOpenOption.READ)) {
			long size = channel.size();
			int frameBytes = AacEncoder.FRAME * 4;
			int frames = (int) (size / frameBytes);
			byte[][] result = new byte[frames][];
			int chunk = 32;
			List<Future<?>> futures = new ArrayList<>();
			for (int start = 0; start < frames; start += chunk) {
				int a = start;
				int b = Math.min(frames, start + chunk);
				futures.add(pool.submit(() -> {
					// このまとまりと、その直前の 1 フレーム(窓が重なる分)を読む
					int from = Math.max(0, a - 1);
					ByteBuffer bytes = ByteBuffer.allocate((b - from) * frameBytes).order(ByteOrder.LITTLE_ENDIAN);
					while (bytes.hasRemaining()) {
						int read = channel.read(bytes, (long) from * frameBytes + bytes.position());
						if (read < 0) {
							throw new IOException("音の一時ファイルが途中で終わっています");
						}
					}
					bytes.flip();
					short[] pcm = new short[(b - from) * AacEncoder.FRAME * 2];
					bytes.asShortBuffer().get(pcm);
					for (int f = a; f < b; f++) {
						result[f] = AacEncoder.encode(pcm, (f - from) * AacEncoder.FRAME * 2, f > 0);
					}
					return null;
				}));
			}
			for (Future<?> f : futures) {
				f.get();
			}
			return result;
		}
	}

	/** 1 フレームごとに、記録の時刻どおりに進めるための、H.264 のエンコーダー(量子化は QP で固定)。 */
	static H264Encoder newEncoder(int keyInterval) {
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
		encoder.setKeyInterval(keyInterval);
		return encoder;
	}

	/**
	 * エンコーダーの出力(00 00 00 01 で区切った NAL)を、MP4 の形(各 NAL の前に 4 バイトの長さ)にする。
	 * SPS と PPS は、フレームの中ではなく、ファイルの先頭の表(avcC)に入れるので、取り出しておく。
	 */
	static byte[] toSample(ByteBuffer annexB, Mp4Writer mp4) {
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
