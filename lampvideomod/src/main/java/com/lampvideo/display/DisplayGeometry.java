package com.lampvideo.display;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ディスプレイ系のブロック(パネル・大型パネル・スタンド)の向きと、連結(1枚の大画面になる関係)の計算。サーバーでもクライアントでも使う。
 * 画面の「上」は、壁なら真上、床・天井なら、置いたプレイヤーの向き(SPIN)。「右」は、見ている人から見て右。
 */
public final class DisplayGeometry {

	private DisplayGeometry() {
	}

	public static boolean isDisplay(BlockState s) {
		return s.getBlock() instanceof DisplayBlock || s.getBlock() instanceof StandDisplayBlock;
	}

	public static boolean isStand(BlockState s) {
		return s.getBlock() instanceof StandDisplayBlock;
	}

	public static boolean frameless(BlockState s) {
		return s.getBlock() instanceof LargeDisplayBlock;
	}

	/** 画面が向く方向。斜めに置いたスタンドは null(連結しない)。 */
	public static Direction facing(BlockState s) {
		if (s.getBlock() instanceof DisplayBlock) {
			return s.getValue(DisplayBlock.FACING);
		}
		if (s.getBlock() instanceof StandDisplayBlock) {
			return StandDisplayBlock.facingOf(s);
		}
		return null;
	}

	/** 床・天井のパネルの、画面の上の向き(水平)。それ以外は NORTH。 */
	public static Direction spin(BlockState s) {
		if (s.getBlock() instanceof DisplayBlock) {
			Direction f = s.getValue(DisplayBlock.FACING);
			if (f.getAxis() == Direction.Axis.Y) {
				return s.getValue(DisplayBlock.SPIN);
			}
		}
		return Direction.NORTH;
	}

	public static Direction up(Direction facing, Direction spin) {
		return facing.getAxis() == Direction.Axis.Y ? spin : Direction.UP;
	}

	/** 見ている人から見て右 = (-法線) x 上。 */
	public static Direction right(Direction facing, Direction spin) {
		Direction up = up(facing, spin);
		int nx = -facing.getStepX();
		int ny = -facing.getStepY();
		int nz = -facing.getStepZ();
		return Direction.getApproximateNearest(ny * up.getStepZ() - nz * up.getStepY(),
				nz * up.getStepX() - nx * up.getStepZ(), nx * up.getStepY() - ny * up.getStepX());
	}

	/** a から dir の向きに隣り合う b が、同じ画面につながるか。 */
	public static boolean canConnect(BlockState a, BlockState b, Direction dir) {
		Direction fa = facing(a);
		Direction fb = facing(b);
		if (fa == null || fb == null || fa != fb || spin(a) != spin(b) || frameless(a) != frameless(b)) {
			return false;
		}
		// スタンドの下側(台)には、つながらない
		if (isStand(a) && dir == Direction.DOWN) {
			return false;
		}
		return !(isStand(b) && dir == Direction.UP);
	}

	/** pos から dir の向きに、つながっているパネルが何枚続くか。 */
	public static int run(LevelReader level, BlockPos pos, Direction dir) {
		int n = 0;
		BlockPos p = pos;
		BlockState cur = level.getBlockState(p);
		while (n < 64) {
			BlockPos q = p.relative(dir);
			BlockState next = level.getBlockState(q);
			if (!canConnect(cur, next, dir)) {
				break;
			}
			n++;
			p = q;
			cur = next;
		}
		return n;
	}

	/** {左に何枚, 右に何枚, 上に何枚, 下に何枚}。連結しない(斜めのスタンドなど)なら全部 0。 */
	public static int[] extent(LevelReader level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		Direction f = facing(s);
		if (f == null) {
			return new int[4];
		}
		Direction r = right(f, spin(s));
		Direction u = up(f, spin(s));
		return new int[] { run(level, pos, r.getOpposite()), run(level, pos, r), run(level, pos, u), run(level, pos, u.getOpposite()) };
	}

	/** グループの親(左上のパネル)の位置。 */
	public static BlockPos primaryOf(LevelReader level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		Direction f = facing(s);
		if (f == null) {
			return pos;
		}
		int[] e = extent(level, pos);
		return pos.relative(right(f, spin(s)).getOpposite(), e[0]).relative(up(f, spin(s)), e[2]);
	}

	/** 親から見た、グループの大きさ {列, 行}。 */
	public static int[] sizeOf(LevelReader level, BlockPos primary) {
		BlockState s = level.getBlockState(primary);
		Direction f = facing(s);
		if (f == null) {
			return new int[] { 1, 1 };
		}
		return new int[] { 1 + run(level, primary, right(f, spin(s))), 1 + run(level, primary, up(f, spin(s)).getOpposite()) };
	}
}
