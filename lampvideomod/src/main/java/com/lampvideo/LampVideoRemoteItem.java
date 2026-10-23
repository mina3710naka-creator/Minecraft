package com.lampvideo;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 右クリックで、ランプ動画のメニュー(画面の作成・再生・停止・片付け・ブロック・テクスチャ)を開くアイテム。 */
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
		if (level.isClientSide()) {
			openMenu.run();
		}
		return InteractionResult.SUCCESS;
	}
}
