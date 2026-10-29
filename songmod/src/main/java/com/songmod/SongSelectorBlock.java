package com.songmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 選曲台。置くと、置いた人のほうを向く(正面にいる人に、選んだ曲名が浮かんで見える)。上にはボタンが付く。
 * 右クリックで曲の一覧画面が開き、クリックで曲を選ぶ。上のボタンを押すと、選んだ曲の回路が、台の後ろ側へ建つ。
 */
public class SongSelectorBlock extends Block {

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public SongSelectorBlock(Properties properties) {
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
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel sl && !oldState.is(this)) {
			SongSelector.placed(sl, pos, state);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			SongSelector.openList(sl, pos, state, sp);
		}
		return InteractionResult.SUCCESS;
	}

	/** 上のボタンが押されると(隣のブロックの状態が変わると)、選んだ曲を建てる。 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean moved) {
		if (level instanceof ServerLevel sl) {
			SongSelector.neighbor(sl, pos, state);
		}
	}
}
