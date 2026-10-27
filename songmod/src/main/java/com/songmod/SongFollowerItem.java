package com.songmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 右クリックで ON / OFF。ON のあいだ、曲を流すと、いま鳴っている場所(信号の先頭)へなめらかに移動する。 */
public class SongFollowerItem extends Item {

	public SongFollowerItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			boolean on = SongFollow.toggle(sp);
			sp.sendSystemMessage(Component.literal("[曲] 再生位置への追従: " + (on ? "ON(曲を流すと、鳴っている場所へ移動します)" : "OFF")));
		}
		return InteractionResult.SUCCESS;
	}
}
