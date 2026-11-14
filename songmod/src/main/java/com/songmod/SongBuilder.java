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
	/** 建てる前に空気にする範囲: 回路の前後(マス)、左右(マス)、上(マス) */
	private static final int CLEAR_FRONT = 2;
	private static final int CLEAR_SIDE = 3;
	private static final int CLEAR_ABOVE = 6;
	private static final int CLEAR_PER_TICK = 20000;

	private final ServerLevel level;
	private final UUID owner;
	public final String name;
	public final SongPlanner.Plan plan;
	private final List<BlockPos> positions = new ArrayList<>();
	private final List<BlockState> states = new ArrayList<>();
	private final Map<BlockPos, BlockState> before = new HashMap<>();
	private int next;
	private BlockPos clearOrigin;
	private int clFx, clFz, clRx, clRz, clU0, clUn, clW0, clWn, clHn;
	private long clearTotal;
	private long clearNext;
	private final List<BlockPos> noteBlocks = new ArrayList<>();
	/** noteBlocks と同じ並びの、音符ごとの強さ(1〜100 %) */
	private final List<Integer> noteVels = new ArrayList<>();
	/** 全体の音量(%)。完成したあと、選曲台の画面で変えられる */
	public int volumePct = 100;
	private final List<BlockPos> repeaters = new ArrayList<>();
	private final List<BlockPos> placedBlocks = new ArrayList<>();
	/** 床の層(h < 0: 石の床・音符ブロックの土台・砂の支え)に置いたブロック。消すときは、元の地形に戻さず、草ブロックにする */
	private final java.util.Set<BlockPos> floorBlocks = new java.util.HashSet<>();
	/** 回路の床(h = 0)の高さ(y) */
	private final int groundY;
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
		this.groundY = origin.getY();

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
		// 1. 範囲を空気にする(地形や木が邪魔にならないように)。前後 2 マス、左右 3 マス、上は回路のてっぺんから 6 マス。
		// 数が多いので、置くときに少しずつ消す(tick() の最初)
		int maxH = 0;
		for (SongPlanner.Cell c : plan.cells) {
			maxH = Math.max(maxH, c.h());
		}
		clearOrigin = origin;
		clFx = fx;
		clFz = fz;
		clRx = rx;
		clRz = rz;
		clU0 = minU - CLEAR_FRONT;
		clUn = maxU + CLEAR_FRONT - clU0 + 1;
		clW0 = minW - CLEAR_SIDE;
		clWn = maxW + CLEAR_SIDE - clW0 + 1;
		clHn = maxH + CLEAR_ABOVE + 1;
		clearTotal = (long) clUn * clWn * clHn;
		for (int cu : new int[] { clU0, clU0 + clUn - 1 }) {
			for (int cw : new int[] { clW0, clW0 + clWn - 1 }) {
				BlockPos corner = origin.offset(fx * cu + rx * cw, 0, fz * cu + rz * cw);
				minX = Math.min(minX, corner.getX());
				maxX = Math.max(maxX, corner.getX());
				minZ = Math.min(minZ, corner.getZ());
				maxZ = Math.max(maxZ, corner.getZ());
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
				} else if (c.kind() == SongPlanner.Kind.NOTE) {
					noteVels.add(c.b()); // noteBlocks と同じ並び(add() が、音符ブロックを同じ順に集める)
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
			if (h < 0) {
				floorBlocks.add(pos);
			}
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
				return block("minecraft:smooth_stone", cache);
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
			case COVER:
				// 音符ブロックの真上の、見えないブロック。バニラの音を消す(上が空気のときだけ鳴る)
				return block("minecraft:light[level=0]", cache);
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
		if (clearNext < clearTotal) {
			BlockState air = Blocks.AIR.defaultBlockState();
			long end = Math.min(clearTotal, clearNext + CLEAR_PER_TICK);
			for (; clearNext < end; clearNext++) {
				long i = clearNext;
				int h = (int) (i % clHn);
				i /= clHn;
				int w = clW0 + (int) (i % clWn);
				int u = clU0 + (int) (i / clWn);
				BlockPos pos = clearOrigin.offset(clFx * u + clRx * w, h, clFz * u + clRz * w);
				BlockState old = level.getBlockState(pos);
				if (!old.isAir()) {
					before.putIfAbsent(pos, old);
					level.setBlock(pos, air, FLAGS);
				}
			}
			return false;
		}
		int end = Math.min(positions.size(), next + PER_TICK);
		for (; next < end; next++) {
			BlockPos pos = positions.get(next);
			before.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, states.get(next), FLAGS);
		}
		if (next >= positions.size()) {
			entry = SongRegistry.add(level, noteBlocks, repeaters, repeaterTimes.stream().mapToInt(Integer::intValue).toArray(),
					startButton, groundY, source, flatten(placedBlocks), forcedChunks,
					noteVels.stream().mapToInt(Integer::intValue).toArray(), volumePct);
			SongMod.LOGGER.info("SONGMOD built source={} notes={} start={}", source, noteBlocks.size(), startButton);
			var player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				player.sendSystemMessage(Component.literal("[曲] 「" + name + "」の回路ができました。始点のボタンを押すと演奏が始まります(音は、近いほど大きく、100 ブロックまで聞こえます)"));
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
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			// 床の層は、もとが何であっても草ブロックにする(それ以外は、もとの地形に戻す)
			level.setBlock(e.getKey(), floorBlocks.contains(e.getKey()) ? grass : e.getValue(), FLAGS);
			n++;
		}
		before.clear();
		return n;
	}
}
