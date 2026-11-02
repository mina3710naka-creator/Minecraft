package com.magicmod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 魔法の手引き: 遊び方と図鑑を開く。 */
public class GuideItem extends Item {

	public GuideItem(Properties p) {
		super(p.stacksTo(1));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			Ui.open(sp, "guide", null);
		}
		return InteractionResult.SUCCESS;
	}
}
