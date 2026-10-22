package com.lampvideo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * 動画をレッドストーンランプの点灯/消灯で再生する。
 *
 * - jar に同梱の動画(sample)は、jar を入れるだけで再生できる。
 * - .minecraft/lampvideo/ に動画ファイル(mp4 など)を入れると、ffmpeg が使える場合に自動で変換して、再生できるようになる。
 * - 画面の大きさは 360 / 540 / 720 から選ぶ。大きさごとの動画ファイル(<名前>_<大きさ>.lvf)は、必要になったときに変換する。
 * - 画面の向きは2種類。flat(既定)は地面に水平で、足元が画面の中心。上空から見下ろして、北が画面の上、東が右。
 *   wall は壁(縦)で、足元が左下の角、+X(右)と +Y(上)に広がり、南側(+Z)から北を向くと正しい向きに見える。
 * - 作った画面は保存されるので、再起動後も /lampvideo play でそのまま使える。
 * - ランプは neighbor update なし(フラグ2)で直接 lit を切り替えるので、背面の電源ブロックは要らない。
 */
public class LampVideoMod implements ModInitializer {

	public static final String MOD_ID = "lampvideo";

	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** setBlock のフラグ。2 = クライアントに送るだけで、隣のブロックには通知しない(ランプが勝手に消えない)。 */
	private static final int FLAGS = 2;

	/** jar に同梱している動画の名前。 */
	private static final String BUNDLED = "sample";
	private static final int[] SIZES = { 360, 540, 720 };
	/** 起動時の自動変換で作る大きさ。 */
	private static final int AUTO_SIZE = 360;
	private static final int CONVERT_HOLD = 3;

	/** 再生速度の指定と、音声ファイルの種類。1/2/3 は全フレームを 1/2/3 tick ずつ、normal は間引いて等速。 */
	private static final String[] SPEEDS = { "1", "2", "3", "normal" };
	private static final String[] AUDIO_MODES = { "t1", "t2", "t3", "n" };

	private static final Pattern VIDEO_EXT = Pattern.compile("(?i).*\\.(mp4|mkv|webm|mov|avi|m4v|flv|wmv)$");
	private static final Pattern SIZED_LVF = Pattern.compile("(.+)_(\\d+)\\.lvf");

