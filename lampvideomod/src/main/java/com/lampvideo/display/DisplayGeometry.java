package com.lampvideo.display;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ディスプレイ系のブロック(パネル・大型パネル・スタンド)の向きと、連結(1枚の大画面になる関係)の計算。サーバーでもクライアントでも使う。
 *
 * 向き: 壁に貼るもの・スタンドは、水平の向き yaw(0〜7。0=南向き、2=西向き、4=北向き、6=東向き。奇数は斜め)。
 * 床・天井に貼るパネルは、FACING(上/下)と、画面の上の向き SPIN(置いたプレイヤーの向き)。
 * 隣り合う画面は、「右」「上」の向きに(ブロックの位置のずれで)つながる。斜めの画面では、右は斜め(x と z が 1 ずつ)のずれになる。
 */
public final class DisplayGeometry {

	/** 枠の太さ(ブロックに対する割合)。 */
	public static final float MX = 1.0f / 16.0f;
	/** 枠を含めた、モニターの高さ(幅いっぱいで、画面が 16:9 になる高さ)。 */
	public static final float FRAME_H = (1.0f - 2 * MX) * 9.0f / 16.0f + 2 * MX;
	/** スタンドの画面の下端(ブロックの下から)。この下に支柱と台がある。 */
	public static final float STAND_BOTTOM = 5.0f / 16.0f;

	private static final Vec3i[] ADJACENT = {
			new Vec3i(1, 0, 0), new Vec3i(-1, 0, 0), new Vec3i(0, 1, 0), new Vec3i(0, -1, 0), new Vec3i(0, 0, 1), new Vec3i(0, 0, -1),
			new Vec3i(1, 0, 1), new Vec3i(1, 0, -1), new Vec3i(-1, 0, 1), new Vec3i(-1, 0, -1) };

	private DisplayGeometry() {
	}

	// ---- 画面の寸法 ----

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

	/** グループ全体で、映像が映る範囲(枠の内側)の大きさ {幅, 高さ}(ブロック単位)。斜めの画面は、幅が √2 倍(別に掛ける)。 */
	public static float[] visibleSize(int cols, int rows, boolean frameless, boolean hasStand) {
		float m = margin(frameless);
		return new float[] { cols - 2 * m, rows - (bottomInset(frameless, hasStand) + m) - (topInset(frameless, hasStand) + m) };
	}

	// ---- 種類・向き ----

	public static boolean isDisplay(BlockState s) {
		return s.getBlock() instanceof DisplayBlock || s.getBlock() instanceof StandDisplayBlock;
	}

	public static boolean isStand(BlockState s) {
		return s.getBlock() instanceof StandDisplayBlock;
	}

	public static boolean frameless(BlockState s) {
		return s.getBlock() instanceof LargeDisplayBlock;
	}

	/** 水平の向きから、yaw(0=南、2=西、4=北、6=東)。 */
	public static int cardinalYaw(Direction d) {
		return switch (d) {
			case SOUTH -> 0;
			case WEST -> 2;
			case NORTH -> 4;
			case EAST -> 6;
			default -> 4;
		};
	}

	/** yaw(偶数)から、水平の向き。 */
	public static Direction cardinalOf(int yaw) {
		return switch (yaw & 6) {
			case 0 -> Direction.SOUTH;
			case 2 -> Direction.WEST;
			case 6 -> Direction.EAST;
			default -> Direction.NORTH;
		};
	}

	/** 水平な向きの画面の yaw(0〜7)。床・天井のパネルや、ディスプレイでないものは -1。 */
	public static int yaw(BlockState s) {
		if (s.getBlock() instanceof DisplayBlock) {
			Direction f = s.getValue(DisplayBlock.FACING);
			if (f.getAxis() == Direction.Axis.Y) {
				return -1;
			}
			return (cardinalYaw(f) + (s.getValue(DisplayBlock.DIAG) ? 1 : 0)) & 7;
		}
		if (s.getBlock() instanceof StandDisplayBlock) {
			return s.getValue(StandDisplayBlock.ROT);
		}
		return -1;
	}

