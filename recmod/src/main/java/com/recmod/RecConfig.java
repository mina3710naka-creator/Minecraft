package com.recmod;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 画質と色の設定(ゲームのフォルダーの config/recmod.properties)。録画を始めるたびに読み直すので、ゲームを閉じずに、書き換えて試せる。
 *  qp     : 画質。H.264 の量子化の数値(10〜40)。小さいほどきれいで、ファイルが大きく、再生が重い。
 *  matrix : 色の式。bt709(HD の標準)か bt601。
 *  range  : 明るさの範囲。limited(16〜235、標準)か full(0〜255)。
 */
final class RecConfig {

	private static final String DEFAULT_TEXT = """
			# recmod の設定。録画を始めるたびに読み直します。
			#
			# 画質: H.264 の量子化の数値(10〜40)。小さいほどきれいで、ファイルが大きく、再生が重くなります。
			#   18 = とても高画質、22 = 高画質(標準)、26 = ふつう、30 = 軽い
			qp=22
			#
			# 色の式: bt709(HD の標準。たいていはこれ)か bt601。色が違って見えるときに試してください。
			matrix=bt709
			#
			# 明るさの範囲: limited(16〜235、標準)か full(0〜255)。黒が浮く・白が飛ぶときに試してください。
			range=limited
			""";

	/** 量子化の数値。 */
	static volatile int qp = 22;
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
			Properties p = new Properties();
			try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				p.load(in);
			}
			qp = Math.max(10, Math.min(40, Integer.parseInt(p.getProperty("qp", "22").trim())));
			bt709 = !p.getProperty("matrix", "bt709").trim().equalsIgnoreCase("bt601");
			fullRange = p.getProperty("range", "limited").trim().equalsIgnoreCase("full");
		} catch (IOException | RuntimeException e) {
			Recorder.LOGGER.warn("設定ファイルを読めなかったので、標準の設定を使います: " + file, e);
			qp = 22;
			bt709 = true;
			fullRange = false;
		}
		Recorder.LOGGER.info("録画の設定: qp=" + qp + ", matrix=" + (bt709 ? "bt709" : "bt601") + ", range=" + (fullRange ? "full" : "limited"));
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
