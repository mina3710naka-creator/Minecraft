package com.povreplay.mixin;

import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.povreplay.AudioMixer;

import javax.sound.sampled.AudioFormat;

/**
 * 効果音のデータは、OpenAL へ渡したあと、ゲームの側では捨てられる。録画で同じ音を混ぜ直せるように、
 * 渡す前の入れ物(ByteBuffer)を覚えておく(SoundBuffer が要らなくなれば、一緒に消える)。
 */
@Mixin(SoundBuffer.class)
public abstract class SoundBufferMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void povreplay$remember(ByteBuffer data, AudioFormat format, CallbackInfo ci) {
		AudioMixer.rememberBuffer((SoundBuffer) (Object) this, data);
	}
}
