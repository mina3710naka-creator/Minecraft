package com.lampvideo.display;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 壁・天井・床に貼り付く薄型ディスプレイ(厚さ2/16)。向き(FACING)は、画面が向く方向。
 * 床・天井に置いたときの、画面の上の向き(SPIN)は、置いたプレイヤーが向いている方向。
 * 枠と映像は、ブロックエンティティの描画(DisplayRenderer)が描く(隣のパネルとつながる辺は、枠が消える)。
 */
public class DisplayBlock extends Block implements EntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	/** 床・天井のときだけ意味がある(壁では NORTH のまま)。 */
	public static final EnumProperty<Direction> SPIN = EnumProperty.create("spin", Direction.class,
			Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

	/** 水平に貼ったパネルが、45度ななめを向いているか(FACING は、いちばん近い東西南北)。 */
	public static final BooleanProperty DIAG = BooleanProperty.create("diag");

	private static final double T = 2.0 / 16.0;

	public DisplayBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(SPIN, Direction.NORTH).setValue(DIAG, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SPIN, DIAG);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		BlockState placed = defaultBlockState().setValue(FACING, face)
				.setValue(SPIN, face.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection() : Direction.NORTH);
		// 隣にディスプレイがあれば、その向きに合わせて、つなぐ
		return DisplayGeometry.adopt(context.getLevel(), context.getClickedPos(), placed);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(DIAG)) {
			return Shapes.box(0.125, 0.0, 0.125, 0.875, 1.0, 0.875);
		}
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
		// 右クリックでは、何も起こらない(操作は、PC に接続して、キーボード・管理パネル・リモコンから)
		return InteractionResult.PASS;
	}
}
