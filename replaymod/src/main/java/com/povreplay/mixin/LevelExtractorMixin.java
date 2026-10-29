package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.povreplay.FrameSample;
import com.povreplay.LiveRecording;
import com.povreplay.ReplayEngine;
import com.povreplay.ReplayMode;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;

/**
 * プレイヤーまわりの描画の状態(一人称の手・体・画面の効果)が決まった直後。
 * 録画中は、その値を記録する。リプレイの再生中は、記録した値で上書きする(ゲームの中の「プレイヤー」は、動かしていないので)。
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

	@Inject(method = "extractPlayerState", at = @At("TAIL"))
	private void povreplay$player(Camera camera, DeltaTracker deltaTracker, float worldPartialTicks, PlayerRenderState state, CallbackInfo ci) {
		try {
			if (ReplayMode.active()) {
				apply(state);
			} else if (LiveRecording.isRecording()) {
				record(camera, deltaTracker, state);
			}
		} catch (RuntimeException e) {
			com.povreplay.PovReplay.LOGGER.error("プレイヤーの状態を扱えませんでした", e);
		}
	}

	private static void record(Camera camera, DeltaTracker deltaTracker, PlayerRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !state.hasPlayer || state.avatarRenderState == null) {
			return;
		}
		FrameSample s = new FrameSample();
		s.camX = camera.position().x;
		s.camY = camera.position().y;
		s.camZ = camera.position().z;
		s.camYRot = camera.yRot();
		s.camXRot = camera.xRot();
		s.fov = camera.getFov();
		s.hudFov = ((CameraAccessor) camera).povreplay$getHudFov();
		float partial = deltaTracker.getGameTimeDeltaPartialTick(!mc.level.tickRateManager().isEntityFrozen(player));
		var avatar = ((AbstractClientPlayer) player).avatarState();
		s.bob = avatar.getInterpolatedBob(partial);
		s.walkDistBack = avatar.getBackwardsInterpolatedWalkDistance(partial);
		FirstPersonHandsAndItemsRenderState fp = state.firstPersonHandsAndItems;
		s.attackHand = (byte) (fp.attackHand == null ? 0 : fp.attackHand.ordinal());
		s.viewXRot = fp.viewXRot;
		s.viewYRot = fp.viewYRot;
		s.xBob = fp.xBob;
		s.yBob = fp.yBob;
		s.isScoping = fp.isScoping;
		s.useItemRemainingTicks = fp.useItemRemainingTicks;
		s.handSelection = (byte) (fp.handRenderSelection == null ? 0 : fp.handRenderSelection.ordinal());
		s.mainHandHeight = fp.mainHandHeight;
		s.oldMainHandHeight = fp.oldMainHandHeight;
		s.offHandHeight = fp.offHandHeight;
		s.oldOffHandHeight = fp.oldOffHandHeight;
		if (state.avatarRenderState instanceof HumanoidRenderState humanoid) {
			s.swingAnimation = humanoid.swingAnimation;
			s.ticksUsingItem = humanoid.ticksUsingItem;
			s.isUsingItem = humanoid.isUsingItem;
			s.useItemHand = (byte) humanoid.useItemHand.ordinal();
		}
		s.portalEffectIntensity = state.portalEffectIntensity;
		s.nauseaEffectIntensity = state.nauseaEffectIntensity;
		s.spinningEffectAngle = state.spinningEffectAngle;
		s.isUnderWater = state.isUnderWater;
		s.eyePositionY = state.eyePositionY;
		s.selectedSlot = (byte) player.getInventory().getSelectedSlot();
		LiveRecording.record(s);
	}

	private static void apply(PlayerRenderState state) {
		FrameSample s = ReplayEngine.currentSample();
		if (s == null || !state.hasPlayer || state.avatarRenderState == null) {
			return;
		}
		FirstPersonHandsAndItemsRenderState fp = state.firstPersonHandsAndItems;
		fp.attackHand = InteractionHand.values()[Math.min(s.attackHand, InteractionHand.values().length - 1)];
		fp.viewXRot = s.viewXRot;
		fp.viewYRot = s.viewYRot;
		fp.xBob = s.xBob;
		fp.yBob = s.yBob;
		fp.isScoping = s.isScoping;
		fp.useItemRemainingTicks = s.useItemRemainingTicks;
		FirstPersonHandsAndItemsRenderState.HandRenderSelection[] selections = FirstPersonHandsAndItemsRenderState.HandRenderSelection.values();
		fp.handRenderSelection = selections[Math.min(s.handSelection, selections.length - 1)];
		fp.mainHandHeight = s.mainHandHeight;
		fp.oldMainHandHeight = s.oldMainHandHeight;
		fp.offHandHeight = s.offHandHeight;
		fp.oldOffHandHeight = s.oldOffHandHeight;
		if (state.avatarRenderState instanceof HumanoidRenderState humanoid) {
			humanoid.swingAnimation = s.swingAnimation;
			humanoid.ticksUsingItem = s.ticksUsingItem;
			humanoid.isUsingItem = s.isUsingItem;
			humanoid.useItemHand = InteractionHand.values()[Math.min(s.useItemHand, InteractionHand.values().length - 1)];
		}
		state.portalEffectIntensity = s.portalEffectIntensity;
		state.nauseaEffectIntensity = s.nauseaEffectIntensity;
		state.spinningEffectAngle = s.spinningEffectAngle;
		state.isUnderWater = s.isUnderWater;
		state.eyePositionY = s.eyePositionY;
	}
}
