package com.povreplay;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * 1 回の接続(ワールドに入ってから出るまで)の記録。ゲームが受け取った通信を、そのまま時刻をつけて書く。
 * 書き込みは、優先度の低い別のスレッドで行い、圧縮(速さ優先)もそこでする。ゲームのスレッドがするのは、バイト列のコピーだけ。
 *
 * ファイルの形: 先頭に "POVR1"、あとは圧縮された記録の並び。1 件 = [種類 1 バイト][時刻 4 バイト(ミリ秒)][長さ 4 バイト][中身]
 */
public final class SessionLog implements Closeable {

	public static final int FRAME = 1;       // 受け取った通信の 1 つ(中身は、パケットのバイト列)
	public static final int PROTOCOL = 2;    // 通信の段階が変わった(中身 1 バイト: 1 = 設定、2 = ゲーム)
	public static final int PLAYER = 3;      // プレイヤーの状態(1 ティックごと)
	public static final int CAMERA = 4;      // 視点(1 フレームごと)
	public static final int MARK = 5;        // 録画の開始(1)・終了(2)の印

	private static final byte[] END = new byte[0];

	private record Entry(int type, int time, byte[] data) {
	}

	private final Path path;
	private final long startNanos = System.nanoTime();
	private final LinkedBlockingQueue<Entry> queue = new LinkedBlockingQueue<>();
	private final Thread writer;
	private volatile boolean failed;

	public SessionLog(Path path) throws IOException {
		this.path = path;
		Files.createDirectories(path.getParent());
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(
				Files.newOutputStream(path), new Deflater(Deflater.BEST_SPEED), 1 << 16), 1 << 16));
		out.writeBytes("POVR1");
		writer = new Thread(() -> {
			try {
				while (true) {
					Entry e = queue.take();
					if (e.data == END) {
						break;
					}
					out.writeByte(e.type);
					out.writeInt(e.time);
					out.writeInt(e.data.length);
					out.write(e.data);
				}
				out.close();
			} catch (IOException | InterruptedException ex) {
				failed = true;
				PovReplay.LOGGER.error("セッションの記録に書けませんでした", ex);
			}
		}, "povreplay-log");
		writer.setDaemon(true);
		writer.setPriority(Thread.MIN_PRIORITY);
		writer.start();
	}

	public Path path() {
		return path;
	}

	/** 記録の始まりからの時間(ミリ秒)。 */
	public int now() {
		return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
	}

	public void add(int type, byte[] data) {
		if (!failed) {
			queue.add(new Entry(type, now(), data));
		}
	}

	@Override
	public void close() {
		queue.add(new Entry(0, 0, END));
		try {
			writer.join(30_000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
