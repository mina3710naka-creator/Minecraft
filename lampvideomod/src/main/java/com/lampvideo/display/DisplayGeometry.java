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

	/** 枠の太さ(ブロックに対する割合)。 */
	public static final float MX = 1.0f / 16.0f;
	/** 枠を含めた、モニターの高さ(幅いっぱいで、画面が 16:9 になる高さ)。 */
	public static final float FRAME_H = (1.0f - 2 * MX) * 9.0f / 16.0f + 2 * MX;
	/** スタンドの画面の下端(ブロックの下から)。この下に支柱と台がある。 */
	public static final float STAND_BOTTOM = 5.0f / 16.0f;

	private DisplayGeometry() {
	}

	/** 枠の太さ。枠なし(大型ディスプレイ)は 0。 */
	public static float margin(boolean frameless) {
		return frameless ? 0f : MX;
	}

	/** 画面の下に、何も置かない空間(ブロックに対する割合)。スタンドを含むグループは、スタンドの高さにそろえる。 */
	public static float bottomInset(boolean frameless, boolean hasStand) {
		return frameless ? 0f : hasStand ? STAND_BOTTOM : (1.0f - FRAME_H) / 2.0f;
	}

	/** 画面の上の空間。 */
	public static float topInset(boolean frameless, boolean hasStand) {
		return frameless ? 0f : hasStand ? 1.0f - (STAND_BOTTOM + FRAME_H) : (1.0f - FRAME_H) / 2.0f;
	}

	/** グループ全体で、映像が映る範囲(枠の内側)の大きさ {幅, 高さ}(ブロック単位)。 */
	public static float[] visibleSize(int cols, int rows, boolean frameless, boolean hasStand) {
		float m = margin(frameless);
		return new float[] { cols - 2 * m, rows - (bottomInset(frameless, hasStand) + m) - (topInset(frameless, hasStand) + m) };
	}

	/** グループ(左右 e[0],e[1]、上下 e[2],e[3] に広がる)の中に、スタンドがあるか。 */
	public static boolean groupHasStand(LevelReader level, BlockPos pos, int[] e) {
		BlockState s = level.getBlockState(pos);
		Direction f = facing(s);
		if (f == null) {
			return isStand(s);
		}
		Direction r = right(f, spin(s));
		Direction u = up(f, spin(s));
		for (int i = -e[0]; i <= e[1]; i++) {
			for (int j = -e[3]; j <= e[2]; j++) {
				if (isStand(level.getBlockState(pos.relative(r, i).relative(u, j)))) {
					return true;
				}
			}
		}
		return false;
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