	private static final ExecutorService CONVERTER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "lampvideo-converter");
		t.setDaemon(true);
		return t;
	});
	private static final Set<String> converting = ConcurrentHashMap.newKeySet();

	private static MinecraftServer server;

	// ---- 設定(保存される) ----
	private static boolean layoutFlat = true;
	private static boolean useConcrete = false;
	private static String speedKey = "3";
	private static String lastName = BUNDLED;

	// ---- 作った画面(保存される) ----
	private static boolean hasScreen;
	private static String screenDim;
	private static BlockPos origin;
	private static int screenWidth;
	private static int screenHeight;
	private static boolean screenFlat;
	private static boolean screenConcrete;

	// ---- 再生中の状態 ----
	private static LvfVideo video;
	private static int tickCounter;
	private static double frameAccumulator;
	private static String playingSpeed = "3";
	private static String playingName;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("lampvideo")
						.then(Commands.literal("start")
								.then(sizeArgument()
										.then(speedArgument()
												.executes(ctx -> start(ctx, size(ctx), speed(ctx), lastName))
												.then(nameArgument()
														.executes(ctx -> start(ctx, size(ctx), speed(ctx), name(ctx)))))))
						.then(Commands.literal("setup")
								.then(sizeArgument()
										.executes(ctx -> setup(ctx, size(ctx), lastName))
										.then(nameArgument()
												.executes(ctx -> setup(ctx, size(ctx), name(ctx))))))
						.then(Commands.literal("play")
								.executes(ctx -> play(ctx, speedKey, lastName))
								.then(speedArgument()
										.executes(ctx -> play(ctx, speed(ctx), lastName))
										.then(nameArgument()
												.executes(ctx -> play(ctx, speed(ctx), name(ctx))))))
						.then(Commands.literal("stop").executes(LampVideoMod::stop))
						.then(Commands.literal("remove").executes(LampVideoMod::remove))
						.then(Commands.literal("list").executes(LampVideoMod::list))
						.then(Commands.literal("status").executes(LampVideoMod::status))
						.then(Commands.literal("convert").executes(LampVideoMod::convert))
						.then(Commands.literal("set")
								.then(Commands.literal("layout")
										.then(Commands.literal("flat").executes(ctx -> setLayout(ctx, true)))
										.then(Commands.literal("wall").executes(ctx -> setLayout(ctx, false))))
								.then(Commands.literal("block")
										.then(Commands.literal("lamp").executes(ctx -> setBlock(ctx, false)))
										.then(Commands.literal("wool").executes(ctx -> setBlock(ctx, true)))))));

		ServerTickEvents.END_SERVER_TICK.register(s -> tick());
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			prepareFolder();
			loadState();
			scan(s, null);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
			stopPlayback();
			server = null;
		});
	}

	private static RequiredArgumentBuilder<CommandSourceStack, Integer> sizeArgument() {
		return Commands.argument("size", IntegerArgumentType.integer())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("360", "540", "720"), builder));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> speedArgument() {
		return Commands.argument("speed", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of(SPEEDS), builder));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> nameArgument() {
		return Commands.argument("name", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(availableNames(), builder));
	}

	private static int size(CommandContext<CommandSourceStack> ctx) {
		return IntegerArgumentType.getInteger(ctx, "size");
	}

	private static String speed(CommandContext<CommandSourceStack> ctx) {
		return StringArgumentType.getString(ctx, "speed");
	}

	private static String name(CommandContext<CommandSourceStack> ctx) {
		return StringArgumentType.getString(ctx, "name");
	}

	private static void ok(CommandSourceStack source, String message) {
		source.sendSuccess(() -> Component.literal("[ランプ動画] " + message), false);
	}

	private static int fail(CommandSourceStack source, String message) {
		source.sendFailure(Component.literal("[ランプ動画] " + message));
		return 0;
	}

	// ---- フォルダ・設定・保存 ----

	private static Path videoDir() {
		return FabricLoader.getInstance().getGameDir().resolve("lampvideo");
	}

	private static Path audioPackDir() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve("lampvideo_audio");
	}

	private static Path stateFile() {
		return videoDir().resolve("lampvideo.properties");
	}

	private static void prepareFolder() {
		try {
			Files.createDirectories(videoDir());
			Path note = videoDir().resolve("使い方.txt");
			if (!Files.exists(note)) {
				Files.writeString(note, String.join("\n",
						"このフォルダに動画ファイル(mp4 など)を入れると、ワールドを開いたときに自動で変換されます。",
						"(動画の読み込みに ffmpeg が必要です。インストールして、PATH を通してください)",
						"",
						"変換が終わると、/lampvideo list に名前が出ます。/lampvideo start 360 3 <名前> で再生できます。",
						"360 以外の大きさ(540, 720)は、初めて使うときに変換します(時間がかかります)。",
						"",
						"音声を鳴らすには、resourcepacks/lampvideo_audio を有効にして、F3+T でリソースを再読み込みしてください。",
						""), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			LOGGER.warn("lampvideo フォルダを作れませんでした", e);
		}
	}

	private static void saveState() {
		Properties p = new Properties();
		p.setProperty("layout", layoutFlat ? "flat" : "wall");
		p.setProperty("block", useConcrete ? "wool" : "lamp");
		p.setProperty("speed", speedKey);
		p.setProperty("name", lastName);
		if (hasScreen) {
			p.setProperty("screen.dim", screenDim);
			p.setProperty("screen.x", Integer.toString(origin.getX()));
			p.setProperty("screen.y", Integer.toString(origin.getY()));
			p.setProperty("screen.z", Integer.toString(origin.getZ()));
			p.setProperty("screen.w", Integer.toString(screenWidth));
			p.setProperty("screen.h", Integer.toString(screenHeight));
			p.setProperty("screen.layout", screenFlat ? "flat" : "wall");
			p.setProperty("screen.block", screenConcrete ? "wool" : "lamp");
		}
		try (OutputStream out = Files.newOutputStream(stateFile())) {
			p.store(out, "lampvideo");
		} catch (IOException e) {
			LOGGER.warn("設定を保存できませんでした", e);
		}
	}

	private static void loadState() {
		Properties p = new Properties();
		if (Files.exists(stateFile())) {
			try (InputStream in = Files.newInputStream(stateFile())) {
				p.load(in);
			} catch (IOException e) {
				LOGGER.warn("設定を読み込めませんでした", e);
				return;
			}
		}
		layoutFlat = !"wall".equals(p.getProperty("layout"));
		useConcrete = "wool".equals(p.getProperty("block"));
		speedKey = validSpeed(p.getProperty("speed")) ? p.getProperty("speed") : "3";
		lastName = p.getProperty("name", BUNDLED);
		hasScreen = false;
		try {
			if (p.getProperty("screen.dim") != null) {
				screenDim = p.getProperty("screen.dim");
				origin = new BlockPos(Integer.parseInt(p.getProperty("screen.x")),
						Integer.parseInt(p.getProperty("screen.y")), Integer.parseInt(p.getProperty("screen.z")));
				screenWidth = Integer.parseInt(p.getProperty("screen.w"));
				screenHeight = Integer.parseInt(p.getProperty("screen.h"));
				screenFlat = !"wall".equals(p.getProperty("screen.layout"));
				screenConcrete = "wool".equals(p.getProperty("screen.block"));
				hasScreen = true;
			}
		} catch (RuntimeException e) {
			LOGGER.warn("保存された画面の情報が不正です", e);
		}
	}

	private static boolean validSpeed(String s) {
		for (String v : SPEEDS) {
			if (v.equals(s)) {
				return true;
			}
		}
		return false;
	}

	private static String audioMode(String speed) {
		return AUDIO_MODES[java.util.Arrays.asList(SPEEDS).indexOf(speed)];
	}

	/** 保存された画面があるディメンションを探す。見つからなければ null。 */
	private static ServerLevel screenLevel() {
		if (!hasScreen || server == null) {
			return null;
		}
		for (ServerLevel l : server.getAllLevels()) {
			if (l.dimension().toString().equals(screenDim)) {
				return l;
			}
		}
		return null;
	}

	// ---- 動画の名前とファイル ----

	/** 動画ファイル名から、コマンドや音声の ID に使える名前(小文字の英数字と _)を作る。 */
	static String safeName(String fileName) {
		String base = fileName.replaceFirst("\\.[^.]*$", "");
		String s = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (s.length() > 40) {
			s = s.substring(0, 40).replaceAll("_+$", "");
		}
		return s.isEmpty() ? "video_" + Integer.toHexString(base.hashCode()) : s;
	}

	/** 再生できる動画の名前(同梱 + 変換済み + 変換待ち)。 */
	private static List<String> availableNames() {
		Set<String> names = new TreeSet<>();
		names.add(BUNDLED);
		try (Stream<Path> s = Files.list(videoDir())) {
			s.map(p -> p.getFileName().toString()).forEach(n -> {
				Matcher m = SIZED_LVF.matcher(n);
				if (m.matches()) {
					names.add(m.group(1));
				} else if (VIDEO_EXT.matcher(n).matches()) {
					names.add(safeName(n));
				}
			});
		} catch (IOException e) {
			// フォルダがなければ同梱の動画だけ
		}
		return new ArrayList<>(names);
	}

	private static List<Integer> sizesOf(String name) {
		Set<Integer> sizes = new TreeSet<>();
		if (BUNDLED.equals(name) && LampVideoMod.class.getResource("/lampvideo/" + name + "_" + AUTO_SIZE + ".lvf") != null) {
			sizes.add(AUTO_SIZE);
		}
		try (Stream<Path> s = Files.list(videoDir())) {
			s.map(p -> p.getFileName().toString()).forEach(n -> {
				Matcher m = SIZED_LVF.matcher(n);
				if (m.matches() && m.group(1).equals(name)) {
					sizes.add(Integer.parseInt(m.group(2)));
				}
			});
		} catch (IOException e) {
			// ignore
		}
		return new ArrayList<>(sizes);
	}

	/** <名前>_<大きさ>.lvf を開く(フォルダ → jar の順)。なければ null。 */
	private static InputStream openVideoStream(String name, int size) throws IOException {
		Path external = videoDir().resolve(name + "_" + size + ".lvf");
		if (Files.exists(external)) {
			return Files.newInputStream(external);
		}
		return LampVideoMod.class.getResourceAsStream("/lampvideo/" + name + "_" + size + ".lvf");
	}

	/** 変換元の動画ファイル。フォルダの動画 → (sample なら)jar の同梱動画。なければ null。 */
	private static Path findSource(String name) throws IOException {
		try (Stream<Path> s = Files.list(videoDir())) {
			for (Path p : (Iterable<Path>) s.sorted()::iterator) {
				String n = p.getFileName().toString();
				if (Files.isRegularFile(p) && VIDEO_EXT.matcher(n).matches() && safeName(n).equals(name)) {
					return p;
				}
			}
		}
		if (BUNDLED.equals(name)) {
			Path cache = videoDir().resolve(".cache").resolve(BUNDLED + ".mp4");
			if (!Files.exists(cache)) {
				try (InputStream in = LampVideoMod.class.getResourceAsStream("/lampvideo/" + BUNDLED + ".mp4")) {
					if (in == null) {
						return null;
					}
					Files.createDirectories(cache.getParent());
					Files.copy(in, cache, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			return cache;
		}
		return null;
	}

	// ---- 変換 ----

	private static void notifyUser(MinecraftServer s, CommandSourceStack notify, String message) {
		LOGGER.info("[lampvideo] {}", message);
		if (notify != null) {
			s.execute(() -> notify.sendSuccess(() -> Component.literal("[ランプ動画] " + message), false));
		}
	}

	/** 起動時と /lampvideo convert: フォルダ内の、まだ AUTO_SIZE に変換していない動画を変換する。 */
	private static void scan(MinecraftServer s, CommandSourceStack notify) {
		CONVERTER.execute(() -> {
			List<Path> todo = new ArrayList<>();
			try (Stream<Path> files = Files.list(videoDir())) {
				files.filter(Files::isRegularFile)
						.filter(p -> VIDEO_EXT.matcher(p.getFileName().toString()).matches())
						.sorted().forEach(p -> {
							String name = safeName(p.getFileName().toString());
							if (!Files.exists(videoDir().resolve(name + "_" + AUTO_SIZE + ".lvf"))
									&& !converting.contains(name + "_" + AUTO_SIZE)) {
								todo.add(p);
							}
						});
			} catch (IOException e) {
				return;
			}
			if (todo.isEmpty()) {
				if (notify != null) {
					notifyUser(s, notify, "変換する動画はありません(" + videoDir() + " に動画を入れてください)");
				}
				return;
			}
			if (!VideoConverter.toolsAvailable()) {
				notifyUser(s, notify, todo.size() + " 個の動画を変換したいのですが、ffmpeg が見つかりません。"
						+ "ffmpeg をインストールして PATH を通してから、/lampvideo convert を実行してください");
				return;
			}
			for (Path file : todo) {
				convertOne(s, notify, file, safeName(file.getFileName().toString()), AUTO_SIZE);
			}
		});
	}

	/** 指定の大きさの動画がなければ、変換元から作る(別スレッド)。変換が始まったら true。 */
	private static boolean requestConvert(CommandSourceStack source, String name, int size) {
		Path src;
		try {
			src = findSource(name);
		} catch (IOException e) {
			src = null;
		}
		if (src == null) {
			fail(source, "動画 " + name + " が見つかりません(/lampvideo list で確認できます)");
			return false;
		}
		final Path file = src;
		MinecraftServer s = source.getServer();
		CONVERTER.execute(() -> {
			if (!VideoConverter.toolsAvailable()) {
				notifyUser(s, source, name + " の " + size + " 用の動画を作るには ffmpeg が必要です。"
						+ "ffmpeg をインストールして PATH を通してから、もう一度実行してください");
				return;
			}
			convertOne(s, source, file, name, size);
		});
		ok(source, name + " の " + size + " 用の動画がないので、変換します。終わったら知らせるので、もう一度同じコマンドを実行してください");
		return true;
	}

	private static void convertOne(MinecraftServer s, CommandSourceStack notify, Path file, String name, int size) {
		String key = name + "_" + size;
		if (!converting.add(key)) {
			return;
		}
		try {
			notifyUser(s, notify, file.getFileName() + " を " + size + " 用に変換しています(名前: " + name + ")。時間がかかります…");
			VideoConverter.Info info = VideoConverter.probe(file);
			int frames = VideoConverter.convert(file, videoDir().resolve(key + ".lvf"), info,
					size, CONVERT_HOLD, 110, 24, 100, 0,
					n -> LOGGER.info("[lampvideo] {}: {} フレーム変換済み", key, n));
			String audioNote = "";
			if (info.hasAudio && !BUNDLED.equals(name) && !Files.exists(audioPackDir().resolve("assets")
					.resolve(MOD_ID).resolve("sounds").resolve(name + "_n.ogg"))) {
				try {
					writeAudio(file, name, info);
					audioNote = " 音声を鳴らすには、リソースパック lampvideo_audio を有効にして F3+T で再読み込みしてください。";
				} catch (IOException e) {
					LOGGER.warn("音声の変換に失敗しました", e);
					audioNote = " (音声の変換には失敗しました)";
				}
			}
			notifyUser(s, notify, name + " の " + size + " 用の変換が終わりました(" + frames + " フレーム)。"
					+ "/lampvideo start " + size + " <速度> " + name + " で再生できます。" + audioNote);
		} catch (IOException | InterruptedException | RuntimeException e) {
			LOGGER.warn("変換に失敗しました: {}", file, e);
			notifyUser(s, notify, file.getFileName() + " の変換に失敗しました: " + e.getMessage());
		} finally {
			converting.remove(key);
		}
	}

	/** 音声を、再生速度ごと(1tick/2tick/3tick/等速)に作って、リソースパック lampvideo_audio に追加する。 */
	private static void writeAudio(Path file, String name, VideoConverter.Info info)
			throws IOException, InterruptedException {
		Path pack = audioPackDir();
		Path sounds = pack.resolve("assets").resolve(MOD_ID).resolve("sounds");
		Files.createDirectories(sounds);
		double[] rates = { 20.0 / info.fps, 20.0 / (info.fps * 2), 20.0 / (info.fps * 3), 1.0 };
		for (int i = 0; i < AUDIO_MODES.length; i++) {
			VideoConverter.extractAudio(file, sounds.resolve(name + "_" + AUDIO_MODES[i] + ".ogg"), rates[i]);
		}

		StringBuilder json = new StringBuilder("{\n");
		List<String> oggs = new ArrayList<>();
		try (Stream<Path> s = Files.list(sounds)) {
			s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".ogg")).sorted()
					.forEach(n -> oggs.add(n.substring(0, n.length() - 4)));
		}
		for (int i = 0; i < oggs.size(); i++) {
			json.append("  \"").append(oggs.get(i)).append("\": {\"sounds\": [{\"name\": \"")
					.append(MOD_ID).append(':').append(oggs.get(i)).append("\", \"stream\": true}]}")
					.append(i + 1 < oggs.size() ? ",\n" : "\n");
		}
		json.append("}\n");
		Files.writeString(pack.resolve("assets").resolve(MOD_ID).resolve("sounds.json"), json.toString(),
				StandardCharsets.UTF_8);
		Files.writeString(pack.resolve("pack.mcmeta"), packMeta(), StandardCharsets.UTF_8);
	}

	/** いま遊んでいるバージョンのリソースパック形式番号を、ゲーム内の version.json から読んで pack.mcmeta を作る。 */
	private static String packMeta() {
		int major = -1;
		boolean split = false;
		try (InputStream in = MinecraftServer.class.getResourceAsStream("/version.json")) {
			if (in != null) {
				String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				Matcher m = Pattern.compile("\"resource_major\"\\s*:\\s*(\\d+)").matcher(text);
				if (m.find()) {
					major = Integer.parseInt(m.group(1));
					split = true;
				} else {
					m = Pattern.compile("\"resource\"\\s*:\\s*(\\d+)").matcher(text);
					if (m.find()) {
						major = Integer.parseInt(m.group(1));
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			// 読めなければ下の既定値
		}
		String desc = "\"description\": \"ランプ動画の音声\"";
		if (split) {
			return "{\"pack\": {" + desc + ", \"min_format\": " + major + ", \"max_format\": " + major + "}}\n";
		}
		return "{\"pack\": {" + desc + ", \"pack_format\": " + (major > 0 ? major : 34) + "}}\n";
	}

	// ---- コマンド: 一覧・設定 ----

	private static int convert(CommandContext<CommandSourceStack> ctx) {
		prepareFolder();
		scan(ctx.getSource().getServer(), ctx.getSource());
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		List<String> lines = new ArrayList<>();
		for (String name : availableNames()) {
			lines.add("  " + name + "  (変換済みの大きさ: " + sizesOf(name) + ")" + (name.equals(BUNDLED) ? " 同梱" : ""));
		}
		for (String key : converting) {
			lines.add("  " + key + "  (変換中…)");
		}
		ok(ctx.getSource(), "再生できる動画:\n" + String.join("\n", lines)
				+ "\n動画ファイルは " + videoDir() + " に入れてください(ffmpeg が必要)。大きさは 360 / 540 / 720");
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		String screen = hasScreen
				? screenWidth + "x" + screenHeight + " " + (screenFlat ? "flat" : "wall") + " " + (screenConcrete ? "wool" : "lamp")
						+ " 位置 " + origin.getX() + " " + origin.getY() + " " + origin.getZ()
				: "なし";
		ok(ctx.getSource(), "画面: " + screen + "\n設定: layout=" + (layoutFlat ? "flat" : "wall")
				+ " block=" + (useConcrete ? "wool" : "lamp") + " speed=" + speedKey + " name=" + lastName
				+ "\n再生中: " + (video != null ? playingName + " (速度 " + playingSpeed + ")" : "なし"));
		return 1;
	}

	private static int setLayout(CommandContext<CommandSourceStack> ctx, boolean flat) {
		layoutFlat = flat;
		saveState();
		ok(ctx.getSource(), "画面の向きを " + (flat ? "flat(水平)" : "wall(縦)") + " にしました(次の setup / start から)");
		return 1;
	}

	private static int setBlock(CommandContext<CommandSourceStack> ctx, boolean wool) {
		useConcrete = wool;
		saveState();
		ok(ctx.getSource(), "画面のブロックを " + (wool ? "羊毛(白/黒)" : "レッドストーンランプ")
				+ " にしました(次の setup / start から)。羊毛は光の計算が要らないので軽く、ちらつきも減るかもしれません");
		return 1;
	}

	// ---- コマンド: 画面と再生 ----

	/** 画面を作って(保存された画面と同じなら、そのまま使って)再生する。 */
	private static int start(CommandContext<CommandSourceStack> ctx, int size, String speed, String name) {
		CommandSourceStack source = ctx.getSource();
		if (!validSpeed(speed)) {
			return fail(source, "速度は 1 / 2 / 3 / normal のどれかです");
		}
		int[] dim = videoSize(source, name, size);
		if (dim == null) {
			return 0;
		}
		boolean reusable = screenLevel() != null && screenWidth == dim[0] && screenHeight == dim[1]
				&& screenFlat == layoutFlat && screenConcrete == useConcrete;
		if (reusable) {
			ok(source, "前回の画面をそのまま使います");
		} else if (!buildScreen(source, size, name)) {
			return 0;
		}
		return play(ctx, speed, name);
	}

	/** 動画の画面の大きさ {幅, 高さ}。ファイルがなければ変換を始めて null。 */
	private static int[] videoSize(CommandSourceStack source, String name, int size) {
		if (java.util.Arrays.stream(SIZES).noneMatch(v -> v == size)) {
			fail(source, "大きさは 360 / 540 / 720 のどれかです");
			return null;
		}
		try (InputStream raw = openVideoStream(name, size)) {
			if (raw == null) {
				requestConvert(source, name, size);
				return null;
			}
			try (LvfVideo v = LvfVideo.open(raw)) {
				return new int[] { v.width, v.height };
			}
		} catch (IOException e) {
			fail(source, "読み込めませんでした: " + e.getMessage());
			return null;
		}
	}

	private static int setup(CommandContext<CommandSourceStack> ctx, int size, String name) {
		return buildScreen(ctx.getSource(), size, name) ? 1 : 0;
	}

	/** ランプ面の位置。画像の (x, y) は、x が左から、y が上から。 */
	private static BlockPos.MutableBlockPos lampAt(BlockPos.MutableBlockPos pos, int x, int y) {
		return screenFlat
				? pos.set(origin.getX() + x, origin.getY(), origin.getZ() + y)
				: pos.set(origin.getX() + x, origin.getY() + (screenHeight - 1 - y), origin.getZ());
	}

	/** ランプ面の手前(見る側)の層。flat は真上、wall は +Z 側。 */
	private static BlockPos.MutableBlockPos frontAt(BlockPos.MutableBlockPos pos, int x, int y) {
		return screenFlat
				? pos.set(origin.getX() + x, origin.getY() + 1, origin.getZ() + y)
				: pos.set(origin.getX() + x, origin.getY() + (screenHeight - 1 - y), origin.getZ() + 1);
	}

	private static BlockState onState() {
		return screenConcrete ? Blocks.WHITE_WOOL.defaultBlockState()
				: Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true);
	}

	private static BlockState offState() {
		return screenConcrete ? Blocks.BLACK_WOOL.defaultBlockState()
				: Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, false);
	}

	/**
	 * 画面(ランプ+手前の光源ブロック)を作る。設定の layout が flat なら足元を中心に水平に、
	 * wall なら足元を左下の角にして縦に作る。
	 */
	private static boolean buildScreen(CommandSourceStack source, int size, String name) {
		int[] dim = videoSize(source, name, size);
		if (dim == null) {
			return false;
		}
		int w = dim[0];
		int h = dim[1];
		Vec3 p = source.getPosition();
		BlockPos feet = BlockPos.containing(p.x, p.y, p.z);
		BlockPos base;
		if (layoutFlat) {
			base = new BlockPos(feet.getX() - w / 2, feet.getY() - 1, feet.getZ() - h / 2);
		} else {
			base = feet;
			if (base.getY() + h - 1 > 319) {
				fail(source, "高さ不足です。画面の高さが" + h + "ブロックあるので、Y=" + (320 - h)
						+ "以下の場所で実行してください(スーパーフラットの地面など)。水平にするには /lampvideo set layout flat");
				return false;
			}
		}

		stopPlayback();
		clearScreen();
		ServerLevel level = source.getLevel();
		screenDim = level.dimension().toString();
		origin = base;
		screenWidth = w;
		screenHeight = h;
		screenFlat = layoutFlat;
		screenConcrete = useConcrete;
		hasScreen = true;

		forceChunks(level, true);
		BlockState off = offState();
		BlockState light = Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 15);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				level.setBlock(lampAt(pos, x, y), off, FLAGS);
				if (!screenConcrete) {
					// 手前に明るさ15の光源を敷き、ランプ面の明るさを一定にする(明暗のムラを防ぐ)
					level.setBlock(frontAt(pos, x, y), light, FLAGS);
				}
			}
		}
		saveState();
		final String how = screenFlat
				? "足元を中心に水平に作りました。上空(クリエイティブで上に飛ぶ)から真下を向いて、北が画面の上になる向きで見てください。"
				: "足元が左下の角です。南側から北を向いて見てください。";
		ok(source, w + "x" + h + " の画面を設置しました。" + how);
		return true;
	}

	private static int play(CommandContext<CommandSourceStack> ctx, String speed, String name) {
		CommandSourceStack source = ctx.getSource();
		if (!validSpeed(speed)) {
			return fail(source, "速度は 1 / 2 / 3 / normal のどれかです");
		}
		ServerLevel level = screenLevel();
		if (level == null) {
			return fail(source, "画面がありません。先に /lampvideo start <大きさ> <速度> [名前] か /lampvideo setup <大きさ> を実行してください");
		}
		stopPlayback();
		try (InputStream raw = openVideoStream(name, screenWidth)) {
			if (raw == null) {
				requestConvert(source, name, screenWidth);
				return 0;
			}
			video = LvfVideo.open(raw);
		} catch (IOException e) {
			return fail(source, "読み込めませんでした: " + e.getMessage());
		}
		if (video.width != screenWidth || video.height != screenHeight) {
			stopPlayback();
			return fail(source, "この動画は画面と大きさが違います(動画 " + video.width + "x" + video.height
					+ " / 画面 " + screenWidth + "x" + screenHeight + ")。/lampvideo setup で画面を作り直してください");
		}
		tickCounter = 0;
		frameAccumulator = 1.0; // 最初のtickで先頭のフレームを出す
		playingSpeed = speed;
		playingName = name;
		lastName = name;
		speedKey = speed;
		saveState();
		// 音声(リソースパックの lampvideo:<名前>_<速度>)。各プレイヤーの位置で鳴らす
		runCommand(source, "execute as @a at @s run playsound " + MOD_ID + ":" + name + "_" + audioMode(speed)
				+ " master @s ~ ~ ~ 1 1");
		ok(source, "再生を開始します(" + name + ", 速度 " + speed + ")");
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) {
		stopPlayback();
		stopSound(ctx.getSource());
		ok(ctx.getSource(), "停止しました");
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		stopPlayback();
		stopSound(ctx.getSource());
		clearScreen();
		hasScreen = false;
		saveState();
		ok(ctx.getSource(), "画面を片付けました");
		return 1;
	}

	private static void stopSound(CommandSourceStack source) {
		String name = playingName != null ? playingName : lastName;
		for (String mode : AUDIO_MODES) {
			runCommand(source, "stopsound @a * " + MOD_ID + ":" + name + "_" + mode);
		}
	}

	private static void runCommand(CommandSourceStack source, String command) {
		source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput(), command);
	}

	// ---- 再生 ----

	/**
	 * 毎tick、速度に合わせてフレームを進める。
	 * 1/2/3: 全フレームを 1/2/3 tick ずつ表示。normal: 元の fps を 20tick/秒に合わせて、フレームを間引いて等速で再生する。
	 */
	private static void tick() {
		if (video == null) {
			return;
		}
		ServerLevel level = screenLevel();
		if (level == null) {
			stopPlayback();
			return;
		}
		int framesNow;
		if ("normal".equals(playingSpeed)) {
			frameAccumulator += video.fps / 20.0;
			framesNow = (int) frameAccumulator;
			frameAccumulator -= framesNow;
		} else {
			int hold = Integer.parseInt(playingSpeed);
			framesNow = (tickCounter++ % hold == 0) ? 1 : 0;
		}
		if (framesNow == 0) {
			return;
		}
		BlockState on = onState();
		BlockState off = offState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		try {
			for (int i = 0; i < framesNow; i++) {
				if (!video.readFrame((x, y, lit) -> level.setBlock(lampAt(pos, x, y), lit ? on : off, FLAGS))) {
					stopPlayback();
					return;
				}
			}
		} catch (IOException e) {
			stopPlayback();
		}
	}

	private static void stopPlayback() {
		if (video != null) {
			try {
				video.close();
			} catch (IOException ignored) {
				// 閉じるだけなので無視する
			}
			video = null;
		}
	}

	/** 画面・光源ブロックを消して、読み込み固定を解除する。 */
	private static void clearScreen() {
		ServerLevel level = screenLevel();
		if (level == null) {
			return;
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < screenHeight; y++) {
			for (int x = 0; x < screenWidth; x++) {
				level.setBlock(lampAt(pos, x, y), air, FLAGS);
				if (!screenConcrete) {
					level.setBlock(frontAt(pos, x, y), air, FLAGS);
				}
			}
		}
		forceChunks(level, false);
	}

	/** 画面の範囲のチャンクを常に読み込んでおく(読み込まれていないと書き込めない)。 */
	private static void forceChunks(ServerLevel level, boolean force) {
		int minX = origin.getX() >> 4;
		int maxX = (origin.getX() + screenWidth - 1) >> 4;
		int minZ = origin.getZ() >> 4;
		int maxZ = (origin.getZ() + (screenFlat ? screenHeight - 1 : 1)) >> 4;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				level.setChunkForced(cx, cz, force);
			}
		}
	}
}
