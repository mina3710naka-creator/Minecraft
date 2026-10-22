package com.lampvideo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

import com.mojang.brigadier.arguments.StringArgumentType;
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
 * - 画面の向きは2種類。flat(既定)は地面に水平で、足元が画面の中心。上空から見下ろして、北が画面の上、東が右。
 *   wall は壁(縦)で、足元が左下の角、+X(右)と +Y(上)に広がり、南側(+Z)から北を向くと正しい向きに見える。
 * - ランプは neighbor update なし(フラグ2)で直接 lit を切り替えるので、背面の電源ブロックは要らない。
 */
public class LampVideoMod implements ModInitializer {

	public static final String MOD_ID = "lampvideo";

	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** setBlock のフラグ。2 = クライアントに送るだけで、隣のブロックには通知しない(ランプが勝手に消えない)。 */
	private static final int FLAGS = 2;

	/** jar に同梱している動画の名前。 */
	private static final String BUNDLED = "sample";
	private static final String DEFAULT_NAME = BUNDLED;

	/** 自動変換の設定: ランプの横の数 / 各フレームを表示する tick 数。 */
	private static final int CONVERT_WIDTH = 360;
	private static final int CONVERT_HOLD = 3;

	private static final Pattern VIDEO_EXT = Pattern.compile("(?i).*\\.(mp4|mkv|webm|mov|avi|m4v|flv|wmv)$");

