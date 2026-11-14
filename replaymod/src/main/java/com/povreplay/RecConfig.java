package com.povreplay;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 画質と色の設定(ゲームのフォルダーの config/povreplay.properties)。録画を始めるたびに読み直すので、ゲームを閉じずに、書き換えて試せる。
 *  width / height : 動画の大きさ(標準 1920x1080。ウィンドウの大きさに関係なく、この大きさで描く)。
 *  matrix : 色の式。bt709(HD の標準)か bt601。
 *  range  : 明るさの範囲。limited(16〜235、標準)か full(0〜255)。
 */
public final class RecConfig {

	/** ffmpeg まわりの設定(前の版の設定ファイルにも、あとから足す)。 */
	private static final String FFMPEG_TEXT = """
			#
			# ffmpeg を使うと、同じ画質で、ファイルが 1/5〜1/8 ほど小さくなります(ffmpeg を別に入れる必要があります)。
			#   auto : ffmpeg を探して、あれば使う(なければ、内蔵のエンコーダーで書き出す)。標準
			#   off  : 使わない(内蔵のエンコーダーだけ)
			#   それ以外: ffmpeg の場所(ファイル、またはフォルダー)。例  C:\\ffmpeg\\bin\\ffmpeg.exe
			ffmpeg=auto
			# ffmpeg を使うときの画質(CRF)。小さいほどきれいで、ファイルが大きい。18 = ほぼ劣化なし、20 = 標準、23 = ふつう、26 = 軽い
			crf=20
			# ffmpeg を使うときの速さ。ultrafast / veryfast / fast / medium / slow / slower。遅いほど、小さくなる(標準 medium)
			preset=medium
			# 上級者向け: 映像の圧縮の指定を、まるごと書き換える(空なら、上の crf と preset を使う)。例  -c:v h264_nvenc -preset p5 -cq 22
			videoArgs=
			#
			# 内蔵のエンコーダーを使うときの画質(量子化の数値 10〜40)。小さいほどきれい。大きくすると、ファイルが小さくなる(標準 18)
			qp=18
			""";

	private static final String DEFAULT_TEXT = """
			# povreplay の設定。録画を始めるたびに読み直します。
			#
			# 動画の大きさ。ゲームの画面(ウィンドウ)の大きさに関係なく、動画にするときに、この大きさで描き直します(縮小も拡大もしません)。
			# 大きくするほど、動画にするのに時間がかかります。
			width=1920
			height=1080
			#
			# 色の式: bt709(HD の標準。たいていはこれ)か bt601。色が違って見えるときに試してください。
			matrix=bt709
			#
			# 明るさの範囲: limited(16〜235、標準)か full(0〜255)。黒が浮く・白が飛ぶときに試してください。
			range=limited
			""" + FFMPEG_TEXT;

	/** 動画の大きさ。 */
	public static volatile int width = 1920;
	public static volatile int height = 1080;
	/** 色の式が BT.709 か(false なら BT.601)。 */
	static volatile boolean bt709 = true;
	/** 明るさの範囲が full(0〜255)か(false なら 16〜235)。 */
	static volatile boolean fullRange;
	/** ffmpeg の指定("auto"・"off"・場所)。 */
	static volatile String ffmpeg = "auto";
	static volatile int crf = 20;
	static volatile String preset = "medium";
	static volatile String videoArgs = "";
	/** 内蔵のエンコーダーの量子化の数値。 */
	public static volatile int qp = 18;

	private RecConfig() {
	}

	public static void load(Path gameDirectory) {
		Path file = gameDirectory.resolve("config").resolve("povreplay.properties");
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
			// 前の版の設定ファイルには、ffmpeg の設定がないので、あとから足す(今までの設定は、そのまま残す)
			if (!text.contains("ffmpeg=")) {
				Files.writeString(file, text.stripTrailing() + "\n" + FFMPEG_TEXT, StandardCharsets.UTF_8);
			}
			Properties p = new Properties();
			try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				p.load(in);
			}
			width = Math.max(64, Math.min(7680, Integer.parseInt(p.getProperty("width", "1920").trim()))) & ~1;
			height = Math.max(64, Math.min(4320, Integer.parseInt(p.getProperty("height", "1080").trim()))) & ~1;
			bt709 = !p.getProperty("matrix", "bt709").trim().equalsIgnoreCase("bt601");
			fullRange = p.getProperty("range", "limited").trim().equalsIgnoreCase("full");
			ffmpeg = p.getProperty("ffmpeg", "auto").trim();
			crf = Math.max(0, Math.min(51, Integer.parseInt(p.getProperty("crf", "20").trim())));
			preset = p.getProperty("preset", "medium").trim();
			videoArgs = p.getProperty("videoArgs", "").trim();
			qp = Math.max(10, Math.min(40, Integer.parseInt(p.getProperty("qp", "18").trim())));
		} catch (IOException | RuntimeException e) {
			PovReplay.LOGGER.warn("設定ファイルを読めなかったので、標準の設定を使います: " + file, e);
			width = 1920;
			height = 1080;
			bt709 = true;
			fullRange = false;
			ffmpeg = "auto";
			crf = 20;
			preset = "medium";
			videoArgs = "";
			qp = 18;
		}
		PovReplay.LOGGER.info("録画の設定: " + width + "x" + height + ", ffmpeg=" + ffmpeg + ", crf=" + crf + ", preset=" + preset + ", qp=" + qp + ", matrix=" + (bt709 ? "bt709" : "bt601") + ", range=" + (fullRange ? "full" : "limited"));
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
