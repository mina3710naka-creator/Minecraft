package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.povreplay.ReplayMode;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;

/** リプレイの再生中は、音を鳴らさない(音は、録画のときに録音したものを使う)。 */
@Mixin(SoundManager.class)
public abstract class SoundMixin {

	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void povreplay$mute(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (ReplayMode.active()) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}
}
