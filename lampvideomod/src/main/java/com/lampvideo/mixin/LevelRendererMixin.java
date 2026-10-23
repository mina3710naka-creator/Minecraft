package com.lampvideo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.lampvideo.LampVideoClient;

import net.minecraft.client.renderer.LevelRenderer;

/**
 * チャンクの見た目の作り直し(LevelRenderer#compileSections)に割り込む。
 *
 * バニラは、設定「チャンクビルダー」が「完全ブロッキング」(NEARBY)のとき、カメラから 768 ブロック^2(約27.7ブロック)以内の
 * 区画だけを、同じフレームの中で(同期で)作り直し、それより遠い区画は、別スレッドで作り直す。
 * 遠い区画は、作り終わる順番がばらばらなので、画面がチャンクごとにずれて見える。
 * この距離の定数(768.0)を、動画の再生中だけ、とても大きな値にして、画面の全部の区画を、同じフレームで作り直させる。
 *
 * 安全のため: 割り込み先が見つからなくても(require = 0)ゲームは落ちない。処理は例外を握りつぶして、元の値を返す。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

	@ModifyConstant(method = "compileSections", constant = @Constant(doubleValue = 768.0D), require = 0)
	private double lampvideo$widenNearbyDistance(double original) {
		try {
			return LampVideoClient.syncDistanceSquared(original);
		} catch (Throwable t) {
			return original;
		}
	}
}
