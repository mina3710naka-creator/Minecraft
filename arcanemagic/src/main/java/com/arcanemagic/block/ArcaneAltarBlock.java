package com.arcanemagic.block;

import com.arcanemagic.screen.ArcaneAltarMenu;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** アーケインの祭壇: 使うと GUI が開き、杖/魔導書への魔法付与・杖の強化ができる。 */
public class ArcaneAltarBlock extends Block {

	public static final MapCodec<ArcaneAltarBlock> CODEC = simpleCodec(ArcaneAltarBlock::new);

	public ArcaneAltarBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
			MenuProvider provider = getMenuProvider(state, level, pos);
			if (provider != null) {
				serverPlayer.openMenu(provider);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
		return new SimpleMenuProvider(
				(syncId, inventory, screenPlayer) -> new ArcaneAltarMenu(syncId, inventory),
				Component.translatable("container.arcanemagic.arcane_altar"));
	}
}
