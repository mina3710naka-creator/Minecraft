package com.effectmod;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 付与台。右クリックで効果を付ける画面を開く。 */
public class BenchBlock extends Block {

	public BenchBlock(Properties p) {
		super(p);
	}

	private static InteractionResult open(Level level, BlockPos pos, Player player) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			sp.openMenu(new ExtendedMenuProvider<BlockPos>() {
				@Override
				public BlockPos getScreenOpeningData(ServerPlayer p) {
					return pos;
				}

				@Override
				public Component getDisplayName() {
					return Component.translatable("block.effectmod.effect_bench");
				}

				@Override
				public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
					return new BenchMenu(id, inv, ContainerLevelAccess.create(level, pos));
				}
			});
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		return open(level, pos, player);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (player.isShiftKeyDown()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		return open(level, pos, player);
	}
}
