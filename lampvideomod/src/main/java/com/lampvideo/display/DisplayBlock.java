package com.lampvideo.display;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 壁・天井・床に貼り付く薄型ディスプレイ(厚さ2/16)。向き(FACING)は、画面が向く方向。
 * 映像は、ブロックエンティティの描画(DisplayRenderer)が、クライアントで1枚のテクスチャとして描く。
 */
public class DisplayBlock extends Block implements EntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	/** 4つの辺(モデルの向きでの北・東・南・西)に枠を出すか。隣に同じ向きのディスプレイがある辺は、枠を消して1枚につなぐ。 */
	public static final BooleanProperty EN = BooleanProperty.create("en");
	public static final BooleanProperty EE = BooleanProperty.create("ee");
	public static final BooleanProperty ES = BooleanProperty.create("es");
	public static final BooleanProperty EW = BooleanProperty.create("ew");

	private static final double T = 2.0 / 16.0;

	public DisplayBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP)
				.setValue(EN, true).setValue(EE, true).setValue(ES, true).setValue(EW, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, EN, EE, ES, EW);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withEdges(context.getLevel(), context.getClickedPos(), defaultBlockState().setValue(FACING, context.getClickedFace()));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case UP -> Shapes.box(0, 0, 0, 1, T, 1);
			case DOWN -> Shapes.box(0, 1 - T, 0, 1, 1, 1);
			case NORTH -> Shapes.box(0, 0, 1 - T, 1, 1, 1);
			case SOUTH -> Shapes.box(0, 0, 0, 1, 1, T);
			case WEST -> Shapes.box(1 - T, 0, 0, 1, 1, 1);
			case EAST -> Shapes.box(0, 0, 0, T, 1, 1);
		};
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		return withEdges(level, pos, state);
	}

	/** 周りのディスプレイに合わせて、枠を出す辺を決める。 */
	public BlockState withEdges(LevelReader level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FACING);
		return state
				.setValue(EN, !connected(level, pos, facing, Direction.NORTH))
				.setValue(EE, !connected(level, pos, facing, Direction.EAST))
				.setValue(ES, !connected(level, pos, facing, Direction.SOUTH))
				.setValue(EW, !connected(level, pos, facing, Direction.WEST));
	}

	private boolean connected(LevelReader level, BlockPos pos, Direction facing, Direction local) {
		return isSameDisplay(level.getBlockState(pos.relative(toWorld(facing, local))), this, facing);
	}

	/** 同じ種類(枠あり同士・枠なし同士)で、同じ向きのディスプレイか。 */
	public static boolean isSameDisplay(BlockState other, Block self, Direction facing) {
		return other.getBlock() == self && other.getValue(FACING) == facing;
	}

	/**
	 * モデルの向き(上向きの板での北・東・南・西)を、ブロックの向き(FACING)に合わせたワールドの向きに直す。
	 * ブロックステートの回転(x=90 で上→北、y は北→東の時計回り)と同じ計算。
	 */
	public static Direction toWorld(Direction facing, Direction local) {
		int x = local.getStepX();
		int y = 0;
		int z = local.getStepZ();
		if (facing == Direction.DOWN) {
			y = -y;
			z = -z;
		} else if (facing != Direction.UP) {
			int ny = z;
			int nz = -y;
			y = ny;
			z = nz;
		}
		int turns = switch (facing) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
		for (int t = 0; t < turns; t++) {
			int nx = -z;
			int nz = x;
			x = nx;
			z = nz;
		}
		return Direction.getApproximateNearest(x, y, z);
	}

	/** グループ(同じ種類・同じ向きで続くパネル)の親(左上)のパネルの位置。サーバーでもクライアントでも使える。 */
	public static BlockPos primaryOf(LevelReader level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!(s.getBlock() instanceof DisplayBlock)) {
			return pos;
		}
		Direction f = s.getValue(FACING);
		Direction left = screenRight(f).getOpposite();
		Direction up = screenUp(f);
		BlockPos p = pos;
		for (int i = 0; i < 64 && isSameDisplay(level.getBlockState(p.relative(left)), s.getBlock(), f); i++) {
			p = p.relative(left);
		}
		for (int i = 0; i < 64 && isSameDisplay(level.getBlockState(p.relative(up)), s.getBlock(), f); i++) {
			p = p.relative(up);
		}
		return p;
	}

	/** 画面の「上」(壁なら真上、床・天井なら北)。 */
	public static Direction screenUp(Direction facing) {
		return facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
	}

	/** 画面の「右」(見ている人から見て右)= (-法線) x 上。 */
	public static Direction screenRight(Direction facing) {
		Direction up = screenUp(facing);
		int nx = -facing.getStepX();
		int ny = -facing.getStepY();
		int nz = -facing.getStepZ();
		return Direction.getApproximateNearest(ny * up.getStepZ() - nz * up.getStepY(),
				nz * up.getStepX() - nx * up.getStepZ(), nx * up.getStepY() - ny * up.getStepX());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DisplayBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() && type == ModDisplay.DISPLAY_ENTITY) {
			return (BlockEntityTicker<T>) (BlockEntityTicker<DisplayBlockEntity>) (l, p, s, be) -> DisplayBlockEntity.CLIENT_TICK.accept(be);
		}
		return null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			DisplayBlockEntity.OPEN_GUI.accept(pos);
		}
		return InteractionResult.SUCCESS;
	}
}
