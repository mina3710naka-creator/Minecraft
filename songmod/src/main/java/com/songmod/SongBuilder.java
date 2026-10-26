package com.songmod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 曲の回路を建てる作業。プレイヤーの 1 ブロック先から、向いている方向(前)と右へ広がる。
 * まず範囲を空気にして、土台 → 本体 → 音符の順に毎 tick 一定数ずつ置く。
 */
public final class SongBuilder {

	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final int PER_TICK = 4000;

	private final ServerLevel level;
	private final UUID owner;
	public final String name;
	public final SongPlanner.Plan plan;
	private final List<BlockPos> positions = new ArrayList<>();
	private final List<BlockState> states = new ArrayList<>();
	private final Map<BlockPos, BlockState> before = new HashMap<>();
	private int next;

	public SongBuilder(ServerLevel level, UUID owner, SongData data, BlockPos origin, Direction facing, int rowLen) {
		this.level = level;
		this.owner = owner;
		this.name = data.name;
		this.plan = SongPlanner.plan(data.notes, rowLen);

		int fx = facing.getStepX();
		int fz = facing.getStepZ();
		int rx = -fz;
		int rz = fx;
		Map<String, BlockState> cache = new HashMap<>();

		int maxU = 0;
		int maxW = 0;
		for (SongPlanner.Cell c : plan.cells) {
			maxU = Math.max(maxU, c.u());
			maxW = Math.max(maxW, c.w());
		}
		// 1. 範囲を空気にする(地形や木が邪魔にならないように)
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int u = SongPlanner.START_U - 2; u <= maxU + 2; u++) {
			for (int w = -2; w <= maxW + 2; w++) {
				for (int h = 0; h <= 2; h++) {
					add(origin, fx, fz, rx, rz, u, w, h, air);
				}
			}
		}
		// 2. 土台(下の段)→ 本体 → 上の音符
		for (int phase = 0; phase < 3; phase++) {
			for (SongPlanner.Cell c : plan.cells) {
				int p = c.h() < 0 ? 0 : (c.h() == 0 ? 1 : 2);
				if (p != phase) {
					continue;
				}
				add(origin, fx, fz, rx, rz, c.u(), c.w(), c.h(), stateOf(c, facing, cache));
			}
		}
	}

	private void add(BlockPos origin, int fx, int fz, int rx, int rz, int u, int w, int h, BlockState s) {
		positions.add(origin.offset(fx * u + rx * w, h, fz * u + rz * w));
		states.add(s);
	}

	private static BlockState block(String id, Map<String, BlockState> cache) {
		return cache.computeIfAbsent(id, k -> {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, k, false).blockState();
			} catch (Exception e) {
				throw new IllegalArgumentException("ブロック \"" + k + "\" を読めません");
			}
		});
	}

	private BlockState stateOf(SongPlanner.Cell c, Direction facing, Map<String, BlockState> cache) {
		switch (c.kind()) {
			case SOLID:
				return block(c.inst().block, cache);
			case SUPPORT:
				return block("minecraft:smooth_stone", cache);
			case NOTE_BASE:
				return block(c.inst().block, cache);
			case REPEATER: {
				Direction flow = world(facing, c.a(), c.b());
				// リピーターの向きは「入力側」なので、信号が流れる向きの反対
				return block("minecraft:repeater[facing=" + flow.getOpposite().getName() + ",delay=" + c.c() + "]", cache);
			}
			case BUTTON:
				return block("minecraft:stone_button[face=wall,facing=" + facing.getOpposite().getName() + "]", cache);
			case NOTE:
				return block("minecraft:note_block[note=" + c.a() + ",instrument=" + c.inst().mcName.toLowerCase() + "]", cache);
			default:
				return Blocks.AIR.defaultBlockState();
		}
	}

	/** 計画上の向き (du, dw) を、実際の水平方向にする */
	private static Direction world(Direction facing, int du, int dw) {
		if (du > 0) {
			return facing;
		}
		if (du < 0) {
			return facing.getOpposite();
		}
		return dw > 0 ? facing.getClockWise() : facing.getCounterClockWise();
	}

	/** @return 完了したら true */
	public boolean tick() {
		int end = Math.min(positions.size(), next + PER_TICK);
		for (; next < end; next++) {
			BlockPos pos = positions.get(next);
			before.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, states.get(next), FLAGS);
		}
		if (next >= positions.size()) {
			var player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				player.sendSystemMessage(Component.literal("[曲] 「" + name + "」の回路ができました。始点のボタンを押すと演奏が始まります"));
			}
			return true;
		}
		return false;
	}

	/** 置いた分を元に戻す。@return 戻したブロック数 */
	public int undo() {
		int n = 0;
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), FLAGS);
			n++;
		}
		before.clear();
		return n;
	}
}
