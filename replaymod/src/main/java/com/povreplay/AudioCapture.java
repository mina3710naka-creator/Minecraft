package com.povreplay;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 録画中のゲームの音を、AudioMixer(ゲームの音の命令を写し取って混ぜ直す)で混ぜて、圧縮せずに、一時ファイルに書く。
 * 音は、聞こえたとおりのものを、そのまま使う(リプレイの再生では、音を作り直さない)。
 */
public final class AudioCapture {

	private final Path file;
	private final OutputStream out;
	private final AudioMixer mixer;
	private volatile boolean failed;

	public AudioCapture(Path file, long startNanos) throws IOException {
		this.file = file;
		this.out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16);
		this.mixer = new AudioMixer(startNanos, this::write);
		this.mixer.start();
	}

	private void write(byte[] pcm, int length) {
		if (failed) {
			return;
		}
		try {
			out.write(pcm, 0, length);
		} catch (IOException e) {
			failed = true;
			PovReplay.LOGGER.error("録音の一時ファイルに書けませんでした", e);
		}
	}

	public Path file() {
		return file;
	}

	public void stop() {
		mixer.stop();
		try {
			out.close();
		} catch (IOException e) {
			PovReplay.LOGGER.error("録音の一時ファイルを閉じられませんでした", e);
		}
	}
}
