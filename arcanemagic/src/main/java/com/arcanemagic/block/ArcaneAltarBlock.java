package com.arcanemagic.block;

import com.arcanemagic.screen.ArcaneAltarScreenHandler;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** アーケインの祭壇: 使うと GUI が開き、杖/魔導書への魔法付与・杖の強化ができる。 */
public class ArcaneAltarBlock extends Block {

	public ArcaneAltarBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
			NamedScreenHandlerFactory factory = state.createScreenHandlerFactory(world, pos);
			if (factory != null) {
				serverPlayer.openHandledScreen(factory);
			}
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
		return new SimpleNamedScreenHandlerFactory(
				(syncId, inventory, screenPlayer) -> new ArcaneAltarScreenHandler(syncId, inventory),
				Text.translatable("container.arcanemagic.arcane_altar"));
	}
}
