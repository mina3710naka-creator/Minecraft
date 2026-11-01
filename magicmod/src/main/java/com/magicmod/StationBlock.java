package com.magicmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 魔法テーブル・鍛冶テーブル・各祭壇(右クリックで画面を開く。手に持った本や杖・武器を対象にする)。 */
public class StationBlock extends Block {

	public final String screen;

	public StationBlock(Properties p, String screen) {
		super(p);
		this.screen = screen;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			Ui.open(sp, screen, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (player.isShiftKeyDown()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			Ui.open(sp, screen, pos);
		}
		return InteractionResult.SUCCESS;
	}
}
