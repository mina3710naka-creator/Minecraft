package com.lampvideo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lampvideo.LampVideoClient;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/**
 * チャンクの見た目の作り直し(LevelRenderer#compileSections)に割り込む。
 *
 * バニラは、設定「チャンクビルダー」が「完全ブロッキング」(NEARBY)のとき、区画の中心からカメラまでの距離の2乗が
 * 768(約27.7ブロック)未満の区画だけを、同じフレームの中で(同期で)作り直し、それより遠い区画は別スレッドで作り直す。
 * 遠い区画は作り終わる順番がばらばらなので、画面がチャンクごとにずれて見える。
 * その距離の計算を、動画の再生中で、区画が画面の範囲の中のときだけ 0 にして、画面の区画を全部、同じフレームで作り直させる。
 * 画面と関係ない区画は、バニラのまま(非同期)なので、画面全体を同期にする負荷は、画面の分だけで済む。
 *
 * 安全のため: 割り込み先が見つからなくても(require = 0)ゲームは落ちない。処理は例外を握りつぶして、元の値を返す。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

	/**
	 * ディスプレイに照準を合わせたときの、当たり判定の枠(ブロックの黒い輪郭線)を描かない。
	 * 当たり判定そのものは変えない(壊せるし、ブロックとして置ける)。描画だけを取りやめる。
	 */
	@Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true, require = 0)
	private void lampvideo$hideDisplayOutline(com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.SubmitNodeCollector collector,
			net.minecraft.client.renderer.state.level.LevelRenderState state, CallbackInfo ci) {
		try {
			if (state.blockOutlineRenderState != null && LampVideoClient.isDisplayAt(state.blockOutlineRenderState.pos())) {
				ci.cancel();
			}
		} catch (Throwable t) {
			// 枠を消せなくても、ゲームは続ける
		}
	}

	@Inject(method = "compileSections", at = @At("HEAD"), require = 0)
	private void lampvideo$compileBegin(net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
		LampVideoClient.compileBegin();
	}

	@Inject(method = "compileSections", at = @At("RETURN"), require = 0)
	private void lampvideo$compileEnd(net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
		LampVideoClient.compileEnd();
	}

	@WrapOperation(method = "compileSections",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"),
			require = 0)
	private double lampvideo$syncScreenSections(BlockPos sectionCenter, Vec3i camera, Operation<Double> original) {
		double distance = original.call(sectionCenter, camera);
		try {
			return LampVideoClient.syncDistance(distance, sectionCenter.getX(), sectionCenter.getY(), sectionCenter.getZ());
		} catch (Throwable t) {
			return distance;
		}
	}
}
