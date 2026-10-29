package com.songmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 右クリックで休符。いま鳴っている場所の前後のリピーターを壊し、1 秒後に戻して続きを流す。 */
public class SongRestItem extends Item {

	public SongRestItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			sp.sendSystemMessage(Component.literal("[曲] " + SongRest.use(sp)));
		}
		return InteractionResult.SUCCESS;
	}
}
