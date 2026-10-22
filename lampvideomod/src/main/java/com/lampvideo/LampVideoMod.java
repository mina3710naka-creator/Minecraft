package com.lampvideo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * 動画ファイル(.lvf)をレッドストーンランプの点灯/消灯で再生する。
 *
 * 画面は、コマンドを実行した位置を左下の角にして、+X(右)と +Y(上)に広がる。
 * 南側(+Z)に立って北を向くと正しい向きに見える。
 * ランプは neighbor update なし(フラグ2)で直接 lit を切り替えるので、背面の電源ブロックは要らない。
 */
public class LampVideoMod implements ModInitializer {

	public static final String MOD_ID = "lampvideo";

	/** setBlock のフラグ。2 = クライアントに送るだけで、隣のブロックには通知しない(ランプが勝手に消えない)。 */
	private static final int FLAGS = 2;

	private static ServerLevel level;
	private static BlockPos origin;
	private static int screenWidth;
	private static int screenHeight;

	private static LvfVideo video;
	private static int tickCounter;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("lampvideo")
						.then(Commands.literal("setup")
								.then(Commands.argument("name", StringArgumentType.word())
										.executes(ctx -> setup(ctx, StringArgumentType.getString(ctx, "name")))))
						.then(Commands.literal("play").executes(LampVideoMod::play))
						.then(Commands.literal("stop").executes(LampVideoMod::stop))
						.then(Commands.literal("remove").executes(LampVideoMod::remove))));

		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> reset());
	}

	private static Path videoPath(String name) {
		return FabricLoader.getInstance().getGameDir().resolve("lampvideo").resolve(name + ".lvf");
	}

	private static String lastName;

	/** 画面(ランプ+手前の光源ブロック)を、実行位置を左下にして作る。 */
	private static int setup(CommandContext<CommandSourceStack> ctx, String name) {
		CommandSourceStack source = ctx.getSource();
		Path path = videoPath(name);
		if (!Files.exists(path)) {
			source.sendFailure(Component.literal("[ランプ動画] " + path + " がありません。.lvf ファイルを置いてください"));
			return 0;
		}
		int w;
		int h;
		try (LvfVideo v = LvfVideo.open(path)) {
			w = v.width;
			h = v.height;
		} catch (IOException e) {
			source.sendFailure(Component.literal("[ランプ動画] 読み込めませんでした: " + e.getMessage()));
			return 0;
		}
		Vec3 p = source.getPosition();
		BlockPos base = BlockPos.containing(p.x, p.y, p.z);
		if (base.getY() + h - 1 > 319) {
			source.sendFailure(Component.literal("[ランプ動画] 高さ不足です。画面の高さが" + h
					+ "ブロックあるので、Y=" + (320 - h) + "以下の場所で実行してください"));
			return 0;
		}

		stopPlayback();
		clearScreen();
		level = source.getLevel();
		origin = base;
		screenWidth = w;
		screenHeight = h;
		lastName = name;

		forceChunks(true);
		BlockState lampOff = lamp(false);
		BlockState light = Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 15);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				level.setBlock(pos.set(origin.getX() + x, origin.getY() + y, origin.getZ()), lampOff, FLAGS);
				// 手前に明るさ15の光源を敷き、ランプ面の明るさを一定にする(明暗のムラを防ぐ)
				level.setBlock(pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + 1), light, FLAGS);
			}
		}
		final String size = w + "x" + h;
		source.sendSuccess(() -> Component.literal("[ランプ動画] " + size
				+ " の画面を設置しました。南側から北を向いて見てください。/lampvideo play で再生"), false);
		return 1;
	}

	private static int play(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		if (level == null || lastName == null) {
			source.sendFailure(Component.literal("[ランプ動画] 先に /lampvideo setup <名前> を実行してください"));
			return 0;
		}
		stopPlayback();
		try {
			video = LvfVideo.open(videoPath(lastName));
		} catch (IOException e) {
			source.sendFailure(Component.literal("[ランプ動画] 読み込めませんでした: " + e.getMessage()));
			return 0;
		}
		tickCounter = 0;
		// 音声(リソースパックの lampvideo:movie)。各プレイヤーの位置で鳴らす
		runCommand(source, "execute as @a at @s run playsound lampvideo:movie master @s ~ ~ ~ 1 1");
		source.sendSuccess(() -> Component.literal("[ランプ動画] 再生を開始します"), false);
		return 1;
	}

	private static int stop(CommandContext<CommandSourceStack> ctx) {
		stopPlayback();
		runCommand(ctx.getSource(), "stopsound @a * lampvideo:movie");
		ctx.getSource().sendSuccess(() -> Component.literal("[ランプ動画] 停止しました"), false);
		return 1;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		stopPlayback();
		runCommand(ctx.getSource(), "stopsound @a * lampvideo:movie");
		clearScreen();
		ctx.getSource().sendSuccess(() -> Component.literal("[ランプ動画] 画面を片付けました"), false);
		return 1;
	}

	private static void runCommand(CommandSourceStack source, String command) {
		source.getServer().getCommands().performPrefixedCommand(source.withSuppressedOutput(), command);
	}

	private static BlockState lamp(boolean lit) {
		return Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, lit);
	}

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
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();
		int top = screenHeight - 1;
		try {
			if (!video.readFrame((x, y, lit) ->
					level.setBlock(pos.set(ox + x, oy + (top - y), oz), lit ? on : off, FLAGS))) {
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
				for (int z = 0; z <= 1; z++) {
					level.setBlock(pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z), air, FLAGS);
				}
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
		int maxZ = (origin.getZ() + 1) >> 4;
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