	public static boolean isDiagonal(BlockState s) {
		int y = yaw(s);
		return y >= 0 && (y & 1) == 1;
	}

	/** 床・天井のパネルの、画面の上の向き(水平)。それ以外は NORTH。 */
	public static Direction spin(BlockState s) {
		if (s.getBlock() instanceof DisplayBlock && s.getValue(DisplayBlock.FACING).getAxis() == Direction.Axis.Y) {
			return s.getValue(DisplayBlock.SPIN);
		}
		return Direction.NORTH;
	}

	/** 床・天井のパネルなら、その向き(UP / DOWN)。それ以外は null。 */
	public static Direction verticalFacing(BlockState s) {
		if (s.getBlock() instanceof DisplayBlock && s.getValue(DisplayBlock.FACING).getAxis() == Direction.Axis.Y) {
			return s.getValue(DisplayBlock.FACING);
		}
		return null;
	}

	public static Direction up(Direction facing, Direction spin) {
		return facing.getAxis() == Direction.Axis.Y ? spin : Direction.UP;
	}

	/** 見ている人から見て右 = (-法線) x 上(東西南北の向き)。 */
	public static Direction right(Direction facing, Direction spin) {
		Direction up = up(facing, spin);
		int nx = -facing.getStepX();
		int ny = -facing.getStepY();
		int nz = -facing.getStepZ();
		return Direction.getApproximateNearest(ny * up.getStepZ() - nz * up.getStepY(),
				nz * up.getStepX() - nx * up.getStepZ(), nx * up.getStepY() - ny * up.getStepX());
	}

	/** 画面の「右」の、ブロックの位置のずれ。斜めなら (±1, 0, ±1)。 */
	public static Vec3i rightOffset(BlockState s) {
		int y = yaw(s);
		if (y >= 0) {
			double th = Math.toRadians(y * 45.0);
			double nx = -Math.sin(th);
			double nz = Math.cos(th);
			double k = (y & 1) == 1 ? Math.sqrt(2.0) : 1.0;
			return new Vec3i((int) Math.round(nz * k), 0, (int) Math.round(-nx * k));
		}
		Direction f = verticalFacing(s);
		if (f != null) {
			return right(f, spin(s)).getUnitVec3i();
		}
		return Vec3i.ZERO;
	}

	/** 画面の「上」の、ブロックの位置のずれ。 */
	public static Vec3i upOffset(BlockState s) {
		if (yaw(s) >= 0) {
			return new Vec3i(0, 1, 0);
		}
		Direction f = verticalFacing(s);
		return f != null ? spin(s).getUnitVec3i() : Vec3i.ZERO;
	}

	private static Vec3i neg(Vec3i v) {
		return new Vec3i(-v.getX(), -v.getY(), -v.getZ());
	}

	private static boolean sameOrientation(BlockState a, BlockState b) {
		Direction va = verticalFacing(a);
		Direction vb = verticalFacing(b);
		if (va != null || vb != null) {
			return va == vb && spin(a) == spin(b);
		}
		int ya = yaw(a);
		return ya >= 0 && ya == yaw(b);
	}

	// ---- 連結 ----

	/** a から d(ブロックの位置のずれ)に隣り合う b が、同じ画面につながるか。画面の面の中(右・左・上・下)にある隣だけ。 */
	public static boolean canConnect(BlockState a, BlockState b, Vec3i d) {
		if (!isDisplay(a) || !isDisplay(b) || frameless(a) != frameless(b) || !sameOrientation(a, b)) {
			return false;
		}
		Vec3i r = rightOffset(a);
		Vec3i u = upOffset(a);
		if (!(d.equals(r) || d.equals(neg(r)) || d.equals(u) || d.equals(neg(u)))) {
			return false;
		}
		// スタンドの下側(台)には、つながらない
		if (isStand(a) && d.getY() < 0) {
			return false;
		}
		return !(isStand(b) && d.getY() > 0);
	}

