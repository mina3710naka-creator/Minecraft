package com.recmod;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 映像(H.264)と音声(AAC-LC)を入れた MP4 ファイルを書く。
 * 中身(mdat)を先に書いていって、どこに何があるかの表(moov)は、閉じるときに最後に付ける。
 * 映像と音声は、別のスレッドから書かれる(どちらの write も同期している)。
 */
final class Mp4Writer implements Closeable {

	/** 映像の時間の単位: 1 フレーム = VIDEO_UNIT。 */
	private static final int VIDEO_UNIT = 1000;
	private static final int AAC_FRAME = 1024;

	private final Path path;
	private final OutputStream out;
	private final int width;
	private final int height;
	private final int fps;
	private final int audioRate;

	private long position;
	private long mdatStart;

	// 映像: 1 サンプル(1 フレーム)ごとの、大きさ・位置・長さ(フレーム数)
	private int videoCount;
	private int[] videoSize = new int[1024];
	private long[] videoOffset = new long[1024];
	private int[] videoDuration = new int[1024];
	private int lastVideoIndex = -1;

	// 音声
	private int audioCount;
	private int[] audioSize = new int[2048];
	private long[] audioOffset = new long[2048];

	private byte[] sps;
	private byte[] pps;

	private final boolean hasVideo;

	Mp4Writer(Path path, int width, int height, int fps, int audioRate) throws IOException {
		this(path, width, height, fps, audioRate, true);
	}

	/** hasVideo が false なら、音だけのファイル(.m4a)にする。 */
	Mp4Writer(Path path, int width, int height, int fps, int audioRate, boolean hasVideo) throws IOException {
		this.hasVideo = hasVideo;
		this.path = path;
		this.width = width;
		this.height = height;
		this.fps = fps;
		this.audioRate = audioRate;
		this.out = new BufferedOutputStream(new FileOutputStream(path.toFile()), 1 << 20);
		byte[] ftyp = hasVideo
				? box("ftyp", cat(ascii("isom"), u32(512), ascii("isom"), ascii("iso2"), ascii("avc1"), ascii("mp41")))
				: box("ftyp", cat(ascii("M4A "), u32(512), ascii("M4A "), ascii("mp42"), ascii("isom")));
		out.write(ftyp);
		position = ftyp.length;
		mdatStart = position;
		out.write(u32(1));                     // 大きさが 64 ビット(あとで書き直す)
		out.write(ascii("mdat"));
		out.write(new byte[8]);
		position += 16;
	}

	synchronized void setParameterSets(byte[] sps, byte[] pps) {
		if (this.sps == null) {
			// JCodec は、レベルをいつも 4.0 と書くが、1080p を超える大きさでも、デコーダーが断らないよう、5.1 にしておく
			sps = sps.clone();
			sps[3] = 51;
			this.sps = sps;
			this.pps = pps;
		}
	}

	synchronized boolean hasParameterSets() {
		return sps != null;
	}

	/**
	 * 映像の 1 フレーム(長さ付きの NAL を並べたもの)。index は、録画の始まりからのフレーム番号(fps 単位)。
	 * 前のフレームの長さは、これと前の index の差になる。
	 */
	synchronized void writeVideo(byte[] sample, int index) throws IOException {
		if (videoCount == videoSize.length) {
			videoSize = Arrays.copyOf(videoSize, videoCount * 2);
			videoOffset = Arrays.copyOf(videoOffset, videoCount * 2);
			videoDuration = Arrays.copyOf(videoDuration, videoCount * 2);
		}
		if (videoCount > 0) {
			videoDuration[videoCount - 1] = Math.max(1, index - lastVideoIndex);
		}
		out.write(sample);
		videoSize[videoCount] = sample.length;
		videoOffset[videoCount] = position;
		videoDuration[videoCount] = 1;
		position += sample.length;
		videoCount++;
		lastVideoIndex = index;
	}

	/** AAC の 1 フレーム(1024 サンプルぶん)。 */
	synchronized void writeAudio(byte[] frame) throws IOException {
		if (audioCount == audioSize.length) {
			audioSize = Arrays.copyOf(audioSize, audioCount * 2);
			audioOffset = Arrays.copyOf(audioOffset, audioCount * 2);
		}
		out.write(frame);
		audioSize[audioCount] = frame.length;
		audioOffset[audioCount] = position;
		position += frame.length;
		audioCount++;
	}

	/** 音のスレッド用: 書けなくても、例外を投げない(映像の側が、エラーを報告する)。 */
	void writeAudioQuietly(byte[] frame) {
		try {
			writeAudio(frame);
		} catch (IOException e) {
			Recorder.LOGGER.error("録画ファイルに音を書けませんでした", e);
		}
	}

