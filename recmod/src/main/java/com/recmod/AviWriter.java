package com.recmod;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 映像(MJPEG)と音声(16bit PCM ステレオ)を入れた AVI ファイルを書く。
 * 長さは書き終わるまで分からないので、ヘッダーの数字は仮に 0 で書いておいて、閉じるときに書き直す。
 * 映像と音声は、別のスレッドから書かれる(どちらの write も同期している)。
 */
final class AviWriter implements Closeable {

	private static final int AVIH_FRAMES_OFFSET = 12 + 12 + 8 + 16;     // RIFF(12) + LIST hdrl(12) + 'avih' ヘッダー(8) + 16 バイト目

	private final Path path;
	private final OutputStream out;
	private final int width;
	private final int height;
	private final int fps;
	private final int audioRate;

	private long position;
	private long moviListOffset;
	private long moviStart;
	private long videoStrhLengthOffset;
	private long audioStrhLengthOffset;

	private int videoFrames;
	private long audioFrames;
	private int maxChunk;
	private final List<long[]> index = new ArrayList<>();   // {種類(0 = 映像, 1 = 音声), 位置, 大きさ}

	AviWriter(Path path, int width, int height, int fps, int audioRate) throws IOException {
		this.path = path;
		this.width = width;
		this.height = height;
		this.fps = fps;
		this.audioRate = audioRate;
		this.out = new BufferedOutputStream(new FileOutputStream(path.toFile()), 1 << 20);
		writeHeader();
	}

	private void writeHeader() throws IOException {
		ByteBuffer b = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
		fourcc(b, "RIFF");
		b.putInt(0);                       // 全体の大きさ(あとで)
		fourcc(b, "AVI ");

		fourcc(b, "LIST");
		b.putInt(4 + 8 + 56 + (12 + 8 + 56 + 8 + 40) + (12 + 8 + 56 + 8 + 16));
		fourcc(b, "hdrl");

		fourcc(b, "avih");
		b.putInt(56);
		b.putInt(1_000_000 / fps);         // 1 フレームのマイクロ秒
		b.putInt(0);                       // 最大のデータ速度
		b.putInt(0);                       // 余白
		b.putInt(0x10 | 0x100);            // AVIF_HASINDEX | AVIF_ISINTERLEAVED
		b.putInt(0);                       // 総フレーム数(あとで)
		b.putInt(0);                       // 初期フレーム
		b.putInt(2);                       // ストリームの数
		b.putInt(1 << 20);                 // 推奨バッファの大きさ
		b.putInt(width);
		b.putInt(height);
		b.putInt(0).putInt(0).putInt(0).putInt(0);

		// 映像ストリーム
		fourcc(b, "LIST");
		b.putInt(4 + 8 + 56 + 8 + 40);
		fourcc(b, "strl");
		fourcc(b, "strh");
		b.putInt(56);
		fourcc(b, "vids");
		fourcc(b, "MJPG");
		b.putInt(0);                       // フラグ
		b.putShort((short) 0);             // 優先度
		b.putShort((short) 0);             // 言語
		b.putInt(0);                       // 初期フレーム
		b.putInt(1);                       // scale
		b.putInt(fps);                     // rate
		b.putInt(0);                       // 開始
		videoStrhLengthOffset = b.position();
		b.putInt(0);                       // 長さ(フレーム数。あとで)
		b.putInt(1 << 20);
		b.putInt(-1);                      // 画質
		b.putInt(0);                       // サンプルの大きさ
		b.putShort((short) 0).putShort((short) 0).putShort((short) width).putShort((short) height);
		fourcc(b, "strf");
		b.putInt(40);
		b.putInt(40);                      // BITMAPINFOHEADER
		b.putInt(width);
		b.putInt(height);
		b.putShort((short) 1);
		b.putShort((short) 24);
		fourcc(b, "MJPG");
		b.putInt(width * height * 3);
		b.putInt(0).putInt(0).putInt(0).putInt(0);

		// 音声ストリーム
		fourcc(b, "LIST");
		b.putInt(4 + 8 + 56 + 8 + 16);
		fourcc(b, "strl");
		fourcc(b, "strh");
		b.putInt(56);
		fourcc(b, "auds");
		b.putInt(0);
		b.putInt(0);
		b.putShort((short) 0);
		b.putShort((short) 0);
		b.putInt(0);
		b.putInt(1);                       // scale
		b.putInt(audioRate);               // rate(1 秒あたりのサンプル数)
		b.putInt(0);
		audioStrhLengthOffset = b.position();
		b.putInt(0);                       // 長さ(サンプル数。あとで)
		b.putInt(audioRate * 4);
		b.putInt(-1);
		b.putInt(4);                       // サンプルの大きさ(2 チャンネル × 2 バイト)
		b.putShort((short) 0).putShort((short) 0).putShort((short) 0).putShort((short) 0);
		fourcc(b, "strf");
		b.putInt(16);
		b.putShort((short) 1);             // PCM
		b.putShort((short) 2);
		b.putInt(audioRate);
		b.putInt(audioRate * 4);
		b.putShort((short) 4);
		b.putShort((short) 16);

		fourcc(b, "LIST");
		moviListOffset = b.position();
		b.putInt(0);                       // 大きさ(あとで)
		fourcc(b, "movi");

		b.flip();
		byte[] bytes = new byte[b.remaining()];
		b.get(bytes);
		out.write(bytes);
		position = bytes.length;
		moviStart = position - 4;          // 'movi' の位置(インデックスの位置は、ここからの相対)
	}

