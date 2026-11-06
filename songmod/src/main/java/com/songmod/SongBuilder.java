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
	private final List<BlockPos> noteBlocks = new ArrayList<>();
	private final List<BlockPos> repeaters = new ArrayList<>();
	private final List<BlockPos> placedBlocks = new ArrayList<>();
	/** この回路を建てた元(SongRegistry.Entry.source) */
	public String source = "";
	private final List<Integer> repeaterTimes = new ArrayList<>();
	private final List<int[]> forcedChunks = new ArrayList<>();
	/** 始点のボタンの位置(押された = 曲が始まった、の検知に使う) */
	private BlockPos startButton;
	private int minX = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int minZ = Integer.MAX_VALUE;
	private int maxZ = Integer.MIN_VALUE;
	/** 完成後に SongRegistry へ登録した記録(やり直し用) */
	public SongRegistry.Entry entry;

	public SongBuilder(ServerLevel level, UUID owner, SongData data, BlockPos origin, Direction facing, int rowLen) {
		this.level = level;
		this.owner = owner;
		this.name = data.name;
		this.plan = SongPlanner.plan(data.notes, rowLen != 0 ? rowLen : data.rowLength);

		int fx = facing.getStepX();
		int fz = facing.getStepZ();
		int rx = -fz;
		int rz = fx;
		Map<String, BlockState> cache = new HashMap<>();

		int minU = SongPlanner.START_U;
		int maxU = 0;
		int minW = 0;
		int maxW = 0;
		for (SongPlanner.Cell c : plan.cells) {
			minU = Math.min(minU, c.u());
			maxU = Math.max(maxU, c.u());
			minW = Math.min(minW, c.w());
			maxW = Math.max(maxW, c.w());
		}
		// 1. 範囲を空気にする(地形や木が邪魔にならないように)
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int u = minU - 2; u <= maxU + 2; u++) {
			for (int w = minW - 2; w <= maxW + 2; w++) {
				for (int h = 0; h <= 2; h++) {
					add(origin, fx, fz, rx, rz, u, w, h, air);
				}
			}
		}
		// 2. 土台(下の段)→ 本体 → 上の音符
		for (int phase = 0; phase < 4; phase++) {
			for (SongPlanner.Cell c : plan.cells) {
				int p = c.h() + 2; // 下の段から順に(砂の下の支えを、砂より先に置く)
				if (p != phase) {
					continue;
				}
				BlockPos pos = add(origin, fx, fz, rx, rz, c.u(), c.w(), c.h(), stateOf(c, facing, cache));
				if (c.kind() == SongPlanner.Kind.SPINE_REPEATER) {
					// 幹のリピーターだけを、再生位置の追跡(追従・休符)に使う
					repeaters.add(pos);
					repeaterTimes.add(c.t());
				} else if (c.kind() == SongPlanner.Kind.BUTTON) {
					startButton = pos;
				}
			}
		}
	}

	private BlockPos add(BlockPos origin, int fx, int fz, int rx, int rz, int u, int w, int h, BlockState s) {
		BlockPos pos = origin.offset(fx * u + rx * w, h, fz * u + rz * w);
		positions.add(pos);
		states.add(s);
		if (!s.isAir()) {
			placedBlocks.add(pos);
		}
		if (s.is(Blocks.NOTE_BLOCK)) {
			noteBlocks.add(pos);
		}
		minX = Math.min(minX, pos.getX());
		maxX = Math.max(maxX, pos.getX());
		minZ = Math.min(minZ, pos.getZ());
		maxZ = Math.max(maxZ, pos.getZ());
		return pos;
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
				return block(c.inst() == null ? "minecraft:smooth_stone" : c.inst().color, cache);
			case NOTE_BASE:
				return block(c.inst().block, cache);
			case DUST: {
				// つながる向きのビット(1=+u 2=-u 4=+w 8=-w)を、実際の方位に直す
				StringBuilder sb = new StringBuilder("minecraft:redstone_wire[power=0");
				int[][] vec = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
				for (int i = 0; i < 4; i++) {
					String side = (c.a() & (1 << i)) != 0 ? "side" : "none";
					sb.append(',').append(world(facing, vec[i][0], vec[i][1]).getName()).append('=').append(side);
				}
				return block(sb.append(']').toString(), cache);
			}
			case REPEATER:
			case SPINE_REPEATER: {
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

	/** 回路の範囲のチャンクを常に読み込んでおく(離れても演奏が止まらないように)。もとから常時読み込みのチャンクは触らない */
	public void forceChunks() {
		for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
			for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
				if (level.setChunkForced(cx, cz, true)) {
					forcedChunks.add(new int[] { cx, cz });
				}
			}
		}
	}

	public void unforceChunks() {
		for (int[] c : forcedChunks) {
			level.setChunkForced(c[0], c[1], false);
		}
		forcedChunks.clear();
		if (entry != null) {
			SongRegistry.save();
		}
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
			entry = SongRegistry.add(level, noteBlocks, repeaters, repeaterTimes.stream().mapToInt(Integer::intValue).toArray(),
					startButton, source, flatten(placedBlocks), forcedChunks);
			var player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				player.sendSystemMessage(Component.literal("[曲] 「" + name + "」の回路ができました。始点のボタンを押すと演奏が始まります(どこにいても音が聞こえます)"));
			}
			return true;
		}
		return false;
	}

	private static int[] flatten(List<BlockPos> list) {
		int[] out = new int[list.size() * 3];
		for (int i = 0; i < list.size(); i++) {
			out[i * 3] = list.get(i).getX();
			out[i * 3 + 1] = list.get(i).getY();
			out[i * 3 + 2] = list.get(i).getZ();
		}
		return out;
	}

	/** 置いた分を元に戻す。@return 戻したブロック数 */
	public int undo() {
		SongRegistry.remove(entry);
		unforceChunks();
		int n = 0;
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), FLAGS);
			n++;
		}
		before.clear();
		return n;
	}
}
