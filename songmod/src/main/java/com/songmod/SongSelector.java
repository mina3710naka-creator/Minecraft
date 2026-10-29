package com.songmod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 選曲台(SongSelectorBlock)の記録と動き。
 * <ul>
 * <li>台ごとに、選んでいる曲の名前を覚える(ゲームフォルダの songmod-selectors.json に保存)。</li>
 * <li>選んだ曲名は、台の手前に浮かぶテキスト(text_display)で見せる。</li>
 * <li>上のボタンが押されたら、台の後ろ側に選んだ曲の回路を建てる。</li>
 * </ul>
 */
public final class SongSelector {

	private static final class Entry {
		final String dim;
		final BlockPos pos;
		String song;
		/** 上のボタンが押されている最中か(1 回の押しで 1 回だけ建てるため) */
		boolean pressed;

		Entry(String dim, BlockPos pos, String song) {
			this.dim = dim;
			this.pos = pos;
			this.song = song;
		}
	}

	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
	private static int counter;

	private SongSelector() {
	}

	private static String key(String dim, BlockPos pos) {
		return dim + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
	}

	private static String tag(BlockPos pos) {
		return "songmod_sel_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
	}

	private static Path file() {
		return FabricLoader.getInstance().getGameDir().resolve("songmod-selectors.json");
	}

