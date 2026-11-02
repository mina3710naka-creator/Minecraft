package com.statuemod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * たいまつの上のコンクリートパウダーを落として、文字 → 画像をつくる作業。
 *
 * <ol>
 * <li>渦巻きの外側から 1 個飛ばしで(偶数番 → 奇数番)、たいまつを壊す → 上のパウダー(文字)が 1 ブロック落ちて、文字ができる</li>
 * <li>しばらく待ってから、同じ順で、画像のコンクリートパウダーを上から落とす → 文字の上に積もって、画像ができる</li>
 * </ol>
 * 構造は /statue build で建てた「1 層目: たいまつ、2 層目: 文字のパウダー」。画像は <名前>.drop.json。
 */
public final class DropJob {

	private static final int WAIT_BETWEEN_PHASES = 60;

	private final ServerLevel level;
	private final UUID owner;
	private final BlockPos origin;
	private final int dx, dz, rx, rz, half;
	private final int perTick;
	private final int dropHeight;
	/** 渦巻きの外側から、1 個飛ばしで並べた (行, 列) */
	private final List<int[]> order = new ArrayList<>();
	private final boolean[][] hasTorch;
	private final BlockState[][] powder;

	private int phase;      // 0: たいまつを壊す, 1: 待つ, 2: 画像を落とす
	private int index;
	private int wait;

	public DropJob(ServerLevel level, UUID owner, StatueBuilder built, JsonObject drop, int perTick, int dropHeight) {
		this.level = level;
		this.owner = owner;
		this.perTick = perTick;
		this.dropHeight = dropHeight;
		StatueData data = built.data();
		this.origin = built.origin();
		Direction facing = built.facing();
		this.dx = facing.getStepX();
		this.dz = facing.getStepZ();
		this.rx = -dz;
		this.rz = dx;
		this.half = (data.width - 1) / 2;

		// 1 層目のたいまつの位置
		Character torchKey = null;
		for (Map.Entry<Character, String> e : data.palette.entrySet()) {
			if (e.getValue().equals("minecraft:torch")) {
				torchKey = e.getKey();
			}
		}
		if (torchKey == null) {
			throw new IllegalArgumentException("1 層目にたいまつ(minecraft:torch)がありません");
		}
		List<String> torchRows = data.layers.get(0);
		int rows = torchRows.size();
		int cols = data.width;
		hasTorch = new boolean[rows][cols];
		for (int r = 0; r < rows; r++) {
			String line = torchRows.get(r);
			for (int c = 0; c < line.length(); c++) {
				hasTorch[r][c] = line.charAt(c) == torchKey;
			}
		}

		// 画像のパウダー
		Map<String, BlockState> cache = new HashMap<>();
		JsonObject pal = drop.getAsJsonObject("palette");
		Map<Character, BlockState> palette = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : pal.entrySet()) {
			palette.put(e.getKey().charAt(0), cache.computeIfAbsent(e.getValue().getAsString(), StatueBuilder::parseState));
		}
		JsonArray rowsJson = drop.getAsJsonArray("rows");
		powder = new BlockState[rows][cols];
		for (int r = 0; r < rows && r < rowsJson.size(); r++) {
			String line = rowsJson.get(r).getAsString();
			for (int c = 0; c < line.length() && c < cols; c++) {
				powder[r][c] = palette.get(line.charAt(c));
			}
		}

		// 渦巻き(外側 → 内側)を、1 個飛ばしにする: 偶数番を先に、奇数番をあとに
		List<int[]> spiral = spiral(rows, cols);
		for (int i = 0; i < spiral.size(); i += 2) {
			order.add(spiral.get(i));
		}
		for (int i = 1; i < spiral.size(); i += 2) {
			order.add(spiral.get(i));
		}
	}

	/** 行×列の長方形を、外側から内側へ渦巻きでたどる */
	static List<int[]> spiral(int rows, int cols) {
		List<int[]> out = new ArrayList<>(rows * cols);
		int top = 0, bottom = rows - 1, left = 0, right = cols - 1;
		while (top <= bottom && left <= right) {
			for (int c = left; c <= right; c++) {
				out.add(new int[] { top, c });
			}
			top++;
			for (int r = top; r <= bottom; r++) {
				out.add(new int[] { r, right });
			}
			right--;
			if (top <= bottom) {
				for (int c = right; c >= left; c--) {
					out.add(new int[] { bottom, c });
				}
				bottom--;
			}
			if (left <= right) {
				for (int r = bottom; r >= top; r--) {
					out.add(new int[] { r, left });
				}
				left++;
			}
		}
		return out;
	}

	private BlockPos pos(int row, int col, int y) {
		int fwd = row + 1;
		int side = col - half;
		return origin.offset(dx * fwd + rx * side, y, dz * fwd + rz * side);
	}

	private void say(String msg) {
		var player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		if (player != null) {
			player.sendSystemMessage(Component.literal("[像] " + msg));
		}
	}

	/** @return 完了したら true */
	public boolean tick() {
		switch (phase) {
			case 0 -> {
				int n = 0;
				while (index < order.size() && n < perTick) {
					int[] cell = order.get(index++);
					if (!hasTorch[cell[0]][cell[1]]) {
						continue;
					}
					BlockPos p = pos(cell[0], cell[1], 0);
					if (level.getBlockState(p).is(Blocks.TORCH)) {
						// 周りに知らせる(UPDATE_ALL)ので、上のコンクリートパウダーが落ちる。アイテムは落とさない
						level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
						n++;
					}
				}
				if (index >= order.size()) {
					say("たいまつを壊し終わりました。文字ができます");
					phase = 1;
					wait = WAIT_BETWEEN_PHASES;
				}
			}
			case 1 -> {
				if (--wait <= 0) {
					phase = 2;
					index = 0;
					say("画像のコンクリートパウダーを落とします");
				}
			}
			default -> {
				int n = 0;
				while (index < order.size() && n < perTick) {
					int[] cell = order.get(index++);
					BlockState st = powder[cell[0]][cell[1]];
					if (st == null) {
						continue;
					}
					// 文字の層(origin の高さ)の 1 つ上に積もるように、dropHeight 上から落とす
					BlockPos start = pos(cell[0], cell[1], 1 + dropHeight);
					FallingBlockEntity.fall(level, start, st);
					n++;
				}
				if (index >= order.size()) {
					say("できました! 画像のコンクリートパウダーがすべて落ちるまで、少し待ってください");
					return true;
				}
			}
		}
		return false;
	}
}