	synchronized int videoFrames() {
		return videoCount;
	}

	/** 最後のフレームを、録画の長さ(totalFrames)まで伸ばして、表をつけて閉じる。 */
	synchronized void finish(int totalFrames) throws IOException {
		if (videoCount > 0) {
			videoDuration[videoCount - 1] = Math.max(1, totalFrames - lastVideoIndex);
		}
		long mdatEnd = position;
		byte[] moov = moov();
		out.write(moov);
		out.close();
		try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw")) {
			f.seek(mdatStart + 8);
			f.write(u64(mdatEnd - mdatStart));
		}
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	// ---- moov(どこに何があるかの表) ----

	private byte[] moov() {
		long videoUnits = 0;
		for (int i = 0; i < videoCount; i++) {
			videoUnits += videoDuration[i];
		}
		long videoMs = videoUnits * 1000L / fps;
		long audioMs = (long) audioCount * AAC_FRAME * 1000L / audioRate;
		long movieMs = Math.max(videoMs, audioMs);

		byte[] mvhd = box("mvhd", cat(u32(0), u32(0), u32(0), u32(1000), u32(movieMs), u32(0x00010000), u16(0x0100), new byte[10],
				matrix(), new byte[24], u32(hasVideo ? 3 : 2)));
		if (!hasVideo) {
			return box("moov", cat(mvhd, audioTrack(1, audioMs, (long) audioCount * AAC_FRAME)));
		}
		byte[] video = videoTrack(1, videoMs, videoUnits * VIDEO_UNIT);
		byte[] audio = audioTrack(2, audioMs, (long) audioCount * AAC_FRAME);
		return box("moov", cat(mvhd, video, audio));
	}

	private byte[] videoTrack(int id, long ms, long units) {
		byte[] tkhd = box("tkhd", cat(u32(3), u32(0), u32(0), u32(id), u32(0), u32(ms), new byte[8], u16(0), u16(0), u16(0), u16(0),
				matrix(), u32((long) width << 16), u32((long) height << 16)));
		byte[] mdhd = box("mdhd", cat(u32(0), u32(0), u32(0), u32((long) fps * VIDEO_UNIT), u32(units), u16(0x55C4), u16(0)));
		byte[] hdlr = box("hdlr", cat(u32(0), u32(0), ascii("vide"), new byte[12], ascii("VideoHandler\0")));
		byte[] vmhd = box("vmhd", cat(u32(1), u16(0), new byte[6]));

		byte[] avcC = box("avcC", cat(new byte[] { 1, sps[1], sps[2], sps[3], (byte) 0xFF, (byte) 0xE1 }, u16(sps.length), sps,
				new byte[] { 1 }, u16(pps.length), pps));
		byte[] avc1 = box("avc1", cat(new byte[6], u16(1), u16(0), u16(0), new byte[12], u16(width), u16(height), u32(0x00480000),
				u32(0x00480000), u32(0), u16(1), new byte[32], u16(0x18), u16(0xFFFF), avcC, colr()));
		byte[] stsd = box("stsd", cat(u32(0), u32(1), avc1));

		// 長さが同じフレームは、まとめて 1 行にする
		ByteArrayOutputStream runs = new ByteArrayOutputStream();
		int runCount = 0;
		for (int i = 0; i < videoCount; ) {
			int j = i;
			while (j < videoCount && videoDuration[j] == videoDuration[i]) {
				j++;
			}
			write(runs, u32(j - i), u32((long) videoDuration[i] * VIDEO_UNIT));
			runCount++;
			i = j;
		}
		byte[] stts = box("stts", cat(u32(0), u32(runCount), runs.toByteArray()));
		byte[] stbl = box("stbl", cat(stsd, stts, sampleTables(videoCount, videoSize, videoOffset)));
		return track(tkhd, mdhd, hdlr, box("minf", cat(vmhd, dinf(), stbl)));
	}

	/** 色の表し方(原色・伝達特性・行列と、範囲)を、設定どおりに書く。プレーヤーが色を取り違えないように。 */
	private static byte[] colr() {
		int code = RecConfig.bt709 ? 1 : 6;   // 1 = BT.709、6 = BT.601(SMPTE 170M)
		return box("colr", cat(ascii("nclx"), u16(code), u16(code), u16(code), new byte[] { (byte) (RecConfig.fullRange ? 0x80 : 0) }));
	}

