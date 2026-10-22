package com.lampvideo;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * 動画ファイル(.lvf)の読み込み。Minecraft には依存しない。
 *
 * 形式(すべてビッグエンディアン、全体が gzip):
 * <pre>
 *   "LVF1" / u16 幅 / u16 高さ / u8 1フレームを表示するtick数 / i32 フレーム数
 *   フレームごと: [点灯する長方形] [消灯する長方形]
 *     i32 個数 / 個数 × (u16 x1, u16 x2, u16 y0, u16 y1)   ※画像座標(yは上が0)、両端を含む
 * </pre>
 * 各フレームは、前のフレームからの差分だけを持つ。
 */
public final class LvfVideo implements Closeable {

	/** 画素を1つずつ受け取る。x は左から、y は上から数える。 */
	public interface Sink {
		void set(int x, int y, boolean lit);
	}

	public final int width;
	public final int height;
	public final int hold;
	public final int frames;

	private final DataInputStream in;
	private int framesRead;

	private LvfVideo(DataInputStream in) throws IOException {
		this.in = in;
		byte[] magic = new byte[4];
		in.readFully(magic);
		if (magic[0] != 'L' || magic[1] != 'V' || magic[2] != 'F' || magic[3] != '1') {
			throw new IOException("lvf ファイルではありません");
		}
		this.width = in.readUnsignedShort();
		this.height = in.readUnsignedShort();
		this.hold = in.readUnsignedByte();
		this.frames = in.readInt();
		if (width <= 0 || height <= 0 || hold <= 0 || frames < 0) {
			throw new IOException("lvf ファイルのヘッダーが不正です");
		}
	}

	public static LvfVideo open(Path path) throws IOException {
		DataInputStream in = new DataInputStream(new BufferedInputStream(
				new GZIPInputStream(Files.newInputStream(path), 1 << 16), 1 << 16));
		try {
			return new LvfVideo(in);
		} catch (IOException | RuntimeException e) {
			in.close();
			throw e;
		}
	}

	/** 次のフレームの差分を sink に流す。最後まで読み終えていれば false。 */
	public boolean readFrame(Sink sink) throws IOException {
		if (framesRead >= frames) {
			return false;
		}
		for (int group = 0; group < 2; group++) {
			boolean lit = group == 0;
			int count = in.readInt();
			if (count < 0 || count > width * height) {
				throw new IOException("lvf ファイルが壊れています");
			}
			for (int i = 0; i < count; i++) {
				int x1 = in.readUnsignedShort();
				int x2 = in.readUnsignedShort();
				int y0 = in.readUnsignedShort();
				int y1 = in.readUnsignedShort();
				if (x2 >= width || y1 >= height || x1 > x2 || y0 > y1) {
					throw new IOException("lvf ファイルが壊れています");
				}
				for (int y = y0; y <= y1; y++) {
					for (int x = x1; x <= x2; x++) {
						sink.set(x, y, lit);
					}
				}
			}
		}
		framesRead++;
		return true;
	}

	public int framesRead() {
		return framesRead;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