	private static void fourcc(ByteBuffer b, String s) {
		for (int i = 0; i < 4; i++) {
			b.put((byte) s.charAt(i));
		}
	}

	/** JPEG 1 枚を、1 フレームとして書く。 */
	synchronized void writeVideo(byte[] jpeg, int length) throws IOException {
		writeChunk("00dc", jpeg, length, 0);
		videoFrames++;
	}

	/** 16bit ステレオ(リトルエンディアン)の音声を書く。length はバイト数。 */
	synchronized void writeAudio(byte[] pcm, int length) throws IOException {
		writeChunk("01wb", pcm, length, 1);
		audioFrames += length / 4;
	}

	/** 音のスレッド用: 書けなくても、例外を投げない(映像の側が、エラーを報告する)。 */
	void writeAudioQuietly(byte[] pcm, int length) {
		try {
			writeAudio(pcm, length);
		} catch (IOException e) {
			Recorder.LOGGER.error("録画ファイルに音を書けませんでした", e);
		}
	}

	private void writeChunk(String id, byte[] data, int length, int kind) throws IOException {
		byte[] header = new byte[8];
		for (int i = 0; i < 4; i++) {
			header[i] = (byte) id.charAt(i);
		}
		header[4] = (byte) length;
		header[5] = (byte) (length >> 8);
		header[6] = (byte) (length >> 16);
		header[7] = (byte) (length >> 24);
		out.write(header);
		out.write(data, 0, length);
		index.add(new long[] { kind, position - moviStart, length });
		position += 8 + length;
		maxChunk = Math.max(maxChunk, length);
		if ((length & 1) != 0) {
			out.write(0);
			position++;
		}
	}

	@Override
	public synchronized void close() throws IOException {
		long moviEnd = position;
		ByteBuffer idx = ByteBuffer.allocate(8 + index.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
		fourcc(idx, "idx1");
		idx.putInt(index.size() * 16);
		for (long[] e : index) {
			fourcc(idx, e[0] == 0 ? "00dc" : "01wb");
			idx.putInt(e[0] == 0 ? 0x10 : 0);          // AVIIF_KEYFRAME(JPEG は、どのフレームも単独で見られる)
			idx.putInt((int) e[1]);
			idx.putInt((int) e[2]);
		}
		out.write(idx.array());
		position += idx.capacity();
		out.close();

		try (RandomAccessFile f = new RandomAccessFile(path.toFile(), "rw")) {
			patch(f, 4, (int) (position - 8));
			patch(f, moviListOffset, (int) (moviEnd - moviListOffset - 4));
			patch(f, AVIH_FRAMES_OFFSET, videoFrames);
			patch(f, videoStrhLengthOffset, videoFrames);
			patch(f, audioStrhLengthOffset, (int) audioFrames);
		}
	}

	private static void patch(RandomAccessFile f, long offset, int value) throws IOException {
		f.seek(offset);
		f.write(new byte[] { (byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24) });
	}

	int videoFrames() {
		return videoFrames;
	}

	/** これ以上大きくすると、AVI(4GB まで)に収まらなくなる、手前の大きさ。 */
	synchronized boolean nearLimit() {
		return position > 3_500_000_000L;
	}
}
