package com.recmod;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 画質と色の設定(ゲームのフォルダーの config/recmod.properties)。録画を始めるたびに読み直すので、ゲームを閉じずに、書き換えて試せる。
 *  width / height : 動画の大きさ(標準 1920x1080。縮小も拡大もしない)。
 *  matrix : 色の式。bt709(HD の標準)か bt601。
 *  range  : 明るさの範囲。limited(16〜235、標準)か full(0〜255)。
 */
final class RecConfig {

	private static final String DEFAULT_TEXT = """
			# recmod の設定。録画を始めるたびに読み直します。
			#
			# 動画の大きさ。画面の大きさによらず、この大きさで固定です(縮小も拡大もしません)。
			# 画面が大きければ真ん中を切り取り、小さければ真ん中に置いて、まわりを黒にします。
			# 画質(量子化の数値)は 18 で固定です。
			width=1920
			height=1080
			#
			# 色の式: bt709(HD の標準。たいていはこれ)か bt601。色が違って見えるときに試してください。
			matrix=bt709
			#
			# 明るさの範囲: limited(16〜235、標準)か full(0〜255)。黒が浮く・白が飛ぶときに試してください。
			range=limited
			""";

	/** 動画の大きさ。 */
	static volatile int width = 1920;
	static volatile int height = 1080;
	/** 色の式が BT.709 か(false なら BT.601)。 */
	static volatile boolean bt709 = true;
	/** 明るさの範囲が full(0〜255)か(false なら 16〜235)。 */
	static volatile boolean fullRange;

	private RecConfig() {
	}

	static void load(Path gameDirectory) {
		Path file = gameDirectory.resolve("config").resolve("recmod.properties");
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, DEFAULT_TEXT, StandardCharsets.UTF_8);
			}
			// 前の版が書いた標準の幅(1940)は、1920 に書き換える
			String text = Files.readString(file, StandardCharsets.UTF_8);
			if (text.contains("width=1940")) {
				Files.writeString(file, text.replace("width=1940", "width=1920"), StandardCharsets.UTF_8);
			}
			Properties p = new Properties();
			try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				p.load(in);
			}
			width = Math.max(64, Math.min(7680, Integer.parseInt(p.getProperty("width", "1920").trim()))) & ~1;
			height = Math.max(64, Math.min(4320, Integer.parseInt(p.getProperty("height", "1080").trim()))) & ~1;
			bt709 = !p.getProperty("matrix", "bt709").trim().equalsIgnoreCase("bt601");
			fullRange = p.getProperty("range", "limited").trim().equalsIgnoreCase("full");
		} catch (IOException | RuntimeException e) {
			Recorder.LOGGER.warn("設定ファイルを読めなかったので、標準の設定を使います: " + file, e);
			width = 1920;
			height = 1080;
			bt709 = true;
			fullRange = false;
		}
		Recorder.LOGGER.info("録画の設定: " + width + "x" + height + ", qp=18, matrix=" + (bt709 ? "bt709" : "bt601") + ", range=" + (fullRange ? "full" : "limited"));
	}

	/** {Y の R, G, B 係数, U の R, G, B 係数, V の R, G, B 係数}(256 を 1 とする)。 */
	static int[] coefficients() {
		if (bt709) {
			return fullRange
					? new int[] { 54, 183, 19, -29, -99, 128, 128, -116, -12 }
					: new int[] { 47, 157, 16, -26, -86, 112, 112, -102, -10 };
		}
		return fullRange
				? new int[] { 77, 150, 29, -43, -85, 128, 128, -107, -21 }
				: new int[] { 66, 129, 25, -38, -74, 112, 112, -94, -18 };
	}
}
