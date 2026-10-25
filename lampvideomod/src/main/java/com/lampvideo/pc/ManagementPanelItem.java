package com.lampvideo.pc;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** 管理パネル(アイテム)。右クリックで、いまいる場所の近く(48ブロック)の PC と画面の一覧を開いて、接続や設定をする。リモコンの進化版。 */
public class ManagementPanelItem extends Item {

	public ManagementPanelItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	/** しゃがみながらディスプレイやスピーカーを右クリックすると、近くの PC に接続(または解除)する。向きが違っても、斜めでも接続できる。 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null || !player.isShiftKeyDown()
				|| !(com.lampvideo.display.DisplayGeometry.isLinkable(level.getBlockState(context.getClickedPos()))
						|| level.getBlockState(context.getClickedPos()).getBlock() instanceof SpeakerBlock)) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			PanelNetwork.quickLink(sp, context.getClickedPos());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			PanelNetwork.open(sp, sp.blockPosition(), "");
		}
		return InteractionResult.SUCCESS;
	}
}
