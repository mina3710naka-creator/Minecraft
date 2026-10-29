package com.povreplay;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.InflaterInputStream;

/** SessionLog が書いたファイルを、先頭から 1 件ずつ読む。途中で切れていても(ゲームが強制終了したとき)、読めたところまでを返す。 */
public final class SessionReader implements Closeable {

	public record Entry(int type, int time, byte[] data) {
	}

	private final DataInputStream in;
	private Entry peeked;

	public SessionReader(Path path) throws IOException {
		in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(Files.newInputStream(path), new java.util.zip.Inflater(), 1 << 16), 1 << 16));
		byte[] magic = new byte[5];
		in.readFully(magic);
		if (!new String(magic, java.nio.charset.StandardCharsets.ISO_8859_1).equals("POVR1")) {
			throw new IOException("リプレイのファイルではありません: " + path);
		}
	}

	/** 次の 1 件を、取り出さずに見る。終わりなら null。 */
	public Entry peek() {
		if (peeked == null) {
			peeked = read();
		}
		return peeked;
	}

	public Entry next() {
		Entry e = peek();
		peeked = null;
		return e;
	}

	private Entry read() {
		try {
			int type = in.readUnsignedByte();
			int time = in.readInt();
			int length = in.readInt();
			byte[] data = new byte[length];
			in.readFully(data);
			return new Entry(type, time, data);
		} catch (EOFException e) {
			return null;
		} catch (IOException e) {
			PovReplay.LOGGER.warn("記録の途中までしか読めませんでした", e);
			return null;
		}
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