	/** pos から d の向きに、つながっているパネルが何枚続くか。 */
	public static int run(LevelReader level, BlockPos pos, Vec3i d) {
		int n = 0;
		BlockPos p = pos;
		BlockState cur = level.getBlockState(p);
		while (n < 64) {
			BlockPos q = p.offset(d);
			BlockState next = level.getBlockState(q);
			if (!canConnect(cur, next, d)) {
				break;
			}
			n++;
			p = q;
			cur = next;
		}
		return n;
	}

	/** {左に何枚, 右に何枚, 上に何枚, 下に何枚}。ディスプレイでなければ全部 0。 */
	public static int[] extent(LevelReader level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!isDisplay(s)) {
			return new int[4];
		}
		Vec3i r = rightOffset(s);
		Vec3i u = upOffset(s);
		return new int[] { run(level, pos, neg(r)), run(level, pos, r), run(level, pos, u), run(level, pos, neg(u)) };
	}

	/** グループの親(左上のパネル)の位置。 */
	public static BlockPos primaryOf(LevelReader level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!isDisplay(s)) {
			return pos;
		}
		int[] e = extent(level, pos);
		return pos.offset(rightOffset(s).multiply(-e[0])).offset(upOffset(s).multiply(e[2]));
	}

	/** 親から見た、グループの大きさ {列, 行}。 */
	public static int[] sizeOf(LevelReader level, BlockPos primary) {
		BlockState s = level.getBlockState(primary);
		if (!isDisplay(s)) {
			return new int[] { 1, 1 };
		}
		return new int[] { 1 + run(level, primary, rightOffset(s)), 1 + run(level, primary, neg(upOffset(s))) };
	}

	/** グループ(左右 e[0],e[1]、上下 e[2],e[3] に広がる)の中に、スタンドがあるか。 */
	public static boolean groupHasStand(LevelReader level, BlockPos pos, int[] e) {
		BlockState s = level.getBlockState(pos);
		if (!isDisplay(s)) {
			return false;
		}
		Vec3i r = rightOffset(s);
		Vec3i u = upOffset(s);
		for (int i = -e[0]; i <= e[1]; i++) {
			for (int j = -e[3]; j <= e[2]; j++) {
				if (isStand(level.getBlockState(pos.offset(r.multiply(i)).offset(u.multiply(j))))) {
					return true;
				}
			}
		}
		return false;
	}

	// ---- 置いたときに、隣のディスプレイの向きに合わせる ----

	/** source の向きを、placed(これから置く、同じ種類のブロック)にコピーする。できなければ null。 */
	private static BlockState withOrientationOf(BlockState placed, BlockState source) {
		if (placed.getBlock() instanceof StandDisplayBlock) {
			int y = yaw(source);
			return y < 0 ? null : placed.setValue(StandDisplayBlock.ROT, y);
		}
		if (!(placed.getBlock() instanceof DisplayBlock)) {
			return null;
		}
		Direction vf = verticalFacing(source);
		if (vf != null) {
			return placed.setValue(DisplayBlock.FACING, vf).setValue(DisplayBlock.SPIN, spin(source)).setValue(DisplayBlock.DIAG, false);
		}
		int y = yaw(source);
		if (y < 0) {
			return null;
		}
		boolean diag = (y & 1) == 1;
		return placed.setValue(DisplayBlock.FACING, cardinalOf(diag ? y - 1 : y)).setValue(DisplayBlock.DIAG, diag)
				.setValue(DisplayBlock.SPIN, Direction.NORTH);
	}

	/**
	 * これから pos に置く placed が、隣(上下左右・斜め)のディスプレイと画面の面の中で隣り合うなら、その向きに合わせた状態を返す
	 * (向きが違っても、後に置いたほうが向きを直して、つながる)。つながる隣がなければ、そのまま返す。
	 */
	public static BlockState adopt(LevelReader level, BlockPos pos, BlockState placed) {
		for (Vec3i d : ADJACENT) {
			BlockState n = level.getBlockState(pos.offset(d));
			if (!isDisplay(n) || frameless(n) != frameless(placed)) {
				continue;
			}
			BlockState cand = withOrientationOf(placed, n);
			if (cand != null && canConnect(n, cand, neg(d))) {
				return cand;
			}
		}
		return placed;
	}
}