	private static final ExecutorService CONVERTER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "lampvideo-converter");
		t.setDaemon(true);
		return t;
	});
	private static final Set<String> converting = ConcurrentHashMap.newKeySet();

	private static ServerLevel level;
	/** flat: 足元のすぐ下のブロックの層(ランプ面)の北西の角。wall: 足元(左下の角)。 */
	private static BlockPos origin;
	private static int screenWidth;
	private static int screenHeight;
	private static boolean flat = true;

	private static LvfVideo video;
	private static int tickCounter;
	private static String currentName;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("lampvideo")
						.then(Commands.literal("start")
								.executes(ctx -> start(ctx, DEFAULT_NAME, true))
								.then(nameArgument()
										.executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "name"), true))
										.then(layoutArgument().executes(ctx -> start(ctx,
												StringArgumentType.getString(ctx, "name"), isFlat(ctx))))))
						.then(Commands.literal("setup")
								.then(nameArgument()
										.executes(ctx -> setup(ctx, StringArgumentType.getString(ctx, "name"), true))
										.then(layoutArgument().executes(ctx -> setup(ctx,
												StringArgumentType.getString(ctx, "name"), isFlat(ctx))))))
						.then(Commands.literal("play").executes(LampVideoMod::play))
						.then(Commands.literal("stop").executes(LampVideoMod::stop))
						.then(Commands.literal("remove").executes(LampVideoMod::remove))
						.then(Commands.literal("list").executes(LampVideoMod::list))
						.then(Commands.literal("convert").executes(LampVideoMod::convert))));

		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			prepareFolder();
			scan(server, null);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> reset());
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> nameArgument() {
		return Commands.argument("name", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(availableNames(), builder));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> layoutArgument() {
		return Commands.argument("layout", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("flat", "wall"), builder));
	}

	/** layout 引数: flat(水平、既定) か wall(縦)。それ以外は flat 扱い。 */
	private static boolean isFlat(CommandContext<CommandSourceStack> ctx) {
		return !"wall".equalsIgnoreCase(StringArgumentType.getString(ctx, "layout"));
	}

	// ---- フォルダと動画の名前 ----

	private static Path videoDir() {
		return FabricLoader.getInstance().getGameDir().resolve("lampvideo");
	}

	private static Path audioPackDir() {
		return FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve("lampvideo_audio");
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
						"変換が終わると、/lampvideo list に名前が出ます。/lampvideo start <名前> で再生できます。",
						"",
						"音声を鳴らすには、resourcepacks/lampvideo_audio を有効にして、F3+T でリソースを再読み込みしてください。",
						""), StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			LOGGER.warn("lampvideo フォルダを作れませんでした", e);
		}
	}

	/** 動画ファイル名から、コマンドや音声の ID に使える名前(小文字の英数字と _)を作る。 */
	static String safeName(String fileName) {
		String base = fileName.replaceFirst("\\.[^.]*$", "");
		String s = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		if (s.length() > 40) {
			s = s.substring(0, 40).replaceAll("_+$", "");
		}
		return s.isEmpty() ? "video_" + Integer.toHexString(base.hashCode()) : s;
	}

	/** 再生できる動画の名前(同梱 + 変換済み)。 */
	private static List<String> availableNames() {
		Set<String> names = new TreeSet<>();
		names.add(BUNDLED);
		try (Stream<Path> s = Files.list(videoDir())) {
			s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".lvf"))
					.forEach(n -> names.add(n.substring(0, n.length() - 4)));
		} catch (IOException e) {
			// フォルダがなければ同梱の動画だけ
		}
		return new ArrayList<>(names);
	}

	private static InputStream openVideoStream(String name) throws IOException {
		Path external = videoDir().resolve(name + ".lvf");
		if (Files.exists(external)) {
			return Files.newInputStream(external);
		}
		InputStream bundled = LampVideoMod.class.getResourceAsStream("/lampvideo/" + name + ".lvf");
		if (bundled == null) {
			throw new IOException("動画 " + name + " が見つかりません(/lampvideo list で確認できます)");
		}
		return bundled;
	}

	// ---- 自動変換 ----

	private static void notifyUser(MinecraftServer server, CommandSourceStack notify, String message) {
		LOGGER.info("[lampvideo] {}", message);
		if (notify != null) {
			server.execute(() -> notify.sendSuccess(() -> Component.literal("[ランプ動画] " + message), false));
		}
	}

	/** フォルダ内の未変換の動画を探して、順番に変換する(別スレッド)。 */
	private static void scan(MinecraftServer server, CommandSourceStack notify) {
		CONVERTER.execute(() -> {
			List<Path> todo = new ArrayList<>();
			try (Stream<Path> s = Files.list(videoDir())) {
				s.filter(Files::isRegularFile)
						.filter(p -> VIDEO_EXT.matcher(p.getFileName().toString()).matches())
						.sorted().forEach(p -> {
							String name = safeName(p.getFileName().toString());
							if (!Files.exists(videoDir().resolve(name + ".lvf")) && !converting.contains(name)) {
								todo.add(p);
							}
						});
			} catch (IOException e) {
				return;
			}
			if (todo.isEmpty()) {
				if (notify != null) {
					notifyUser(server, notify, "変換する動画はありません(" + videoDir() + " に動画を入れてください)");
				}
				return;
			}
			if (!VideoConverter.toolsAvailable()) {
				notifyUser(server, notify, todo.size() + " 個の動画を変換したいのですが、ffmpeg が見つかりません。"
						+ "ffmpeg をインストールして PATH を通してから、/lampvideo convert を実行してください");
				return;
			}
			for (Path file : todo) {
				convertOne(server, notify, file);
			}
		});
	}

	private static void convertOne(MinecraftServer server, CommandSourceStack notify, Path file) {
		String name = safeName(file.getFileName().toString());
		if (!converting.add(name)) {
			return;
		}
		try {
			notifyUser(server, notify, file.getFileName() + " を変換しています(名前: " + name + ")。時間がかかります…");
			VideoConverter.Info info = VideoConverter.probe(file);
			int frames = VideoConverter.convert(file, videoDir().resolve(name + ".lvf"), info,
					CONVERT_WIDTH, CONVERT_HOLD, 110, 24, 100, 0,
					n -> LOGGER.info("[lampvideo] {}: {} フレーム変換済み", name, n));
			String audioNote = "";
			if (info.hasAudio) {
				try {
					writeAudio(file, name, info);
					audioNote = " 音声を鳴らすには、リソースパック lampvideo_audio を有効にして F3+T で再読み込みしてください。";
				} catch (IOException e) {
					LOGGER.warn("音声の変換に失敗しました", e);
					audioNote = " (音声の変換には失敗しました)";
				}
			}
			notifyUser(server, notify, name + " の変換が終わりました(" + frames + " フレーム)。/lampvideo start " + name
					+ " で再生できます。" + audioNote);
		} catch (IOException | InterruptedException | RuntimeException e) {
			LOGGER.warn("変換に失敗しました: {}", file, e);
			notifyUser(server, notify, file.getFileName() + " の変換に失敗しました: " + e.getMessage());
		} finally {
			converting.remove(name);
		}
	}

	/** 音声を、リソースパック lampvideo_audio に追加する。 */
	private static void writeAudio(Path file, String name, VideoConverter.Info info)
			throws IOException, InterruptedException {
		Path pack = audioPackDir();
		Path sounds = pack.resolve("assets").resolve(MOD_ID).resolve("sounds");
		Files.createDirectories(sounds);
		VideoConverter.extractAudio(file, sounds.resolve(name + ".ogg"), info, CONVERT_HOLD);

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

	// ---- コマンド ----

	private static int convert(CommandContext<CommandSourceStack> ctx) {
		prepareFolder();
		scan(ctx.getSource().getServer(), ctx.getSource());
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		List<String> lines = new ArrayList<>();
		for (String name : availableNames()) {
			lines.add("  " + name + (name.equals(BUNDLED) ? "  (同梱)" : ""));
		}
		for (String name : converting) {
			lines.add("  " + name + "  (変換中…)");
		}
		source.sendSuccess(() -> Component.literal("[ランプ動画] 再生できる動画:\n" + String.join("\n", lines)
				+ "\n動画ファイルは " + videoDir() + " に入れてください(ffmpeg が必要)"), false);
		return 1;
	}

	/** 画面を作って、すぐ再生する。 */
	private static int start(CommandContext<CommandSourceStack> ctx, String name, boolean flatLayout) {
		if (setup(ctx, name, flatLayout) == 0) {
			return 0;
		}
		return play(ctx);
	}

	/** ランプ面の位置。画像の (x, y) は、x が左から、y が上から。 */
	private static BlockPos.MutableBlockPos lampAt(BlockPos.MutableBlockPos pos, int x, int y) {
		return flat
				? pos.set(origin.getX() + x, origin.getY(), origin.getZ() + y)
				: pos.set(origin.getX() + x, origin.getY() + (screenHeight - 1 - y), origin.getZ());
	}

	/** ランプ面の手前(見る側)の層。flat は真上、wall は +Z 側。 */
	private static BlockPos.MutableBlockPos frontAt(BlockPos.MutableBlockPos pos, int x, int y) {
		return flat
				? pos.set(origin.getX() + x, origin.getY() + 1, origin.getZ() + y)
				: pos.set(origin.getX() + x, origin.getY() + (screenHeight - 1 - y), origin.getZ() + 1);
	}

	/**
	 * 画面(ランプ+手前の光源ブロック)を作る。
	 * flat: 足元のすぐ下の層に、足元を中心にして水平に作る。wall: 足元を左下の角にして縦に作る。
	 */
	private static int setup(CommandContext<CommandSourceStack> ctx, String name, boolean flatLayout) {
		CommandSourceStack source = ctx.getSource();
		int w;
		int h;
		try (LvfVideo v = LvfVideo.open(openVideoStream(name))) {
			w = v.width;
			h = v.height;
		} catch (IOException e) {
			source.sendFailure(Component.literal("[ランプ動画] 読み込めませんでした: " + e.getMessage()));
			return 0;
		}
		Vec3 p = source.getPosition();
		BlockPos feet = BlockPos.containing(p.x, p.y, p.z);
		BlockPos base;
		if (flatLayout) {
			base = new BlockPos(feet.getX() - w / 2, feet.getY() - 1, feet.getZ() - h / 2);
		} else {
			base = feet;
			if (base.getY() + h - 1 > 319) {
				source.sendFailure(Component.literal("[ランプ動画] 高さ不足です。画面の高さが" + h
						+ "ブロックあるので、Y=" + (320 - h) + "以下の場所で実行してください(スーパーフラットの地面など)"));
				return 0;
			}
		}

		stopPlayback();
		clearScreen();
		level = source.getLevel();
		origin = base;
		screenWidth = w;
		screenHeight = h;
		flat = flatLayout;
		currentName = name;

		forceChunks(true);
		BlockState lampOff = lamp(false);
		BlockState light = Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 15);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				level.setBlock(lampAt(pos, x, y), lampOff, FLAGS);
				// 手前に明るさ15の光源を敷き、ランプ面の明るさを一定にする(明暗のムラを防ぐ)
				level.setBlock(frontAt(pos, x, y), light, FLAGS);
			}
		}
		final String size = w + "x" + h;
		final String how = flatLayout
				? "足元を中心に水平に作りました。上空(クリエイティブで上に飛ぶ)から真下を向いて、北が画面の上になる向きで見てください。"
				: "足元が左下の角です。南側から北を向いて見てください。";
		source.sendSuccess(() -> Component.literal("[ランプ動画] " + size + " の画面を設置しました。" + how
				+ " /lampvideo play で再生"), false);
		return 1;
	}

	private static int play(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if (level == null || currentName == null) {
			source.sendFailure(Component.literal("[ランプ動画] 先に /lampvideo start [名前] か /lampvideo setup <名前> を実行してください"));
			return 0;
		}
		stopPlayback();
		try {
			video = LvfVideo.open(openVideoStream(currentName));
		} catch (IOException e) {
			source.sendFailure(Component.literal("[ランプ動画] 読み込めませんでした: " + e.getMessage()));
			return 0;
		}
		tickCounter = 0;
		// 音声(リソースパックの lampvideo:<名前>)。各プレイヤーの位置で鳴らす
		runCommand(source, "execute as @a at @s run playsound " + MOD_ID + ":" + currentName + " master @s ~ ~ ~ 1 1");
		source.sendSuccess(() -> Component.literal("[ランプ動画] 再生を開始します"), false);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) {
		stopPlayback();
		stopSound(ctx.getSource());
		ctx.getSource().sendSuccess(() -> Component.literal("[ランプ動画] 停止しました"), false);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		stopPlayback();
		stopSound(ctx.getSource());
		clearScreen();
		ctx.getSource().sendSuccess(() -> Component.literal("[ランプ動画] 画面を片付けました"), false);
		return 1;
	}

	private static void stopSound(CommandSourceStack source) {
		if (currentName != null) {
			runCommand(source, "stopsound @a * " + MOD_ID + ":" + currentName);
		}
	}

	private static void runCommand(CommandSourceStack source, String command) {
		source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput(), command);
	}

	private static BlockState lamp(boolean lit) {
		return Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, lit);
	}

	// ---- 再生 ----

	/** 毎tick、表示間隔(hold)ごとに次のフレームの差分を反映する。 */
	private static void tick() {
		if (video == null || level == null) {
			return;
		}
		if (tickCounter++ % video.hold != 0) {
			return;
		}
		BlockState on = lamp(true);
		BlockState off = lamp(false);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		try {
			if (!video.readFrame((x, y, lit) -> level.setBlock(lampAt(pos, x, y), lit ? on : off, FLAGS))) {
				stopPlayback();
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
		if (level == null || origin == null) {
			return;
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < screenHeight; y++) {
			for (int x = 0; x < screenWidth; x++) {
				level.setBlock(lampAt(pos, x, y), air, FLAGS);
				level.setBlock(frontAt(pos, x, y), air, FLAGS);
			}
		}
		forceChunks(false);
		level = null;
		origin = null;
	}

	/** 画面の範囲のチャンクを常に読み込んでおく(読み込まれていないと書き込めない)。 */
	private static void forceChunks(boolean force) {
		int minX = origin.getX() >> 4;
		int maxX = (origin.getX() + screenWidth - 1) >> 4;
		int minZ = origin.getZ() >> 4;
		int maxZ = (origin.getZ() + (flat ? screenHeight - 1 : 1)) >> 4;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				level.setChunkForced(cx, cz, force);
			}
		}
	}

	private static void reset() {
		stopPlayback();
		level = null;
		origin = null;
	}
}
