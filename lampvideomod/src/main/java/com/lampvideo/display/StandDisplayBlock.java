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
 * スタンド型ディスプレイ。地面に立てて置く1ブロックの卓上モニター(台・支柱・画面)。連結はしない。
 * 向き(FACING)は、画面が向く水平の方向。置くと、プレイヤーの方を向く。
 */
public class StandDisplayBlock extends Block implements EntityBlock {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public StandDisplayBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		// 台と支柱と画面を含む外形(奥行きは画面の厚みの分)
		return state.getValue(FACING).getAxis() == Direction.Axis.Z
				? Shapes.box(0.0, 0.0, 0.1875, 1.0, 1.0, 0.8125)
				: Shapes.box(0.1875, 0.0, 0.0, 0.8125, 1.0, 1.0);
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