	private byte[] audioTrack(int id, long ms, long units) {
		byte[] tkhd = box("tkhd", cat(u32(3), u32(0), u32(0), u32(id), u32(0), u32(ms), new byte[8], u16(0), u16(0), u16(0x0100),
				u16(0), matrix(), u32(0), u32(0)));
		byte[] mdhd = box("mdhd", cat(u32(0), u32(0), u32(0), u32(audioRate), u32(units), u16(0x55C4), u16(0)));
		byte[] hdlr = box("hdlr", cat(u32(0), u32(0), ascii("soun"), new byte[12], ascii("SoundHandler\0")));
		byte[] smhd = box("smhd", cat(u32(0), u16(0), u16(0)));

		// AudioSpecificConfig: AAC-LC(2)・44.1kHz(4)・ステレオ(2)
		byte[] asc = { 0x12, 0x10 };
		byte[] decSpecific = cat(new byte[] { 0x05, (byte) asc.length }, asc);
		byte[] decConfig = cat(new byte[] { 0x04, (byte) (13 + decSpecific.length), 0x40, 0x15 }, new byte[3], u32(0), u32(0), decSpecific);
		byte[] esDesc = cat(new byte[] { 0x03, (byte) (3 + decConfig.length + 3), 0, 0, 0 }, decConfig, new byte[] { 0x06, 0x01, 0x02 });
		byte[] esds = box("esds", cat(u32(0), esDesc));
		byte[] mp4a = box("mp4a", cat(new byte[6], u16(1), new byte[8], u16(2), u16(16), u16(0), u16(0), u32((long) audioRate << 16), esds));
		byte[] stsd = box("stsd", cat(u32(0), u32(1), mp4a));
		byte[] stts = box("stts", cat(u32(0), u32(1), u32(audioCount), u32(AAC_FRAME)));
		byte[] stbl = box("stbl", cat(stsd, stts, sampleTables(audioCount, audioSize, audioOffset)));
		return track(tkhd, mdhd, hdlr, box("minf", cat(smhd, dinf(), stbl)));
	}

	private static byte[] track(byte[] tkhd, byte[] mdhd, byte[] hdlr, byte[] minf) {
		return box("trak", cat(tkhd, box("mdia", cat(mdhd, hdlr, minf))));
	}

	private static byte[] dinf() {
		byte[] url = box("url ", u32(1));
		return box("dinf", box("dref", cat(u32(0), u32(1), url)));
	}

	/** stsc(1 チャンク = 1 サンプル)・stsz・co64。 */
	private static byte[] sampleTables(int count, int[] sizes, long[] offsets) {
		byte[] stsc = count == 0 ? box("stsc", cat(u32(0), u32(0))) : box("stsc", cat(u32(0), u32(1), u32(1), u32(1), u32(1)));
		ByteArrayOutputStream sz = new ByteArrayOutputStream();
		ByteArrayOutputStream co = new ByteArrayOutputStream();
		for (int i = 0; i < count; i++) {
			write(sz, u32(sizes[i]));
			write(co, u64(offsets[i]));
		}
		byte[] stsz = box("stsz", cat(u32(0), u32(0), u32(count), sz.toByteArray()));
		byte[] co64 = box("co64", cat(u32(0), u32(count), co.toByteArray()));
		return cat(stsc, stsz, co64);
	}

	private static byte[] matrix() {
		return cat(u32(0x00010000), u32(0), u32(0), u32(0), u32(0x00010000), u32(0), u32(0), u32(0), u32(0x40000000));
	}

	// ---- バイト列を作る道具 ----

	private static byte[] box(String type, byte[]... parts) {
		byte[] body = cat(parts);
		return cat(u32(body.length + 8), ascii(type), body);
	}

	private static byte[] cat(byte[]... parts) {
		int n = 0;
		for (byte[] p : parts) {
			n += p.length;
		}
		byte[] r = new byte[n];
		int o = 0;
		for (byte[] p : parts) {
			System.arraycopy(p, 0, r, o, p.length);
			o += p.length;
		}
		return r;
	}

	private static void write(ByteArrayOutputStream o, byte[]... parts) {
		for (byte[] p : parts) {
			o.writeBytes(p);
		}
	}

	private static byte[] ascii(String s) {
		return s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
	}

	private static byte[] u16(int v) {
		return new byte[] { (byte) (v >> 8), (byte) v };
	}

	private static byte[] u32(long v) {
		return new byte[] { (byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v };
	}

	private static byte[] u64(long v) {
		return cat(u32(v >>> 32), u32(v & 0xFFFFFFFFL));
	}
}
