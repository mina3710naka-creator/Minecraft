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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * スタンド型ディスプレイ。地面に立てて置く1ブロックの卓上モニター(台・支柱・画面)。
 * 向き(ROT)は、8方向(0=南向き、1=南西…、2=西向き…)。置くと、プレイヤーの方を向く。
 * 見た目は、ブロックの描画(モデル)ではなく、ブロックエンティティの描画(DisplayRenderer)が全部描く。斜めにも置けるように。
 * 東西南北の向きのスタンドは、同じ向きの隣のパネルとつながって、画面を拡張できる(スタンドの上と左右)。
 */
public class StandDisplayBlock extends Block implements EntityBlock {

	/** 画面が向く方向の角度(45度刻み)。0=南向き(+Z)、2=西向き(-X)、4=北向き、6=東向き。 */
	public static final IntegerProperty ROT = IntegerProperty.create("rot", 0, 7);

	public StandDisplayBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(ROT, 4));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ROT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// プレイヤーの向き(yaw。0=南を向く)の反対側(= プレイヤーの方)に、画面を向ける
		int rot = Math.floorMod(Math.round((context.getRotation() + 180.0f) / 45.0f), 8);
		// 隣にディスプレイがあれば、その向きに合わせて、つなぐ
		return DisplayGeometry.adopt(context.getLevel(), context.getClickedPos(), defaultBlockState().setValue(ROT, rot));
	}

	/** 東西南北の向きなら、その方向。斜めなら null。 */
	public static Direction facingOf(BlockState s) {
		return switch (s.getValue(ROT)) {
			case 0 -> Direction.SOUTH;
			case 2 -> Direction.WEST;
			case 4 -> Direction.NORTH;
			case 6 -> Direction.EAST;
			default -> null;
		};
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
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
		// 右クリックでは、何も起こらない
		return InteractionResult.PASS;
	}
}
