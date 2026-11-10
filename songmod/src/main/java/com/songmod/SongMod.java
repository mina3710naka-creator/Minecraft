package com.songmod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 曲づくり MOD。ゲームフォルダ(.minecraft)の songs/ フォルダにある音符 JSON を読み込み、
 * /song build &lt;名前&gt; でリピーターと音符ブロックの演奏回路を建てる。
 */
public class SongMod implements ModInitializer {

	public static final String MOD_ID = "songmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final String PREFIX = "[曲] ";
	private static final String[] DEFAULTS = { "twinkle" };

	private static final List<SongBuilder> JOBS = new ArrayList<>();
	/** プレイヤーごとの、直前に建てた曲(やり直し用) */
	private static final Map<UUID, SongBuilder> LAST = new ConcurrentHashMap<>();
	/** 建てた元(選曲台の位置・プレイヤー)ごとの、いちばん新しい回路。建て直すときに、これを先に消す */
	private static final Map<String, SongBuilder> SOURCES = new ConcurrentHashMap<>();

	public static Path songsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("songs");
	}

	@Override
	public void onInitialize() {
		installDefaults();
		SongNetwork.init();
		ModBlocks.init();
		ModItems.init();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SongRegistry.load();
			SongSelector.load();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!JOBS.isEmpty()) {
				JOBS.removeIf(SongBuilder::tick);
			}
			SongRegistry.tick(server);
			SongFollow.tick(server);
			SongRest.tick(server);
			SongSelector.tick(server);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("song")
						.then(Commands.literal("list").executes(SongMod::list))
						.then(Commands.literal("undo").executes(SongMod::undo))
						.then(Commands.literal("stop").executes(SongMod::stop))
						.then(Commands.literal("press").executes(SongMod::press))
						.then(Commands.literal("report").executes(ctx -> report(ctx, false))
								.then(Commands.literal("reset").executes(ctx -> report(ctx, true))))
						.then(Commands.literal("buildat")
								.then(Commands.argument("name", StringArgumentType.string())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(quoted(names()), b))
										.then(Commands.argument("pos", BlockPosArgument.blockPos())
												.then(Commands.argument("direction", StringArgumentType.word())
														.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
																List.of("north", "east", "south", "west"), b))
														.executes(SongMod::buildAt)))))
						.then(Commands.literal("item").executes(SongMod::giveItem))
						.then(Commands.literal("unforce").executes(SongMod::unforce))
						.then(Commands.literal("build")
								.then(Commands.argument("name", StringArgumentType.string())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(quoted(names()), b))
										.executes(ctx -> build(ctx, 0))
										.then(Commands.literal("line").executes(ctx -> build(ctx, -1)))
										.then(Commands.argument("rowLength", IntegerArgumentType.integer(11, 100000))
												.executes(ctx -> build(ctx, IntegerArgumentType.getInteger(ctx, "rowLength"))))))));
		LOGGER.info("Song Builder: JSON folder = {}", songsDir());
	}

	/** 初回起動時に songs/ フォルダを作り、同梱のサンプル曲をコピーする(既にあるファイルは上書きしない) */
	private static void installDefaults() {
		Path dir = songsDir();
		try {
			Files.createDirectories(dir);
			for (String n : DEFAULTS) {
				Path target = dir.resolve(n + ".json");
				if (Files.exists(target)) {
					continue;
				}
				try (InputStream in = SongMod.class.getResourceAsStream("/songmod/defaults/" + n + ".json")) {
					if (in != null) {
						Files.copy(in, target);
					}
				}
			}
		} catch (IOException e) {
			LOGGER.warn("songs フォルダを用意できません", e);
		}
	}

	static List<String> names() {
		List<String> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(songsDir())) {
			s.map(p -> p.getFileName().toString())
					.filter(n -> n.endsWith(".json"))
					.map(n -> n.substring(0, n.length() - 5))
					.filter(n -> !n.isEmpty() && n.indexOf('"') < 0 && n.indexOf('\\') < 0)
					.sorted()
					.forEach(out::add);
		} catch (IOException ignored) {
		}
		return out;
	}

	/** 英数字以外(日本語・スペースなど)を含む名前は、コマンドでは "…" で囲んで指定する */
	private static List<String> quoted(List<String> names) {
		List<String> out = new ArrayList<>();
		for (String n : names) {
			out.add(n.matches("[A-Za-z0-9_.+-]+") ? n : "\"" + n + "\"");
		}
		return out;
	}

	private static void ok(CommandContext<CommandSourceStack> ctx, String msg) {
		ctx.getSource().sendSuccess(() -> Component.literal(PREFIX + msg), false);
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
		ctx.getSource().sendFailure(Component.literal(PREFIX + msg));
		return 0;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		List<String> names = names();
		if (names.isEmpty()) {
			return fail(ctx, songsDir() + " に .json ファイルがありません");
		}
		ok(ctx, "曲の一覧(" + songsDir() + "): " + String.join(", ", names));
		return names.size();
	}

	/**
	 * 曲を建て始める(コマンドと選曲台で共通)。
	 * @param owner 建てた人(やり直し・完成の知らせの宛先)。いなければ null
	 * @throws IOException 曲が見つからない・読めない、ブロックが読めないとき(メッセージが理由)
	 */
	static SongBuilder startBuild(ServerLevel level, ServerPlayer owner, String source, String name, BlockPos origin, Direction facing,
			int rowLen) throws IOException {
		return startBuild(level, owner, source, name, origin, facing, rowLen, 100, 0, "", 100);
	}

	/** speedPct = 速度(%)、transpose = 移調(半音)、inst = 音程のある音をすべてこの楽器にする(空なら自動) */
	static SongBuilder startBuild(ServerLevel level, ServerPlayer owner, String source, String name, BlockPos origin, Direction facing,
			int rowLen, int speedPct, int transpose, String inst, int volumePct) throws IOException {
		if (name.contains("/") || name.contains("\\") || name.contains("..")) {
			throw new IOException("曲の名前に / \\ .. は使えません");
		}
		Path file = songsDir().resolve(name + ".json");
		if (!Files.isRegularFile(file)) {
			throw new IOException(name + ".json が見つかりません(/song list で一覧)");
		}
		SongData data;
		try {
			data = SongData.load(file);
		} catch (IOException e) {
			throw new IOException(name + ".json: " + e.getMessage());
		}
		if (speedPct != 100 || transpose != 0 || !inst.isEmpty()) {
			data = data.transformed(speedPct / 100.0, transpose, inst.isEmpty() ? null : Inst.byName(inst));
		}
		eraseSource(level, source); // 前に作った回路は消す
		SongBuilder job;
		try {
			job = new SongBuilder(level, owner == null ? null : owner.getUUID(), data, origin, facing, rowLen);
		} catch (IllegalArgumentException e) {
			throw new IOException(name + ".json: " + e.getMessage());
		}
		job.source = source;
		job.volumePct = volumePct;
		if (owner != null) {
			LAST.put(owner.getUUID(), job);
		}
		job.forceChunks();
		JOBS.add(job);
		SOURCES.put(source, job);
		return job;
	}

	/** 回路を消す(建築中なら止める)。同じセッション内なら元の地形まで戻し、そうでなければ空気にする。@return 消したブロック数 */
	static int eraseSource(ServerLevel level, String source) {
		SongBuilder prev = SOURCES.remove(source);
		if (prev != null) {
			return erase(prev);
		}
		return SongRegistry.eraseBySource(level.getServer(), source);
	}

	private static int erase(SongBuilder job) {
		JOBS.remove(job);
		SOURCES.values().remove(job);
		LAST.values().remove(job);
		return job.undo();
	}

	/** 建て始めの知らせ */
	static String startMessage(SongBuilder job) {
		SongPlanner.Plan p = job.plan;
		return "「" + job.name + "」を建て始めます(" + p.placed + " 音、長さ約 " + (p.lengthTicks / 10) + " 秒、"
				+ (p.rows == 1 ? "折り返しなしの 1 列 " + p.rowLen + " マス" : "1 列 " + p.rowLen + " マス × " + p.rows + " 列")
				+ ")。範囲のチャンクは常に読み込みます。やり直しは /song undo";
	}

	private static int build(CommandContext<CommandSourceStack> ctx, int rowLen) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "name");
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		SongBuilder job;
		try {
			job = startBuild(ctx.getSource().getLevel(), player, "player:" + player.getUUID(), name, player.blockPosition(),
					player.getDirection(), rowLen);
		} catch (IOException e) {
			return fail(ctx, e.getMessage());
		}
		ok(ctx, startMessage(job));
		if (job.plan.dropped > 0) {
			ctx.getSource().sendFailure(Component.literal(PREFIX + "1 区間(0.4 秒)に置ける枝の数をこえて " + job.plan.dropped
					+ " 音を省きました"));
		}
		return 1;
	}

	/** 座標と向きを指定して建てる(コンソール・コマンドブロック用。動作の検証にも使う)。向きは「建てていく方向」。 */
	private static int buildAt(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		Direction facing = Direction.byName(StringArgumentType.getString(ctx, "direction"));
		if (facing == null || facing.getAxis().isVertical()) {
			return fail(ctx, "向きは north / east / south / west のどれかにしてください");
		}
		try {
			BlockPos origin = BlockPosArgument.getBlockPos(ctx, "pos");
			SongBuilder job = startBuild(ctx.getSource().getLevel(), null, "at:" + origin.getX() + "," + origin.getY() + "," + origin.getZ(),
					name, origin, facing, -1);
			ok(ctx, startMessage(job));
			return 1;
		} catch (IOException | CommandSyntaxException e) {
			return fail(ctx, e.getMessage());
		}
	}

	/** いちばん新しく完成した回路の、始点のボタンを押す(動作の検証用) */
	private static int press(CommandContext<CommandSourceStack> ctx) {
		SongRegistry.Entry e = SongRegistry.last();
		if (e == null || e.start == null) {
			return fail(ctx, "完成している回路がありません");
		}
		ServerLevel level = SongRegistry.levelOf(ctx.getSource().getServer(), e.dim);
		BlockState state = level == null ? null : level.getBlockState(e.start);
		if (state == null || !(state.getBlock() instanceof ButtonBlock button)) {
			return fail(ctx, "始点のボタンがありません");
		}
		button.press(state, level, e.start, null);
		ok(ctx, "始点のボタンを押しました: " + e.source);
		LOGGER.info("SONGMOD pressed source={} tick={}", e.source, level.getGameTime());
		return 1;
	}

	/** 自己診断: 音符ブロックが、何個鳴ったか */
	private static int report(CommandContext<CommandSourceStack> ctx, boolean reset) {
		for (String line : SongRegistry.report(reset)) {
			ok(ctx, line);
			LOGGER.info("SONGMOD report {}", line);
		}
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		SongBuilder job = LAST.get(ctx.getSource().getPlayerOrException().getUUID());
		if (job == null || !JOBS.remove(job)) {
			return fail(ctx, "建築中の曲はありません");
		}
		job.unforceChunks();
		ok(ctx, "建築を止めました(ここまでの分は /song undo で戻せます)");
		return 1;
	}

	/** 回路は残したまま、チャンクの常時読み込みだけやめる(演奏するときは /song build し直すか、/forceload で読み込む) */
	private static int unforce(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		SongBuilder job = LAST.get(ctx.getSource().getPlayerOrException().getUUID());
		if (job == null) {
			return fail(ctx, "直前に建てた曲がありません");
		}
		job.unforceChunks();
		ok(ctx, "チャンクの常時読み込みをやめました");
		return 1;
	}

	private static int giveItem(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		player.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.FOLLOWER));
		player.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.REST));
		player.getInventory().add(new net.minecraft.world.item.ItemStack(ModBlocks.SONG_SELECTOR_ITEM));
		ok(ctx, "「曲の追従」(右クリックで ON / OFF)と「休符」(右クリックで 1 秒の休み)を渡しました");
		return 1;
	}

	private static int undo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		SongBuilder job = LAST.remove(ctx.getSource().getPlayerOrException().getUUID());
		if (job == null) {
			return fail(ctx, "やり直せる曲がありません");
		}
		int n = erase(job);
		ok(ctx, n + " ブロックを元に戻しました");
		return n;
	}
}
