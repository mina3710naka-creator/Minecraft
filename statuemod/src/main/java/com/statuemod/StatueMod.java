package com.statuemod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 像づくり MOD。ゲームフォルダ(.minecraft)の statues/ フォルダにある JSON を読み込み、
 * /statue build &lt;名前&gt; で 1 層目から順に像を建てる。
 */
public class StatueMod implements ModInitializer {

	public static final String MOD_ID = "statuemod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final String PREFIX = "[像] ";
	private static final String[] DEFAULTS = { "creeper", "steve", "pig", "snowman", "slime", "enderman" };

	private static final List<StatueBuilder> JOBS = new ArrayList<>();
	/** プレイヤーごとの、直前に建てた像(やり直し用) */
	private static final Map<UUID, StatueBuilder> LAST = new ConcurrentHashMap<>();

	public static Path statuesDir() {
		return FabricLoader.getInstance().getGameDir().resolve("statues");
	}

	@Override
	public void onInitialize() {
		installDefaults();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (JOBS.isEmpty()) {
				return;
			}
			JOBS.removeIf(StatueBuilder::tick);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("statue")
						.then(Commands.literal("list").executes(StatueMod::list))
						.then(Commands.literal("reload").executes(StatueMod::reload))
						.then(Commands.literal("undo").executes(StatueMod::undo))
						.then(Commands.literal("stop").executes(StatueMod::stop))
						.then(Commands.literal("buildat")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(), b))
										.then(Commands.argument("pos", BlockPosArgument.blockPos())
												.then(Commands.argument("direction", StringArgumentType.word())
														.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
																List.of("north", "east", "south", "west"), b))
														.executes(StatueMod::buildAt)))))
						.then(Commands.literal("build")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(names(), b))
										.executes(ctx -> build(ctx, 6))
										.then(Commands.argument("ticksPerLayer", IntegerArgumentType.integer(0, 200))
												.executes(ctx -> build(ctx,
														IntegerArgumentType.getInteger(ctx, "ticksPerLayer"))))))));
		LOGGER.info("Character Statue: JSON folder = {}", statuesDir());
	}

	/** 初回起動時に statues/ フォルダを作り、同梱のサンプル像をコピーする(既にあるファイルは上書きしない) */
	private static void installDefaults() {
		Path dir = statuesDir();
		try {
			Files.createDirectories(dir);
			for (String n : DEFAULTS) {
				Path target = dir.resolve(n + ".json");
				if (Files.exists(target)) {
					continue;
				}
				try (InputStream in = StatueMod.class.getResourceAsStream("/statuemod/defaults/" + n + ".json")) {
					if (in != null) {
						Files.copy(in, target);
					}
				}
			}
		} catch (IOException e) {
			LOGGER.warn("statues フォルダを用意できません", e);
		}
	}

	private static List<String> names() {
		List<String> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(statuesDir())) {
			s.map(p -> p.getFileName().toString())
					.filter(n -> n.endsWith(".json"))
					.map(n -> n.substring(0, n.length() - 5))
					.filter(n -> n.matches("[A-Za-z0-9_.+-]+"))
					.sorted()
					.forEach(out::add);
		} catch (IOException ignored) {
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
			return fail(ctx, statuesDir() + " に .json ファイルがありません");
		}
		ok(ctx, "像の一覧(" + statuesDir() + "): " + String.join(", ", names));
		return names.size();
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		int okCount = 0;
		for (String n : names()) {
			try {
				StatueData.load(statuesDir().resolve(n + ".json"));
				okCount++;
			} catch (IOException e) {
				ctx.getSource().sendFailure(Component.literal(PREFIX + n + ".json: " + e.getMessage()));
			}
		}
		ok(ctx, okCount + " 個の像を確認しました(建てるときは毎回ファイルを読み直します)");
		return okCount;
	}

	private static int build(CommandContext<CommandSourceStack> ctx, int ticksPerLayer) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "name");
		Path file = statuesDir().resolve(name + ".json");
		if (!Files.isRegularFile(file)) {
			return fail(ctx, name + ".json が見つかりません(/statue list で一覧)");
		}
		StatueData data;
		try {
			data = StatueData.load(file);
		} catch (IOException e) {
			return fail(ctx, name + ".json: " + e.getMessage());
		}
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Direction facing = player.getDirection();
		BlockPos origin = player.blockPosition();
		StatueBuilder job;
		try {
			job = new StatueBuilder(ctx.getSource().getLevel(), player.getUUID(), data, origin, facing, ticksPerLayer);
		} catch (IllegalArgumentException e) {
			return fail(ctx, name + ".json: " + e.getMessage());
		}
		StatueBuilder prev = LAST.put(player.getUUID(), job);
		if (prev != null) {
			JOBS.remove(prev);
		}
		JOBS.add(job);
		ok(ctx, "「" + data.name + "」を建て始めます(" + data.width + "×" + data.depth + "×" + data.height()
				+ "、1 層ごとに " + ticksPerLayer + " tick)。やり直しは /statue undo");
		return 1;
	}

	/** 座標と向きを指定して建てる(コマンドブロック・コンソール用)。向きは「建てていく方向」。 */
	private static int buildAt(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		Direction facing = Direction.byName(StringArgumentType.getString(ctx, "direction"));
		if (facing == null || facing.getAxis().isVertical()) {
			return fail(ctx, "向きは north / east / south / west のどれかにしてください");
		}
		Path file = statuesDir().resolve(name + ".json");
		if (!Files.isRegularFile(file)) {
			return fail(ctx, name + ".json が見つかりません(/statue list で一覧)");
		}
		try {
			StatueData data = StatueData.load(file);
			BlockPos origin = BlockPosArgument.getBlockPos(ctx, "pos");
			StatueBuilder job = new StatueBuilder(ctx.getSource().getLevel(), null, data, origin, facing, 6);
			JOBS.add(job);
			ok(ctx, "「" + data.name + "」を建て始めます");
			return 1;
		} catch (IOException | IllegalArgumentException e) {
			return fail(ctx, name + ".json: " + e.getMessage());
		}
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		StatueBuilder job = LAST.get(ctx.getSource().getPlayerOrException().getUUID());
		if (job == null || !JOBS.remove(job)) {
			return fail(ctx, "建築中の像はありません");
		}
		ok(ctx, "建築を止めました(ここまでの分は /statue undo で戻せます)");
		return 1;
	}

	private static int undo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		StatueBuilder job = LAST.remove(ctx.getSource().getPlayerOrException().getUUID());
		if (job == null) {
			return fail(ctx, "やり直せる像がありません");
		}
		JOBS.remove(job);
		int n = job.undo();
		ok(ctx, n + " ブロックを元に戻しました");
		return n;
	}
}