	public static synchronized void load() {
		ENTRIES.clear();
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			for (JsonElement el : JsonParser.parseReader(r).getAsJsonArray()) {
				JsonObject o = el.getAsJsonObject();
				BlockPos pos = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
				String dim = o.get("dim").getAsString();
				ENTRIES.put(key(dim, pos), new Entry(dim, pos, o.get("song").getAsString()));
			}
		} catch (IOException | RuntimeException e) {
			SongMod.LOGGER.warn("songmod-selectors.json を読めません", e);
		}
	}

	private static void save() {
		JsonArray all = new JsonArray();
		for (Entry e : ENTRIES.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("dim", e.dim);
			o.addProperty("x", e.pos.getX());
			o.addProperty("y", e.pos.getY());
			o.addProperty("z", e.pos.getZ());
			o.addProperty("song", e.song);
			all.add(o);
		}
		try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
			w.write(all.toString());
		} catch (IOException ex) {
			SongMod.LOGGER.warn("songmod-selectors.json を保存できません", ex);
		}
	}

	/** 台が置かれた: 最初の曲を選んで、上にボタンを付け、曲名を出す */
	public static synchronized void placed(ServerLevel level, BlockPos pos, BlockState state) {
		List<String> names = SongMod.names();
		Entry e = new Entry(level.dimension().toString(), pos, names.isEmpty() ? "" : names.get(0));
		ENTRIES.put(key(e.dim, pos), e);
		save();
		BlockPos above = pos.above();
		if (level.getBlockState(above).isAir()) {
			try {
				level.setBlock(above, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,
						"minecraft:stone_button[face=floor,facing=" + state.getValue(SongSelectorBlock.FACING).getName() + "]",
						false).blockState(), 3);
			} catch (Exception ex) {
				SongMod.LOGGER.warn("選曲台の上にボタンを置けません", ex);
			}
		}
		refresh(level, e, state);
	}

	private static Entry entryOf(ServerLevel level, BlockPos pos, BlockState state) {
		String k = key(level.dimension().toString(), pos);
		Entry e = ENTRIES.get(k);
		if (e == null) { // 記録がない台(古いデータなど)
			List<String> names = SongMod.names();
			e = new Entry(level.dimension().toString(), pos, names.isEmpty() ? "" : names.get(0));
			ENTRIES.put(k, e);
			save();
			refresh(level, e, state);
		}
		return e;
	}

	/** 右クリック: 次(delta=1)・前(delta=-1)の曲を選ぶ */
	public static synchronized void cycle(ServerLevel level, BlockPos pos, BlockState state, int delta, ServerPlayer player) {
		List<String> names = SongMod.names();
		if (names.isEmpty()) {
			player.sendSystemMessage(Component.literal("[曲] " + SongMod.songsDir() + " に曲(.json)がありません"));
			return;
		}
		Entry e = entryOf(level, pos, state);
		int idx = names.indexOf(e.song);
		int next = idx < 0 ? (delta > 0 ? 0 : names.size() - 1) : Math.floorMod(idx + delta, names.size());
		e.song = names.get(next);
		save();
		refresh(level, e, state);
		player.sendSystemMessage(Component.literal("[曲] 選んだ曲: " + e.song + " (" + (next + 1) + "/" + names.size()
				+ ")。上のボタンで建てます"));
	}

	/** 右クリック: 曲の一覧画面を開く */
	public static synchronized void openList(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		List<String> names = SongMod.names();
		Entry e = entryOf(level, pos, state);
		SongNetwork.open(player, pos, e.song, names);
	}

	/** 一覧画面で曲が選ばれた */
	public static synchronized void select(ServerPlayer player, BlockPos pos, String name) {
		if (!(player.level() instanceof ServerLevel level)
				|| player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.SONG_SELECTOR)) {
			return;
		}
		List<String> names = SongMod.names();
		if (!names.contains(name)) {
			player.sendSystemMessage(Component.literal("[曲] その曲が見つかりません: " + name));
			return;
		}
		Entry e = entryOf(level, pos, state);
		e.song = name;
		save();
		refresh(level, e, state);
		player.sendSystemMessage(Component.literal("[曲] 選んだ曲: " + name + "。上のボタンで建てます"));
	}

	/** 隣のブロックが変わった: 上のボタンが押されたら、選んだ曲を建てる */
	public static synchronized void neighbor(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState above = level.getBlockState(pos.above());
		boolean on = above.getBlock() instanceof ButtonBlock && above.getValue(ButtonBlock.POWERED);
		Entry e = entryOf(level, pos, state);
		if (!on) {
			e.pressed = false;
			return;
		}
		if (e.pressed) {
			return;
		}
		e.pressed = true;
		build(level, pos, state, e);
	}

	private static void build(ServerLevel level, BlockPos pos, BlockState state, Entry e) {
		Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24, false);
		ServerPlayer owner = nearest instanceof ServerPlayer sp ? sp : null;
		if (e.song.isEmpty()) {
			tell(owner, "曲が選ばれていません(台を右クリックで選びます)");
			return;
		}
		// 台の後ろ側(正面と反対)へ建てる。台と回路のあいだに 1 マスあける
		Direction back = state.getValue(SongSelectorBlock.FACING).getOpposite();
		BlockPos origin = pos.relative(back, 2);
		try {
			SongBuilder job = SongMod.startBuild(level, owner, e.song, origin, back, 0);
			tell(owner, SongMod.startMessage(job));
			if (job.plan.dropped > 0) {
				tell(owner, "置き場所が足りず " + job.plan.dropped + " 音を省きました(同時に 3 音までです)");
			}
		} catch (IOException ex) {
			tell(owner, ex.getMessage());
		}
	}

	private static void tell(ServerPlayer p, String msg) {
		if (p != null) {
			p.sendSystemMessage(Component.literal("[曲] " + msg));
		}
	}

	/** 台が壊されていたら、記録と曲名のテキストを消す(数秒おきに調べる) */
	public static synchronized void tick(MinecraftServer server) {
		if (ENTRIES.isEmpty() || ++counter % 100 != 0) {
			return;
		}
		boolean changed = false;
		List<Entry> gone = new ArrayList<>();
		for (Entry e : ENTRIES.values()) {
			ServerLevel level = levelOf(server, e.dim);
			if (level != null && level.hasChunkAt(e.pos) && !level.getBlockState(e.pos).is(ModBlocks.SONG_SELECTOR)) {
				run(level, "kill @e[type=minecraft:text_display,tag=" + tag(e.pos) + "]");
				gone.add(e);
			}
		}
		for (Entry e : gone) {
			ENTRIES.remove(key(e.dim, e.pos));
			changed = true;
		}
		if (changed) {
			save();
		}
	}

	private static ServerLevel levelOf(MinecraftServer server, String dim) {
		for (ServerLevel l : server.getAllLevels()) {
			if (l.dimension().toString().equals(dim)) {
				return l;
			}
		}
		return null;
	}

	private static void run(ServerLevel level, String command) {
		MinecraftServer server = level.getServer();
		CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, command);
	}

	private static String snbt(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
	}

	/** 台の手前(正面側)に、選んでいる曲名のテキストを出し直す */
	private static void refresh(ServerLevel level, Entry e, BlockState state) {
		String tag = tag(e.pos);
		run(level, "kill @e[type=minecraft:text_display,tag=" + tag + "]");
		List<String> names = SongMod.names();
		int idx = names.indexOf(e.song);
		String line = e.song.isEmpty() ? "(曲がありません)" : "♪ " + e.song;
		String sub = idx >= 0 ? "(" + (idx + 1) + "/" + names.size() + ")" : "";
		Direction front = state.getValue(SongSelectorBlock.FACING);
		double x = e.pos.getX() + 0.5 + front.getStepX() * 0.8;
		double y = e.pos.getY() + 0.7;
		double z = e.pos.getZ() + 0.5 + front.getStepZ() * 0.8;
		run(level, String.format(Locale.ROOT,
				"summon minecraft:text_display %.2f %.2f %.2f {Tags:[\"%s\"],billboard:\"vertical\",background:-1073741824,text:{text:%s}}",
				x, y, z, tag, snbt(line + (sub.isEmpty() ? "" : "\n" + sub))));
	}
}
