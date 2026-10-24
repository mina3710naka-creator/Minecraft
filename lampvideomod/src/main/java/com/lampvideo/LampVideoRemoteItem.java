package com.lampvideo;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 右クリックで、ランプ動画のメニュー(画面の作成・再生・停止・片付け・ブロック・テクスチャ)を開くアイテム。しゃがんで使うと、近くの PC を操作する(リモコン)。 */
public class LampVideoRemoteItem extends Item {

	/**
	 * クライアント側で、メニュー画面を開く処理。クライアントの初期化(LampVideoClient)で設定する。
	 * サーバー側にはクライアントのクラスがないので、ここから直接は参照しない。
	 */
	public static volatile Runnable openMenu = () -> { };

	public LampVideoRemoteItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isShiftKeyDown()) {
			// しゃがんで使うと、近く(32ブロック)の PC を操作する
			if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
				com.lampvideo.pc.PcNetwork.open(sp, sp.blockPosition(), com.lampvideo.pc.PcNetwork.REMOTE_RANGE, "");
			}
		} else if (level.isClientSide()) {
			openMenu.run();
		}
		return InteractionResult.SUCCESS;
	}
}
