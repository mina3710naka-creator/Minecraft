package com.lampvideo.pc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 操作の入口になるブロック。kind: PC(本体。パスワードや管理下の画面を持つ) / KEYBOARD。
 * キーボードは、近くにある一番近い PC を操作する。
 */
public class PcBlock extends Block implements EntityBlock {

	public enum Kind {
		PC(16), KEYBOARD(16);

		/** 操作できる PC を探す範囲(ブロック)。 */
		public final int range;

		Kind(int range) {
			this.range = range;
		}
	}

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public final Kind kind;

	public PcBlock(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
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
		if (kind != Kind.KEYBOARD) {
			return Shapes.block();
		}
		return state.getValue(FACING).getAxis() == Direction.Axis.Z
				? Shapes.box(0.0, 0.0, 0.2, 1.0, 0.125, 0.8)
				: Shapes.box(0.2, 0.0, 0.0, 0.8, 0.125, 1.0);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return kind == Kind.PC ? new PcBlockEntity(pos, state) : null;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && kind == Kind.PC && placer instanceof Player player
				&& level.getBlockEntity(pos) instanceof PcBlockEntity pc) {
			pc.owner = player.getUUID().toString();
			pc.setChanged();
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			PcNetwork.open(sp, pos, kind.range, "");
		}
		return InteractionResult.SUCCESS;
	}
}
