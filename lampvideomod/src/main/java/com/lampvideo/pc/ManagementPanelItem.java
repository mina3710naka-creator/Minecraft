package com.lampvideo.pc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 管理パネル(アイテム)。右クリックで、いまいる場所の近く(48ブロック)の PC と画面の一覧を開いて、接続や設定をする。リモコンの進化版。 */
public class ManagementPanelItem extends Item {

	public ManagementPanelItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			PanelNetwork.open(sp, sp.blockPosition(), "");
		}
		return InteractionResult.SUCCESS;
	}
}
