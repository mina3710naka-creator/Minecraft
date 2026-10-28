package com.recmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import com.recmod.AudioMixer;

/** 聞き手(プレイヤーの耳)の位置と向きを、録画用にも覚えておく。 */
@Mixin(Listener.class)
public abstract class ListenerMixin {

	@Inject(method = "setTransform", at = @At("HEAD"))
	private void recmod$listener(ListenerTransform transform, CallbackInfo ci) {
		AudioMixer.setListener(transform);
	}
}
