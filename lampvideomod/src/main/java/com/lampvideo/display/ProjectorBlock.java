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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * プロジェクター。向いている方向の壁に、大画面の映像を映す(壁までの距離が遠いほど、大きく映る)。
 * ディスプレイと同じように、PC に接続して、キーボード・管理パネル・リモコンから操作する。右クリックでは、何も起こらない。
 * 置くと、プレイヤーが向いている方向を向く。
 */
public class ProjectorBlock extends Block implements EntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public ProjectorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z
				? Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.375, 0.9375)
				: Shapes.box(0.1875, 0.0, 0.1875, 0.9375, 0.375, 0.8125);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ProjectorBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() && type == ModDisplay.PROJECTOR_ENTITY) {
			return (BlockEntityTicker<T>) (BlockEntityTicker<ProjectorBlockEntity>) (l, p, s, be) -> DisplayBlockEntity.CLIENT_TICK.accept(be);
		}
		return null;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return InteractionResult.PASS;
	}
}
