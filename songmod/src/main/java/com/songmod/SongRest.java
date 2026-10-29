package com.songmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 休符。いま鳴っている場所(信号の先頭のリピーター)の前と後ろのリピーターを壊し、{@link #REST_TICKS} tick(1 秒)後に元へ戻す。
 *
 * <p>壊しただけだと信号が途切れて曲がそこで終わってしまうので、戻すときに後ろ側のリピーターへ信号をつなぎ直し、
 * 続きから流れるようにする(= 1 秒ぶんの休み)。
 */
public final class SongRest {

	/** 休む長さ(tick)。20 = 1 秒 */
	public static final int REST_TICKS = 20;

	private static final class Task {
		final ServerLevel level;
		final SongRegistry.Entry entry;
		final long due;
		final BlockPos before;
		final BlockState beforeState;
		final BlockPos after;
		final BlockState afterState;

		Task(ServerLevel level, SongRegistry.Entry entry, long due, BlockPos before, BlockState beforeState,
				BlockPos after, BlockState afterState) {
			this.level = level;
			this.entry = entry;
			this.due = due;
			this.before = before;
			this.beforeState = beforeState;
			this.after = after;
			this.afterState = afterState;
		}
	}

	private static final List<Task> TASKS = new ArrayList<>();

	private SongRest() {
	}

	/** その曲が休符のあいだか(追従の時計を止めるのに使う) */
	public static synchronized boolean resting(SongRegistry.Entry entry) {
		for (Task t : TASKS) {
			if (t.entry == entry) {
				return true;
			}
		}
		return false;
	}

	/** @return プレイヤーに見せるメッセージ */
	public static synchronized String use(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		SongRegistry.Playing playing = SongRegistry.playing(level, p.position());
		if (playing == null) {
			return "いま鳴っている曲がありません(休符は、曲が流れているときに使います)";
		}
		SongRegistry.Entry e = playing.entry();
		if (resting(e)) {
			return "いま休符の最中です";
		}
		int i = playing.index();
		BlockPos before = i - 1 >= 0 ? e.repeaters.get(i - 1) : null;
		BlockPos after = i + 1 < e.repeaters.size() ? e.repeaters.get(i + 1) : null;
		BlockState beforeState = take(level, before);
		BlockState afterState = take(level, after);
		if (beforeState == null && afterState == null) {
			return "前後にリピーターがありません";
		}
		TASKS.add(new Task(level, e, level.getGameTime() + REST_TICKS, beforeState == null ? null : before, beforeState,
				afterState == null ? null : after, afterState));
		return "休符: 前後のリピーターを壊しました(" + REST_TICKS / 20.0 + " 秒後に戻って、続きが流れます)";
	}

	/** リピーターなら、通電していない状態を覚えて壊す。壊せなければ null */
	private static BlockState take(ServerLevel level, BlockPos pos) {
		if (pos == null || !level.hasChunkAt(pos)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.REPEATER)) {
			return null;
		}
		level.destroyBlock(pos, false);
		return state.setValue(BlockStateProperties.POWERED, false);
	}

	public static synchronized void tick(MinecraftServer server) {
		if (TASKS.isEmpty()) {
			return;
		}
		TASKS.removeIf(t -> {
			if (t.level.getGameTime() < t.due) {
				return false;
			}
			if (t.before != null) {
				t.level.setBlock(t.before, t.beforeState, Block.UPDATE_ALL);
			}
			if (t.after != null) {
				t.level.setBlock(t.after, t.afterState, Block.UPDATE_ALL);
				// 後ろ側へ信号をつなぎ直す: 通電させて、遅延ぶん後に切れるようにする(続きの回路へ信号が流れる)
				t.level.setBlock(t.after, t.afterState.setValue(BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
				t.level.scheduleTick(t.after, Blocks.REPEATER, 2 * t.afterState.getValue(BlockStateProperties.DELAY));
			}
			return true;
		});
	}
}
