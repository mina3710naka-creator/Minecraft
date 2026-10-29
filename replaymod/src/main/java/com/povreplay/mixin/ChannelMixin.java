package com.povreplay.mixin;

import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.povreplay.AudioMixer;
import com.povreplay.Pcm;
import com.povreplay.Voice;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.world.phys.Vec3;

/**
 * ゲームが音の出口(Channel)に出す命令を、録画用の写し(Voice)にも書いておく。
 * 写しを作るだけで、ゲームの音そのものには何も変えない。
 */
@Mixin(Channel.class)
public abstract class ChannelMixin {

	@Inject(method = "play", at = @At("HEAD"))
	private void povreplay$play(CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.play(System.nanoTime());
		}
	}

	@Inject(method = "pause", at = @At("HEAD"))
	private void povreplay$pause(CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.pause(System.nanoTime());
		}
	}

	@Inject(method = "unpause", at = @At("HEAD"))
	private void povreplay$unpause(CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.unpause(System.nanoTime());
		}
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void povreplay$stop(CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.reset();
		}
	}

	@Inject(method = "destroy", at = @At("HEAD"))
	private void povreplay$destroy(CallbackInfo ci) {
		AudioMixer.forget((Channel) (Object) this);
	}

	@Inject(method = "setSelfPosition", at = @At("HEAD"))
	private void povreplay$position(Vec3 position, CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.x = position.x;
			v.y = position.y;
			v.z = position.z;
		}
	}

	@Inject(method = "setPitch", at = @At("HEAD"))
	private void povreplay$pitch(float pitch, CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.pitch = pitch;
		}
	}

	@Inject(method = "setLooping", at = @At("HEAD"))
	private void povreplay$looping(boolean looping, CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.looping = looping;
		}
	}

	@Inject(method = "setVolume", at = @At("HEAD"))
	private void povreplay$volume(float volume, CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.gain = volume;
		}
	}

	@Inject(method = "disableAttenuation", at = @At("HEAD"))
	private void povreplay$noAttenuation(CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.attenuated = false;
		}
	}

	@Inject(method = "linearAttenuation", at = @At("HEAD"))
	private void povreplay$linearAttenuation(float maxDistance, CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.attenuated = true;
			v.maxDistance = maxDistance;
		}
	}

	@Inject(method = "setRelative", at = @At("HEAD"))
	private void povreplay$relative(boolean relative, CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.relative = relative;
		}
	}

	@Inject(method = "attachStaticBuffer", at = @At("HEAD"))
	private void povreplay$staticBuffer(SoundBuffer buffer, CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.queue.clear();
			v.stream = false;
			v.current = null;
			v.generation = -1;
			v.staticSource = buffer;
		}
	}

	@Inject(method = "attachBufferStream", at = @At("HEAD"))
	private void povreplay$stream(AudioStream stream, CallbackInfo ci) {
		Voice v = AudioMixer.voice((Channel) (Object) this);
		synchronized (v) {
			v.queue.clear();
			v.stream = true;
			v.staticSource = null;
			v.current = null;
			v.pos = 0;
		}
	}

	/** ストリームの音(音楽など)は、ゲームが読み込んだ分を、そのまま写し取る。 */
	@WrapOperation(method = "pumpBuffers",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/AudioStream;read(I)Ljava/nio/ByteBuffer;"))
	private ByteBuffer povreplay$copyStreamChunk(AudioStream stream, int size, Operation<ByteBuffer> original) {
		ByteBuffer chunk = original.call(stream, size);
		try {
			if (chunk != null) {
				Pcm pcm = Pcm.of(chunk, stream.getFormat());
				Voice v = AudioMixer.voice((Channel) (Object) this);
				synchronized (v) {
					v.enqueue(pcm);
				}
			}
		} catch (RuntimeException e) {
			// 写せなくても、ゲームの音には影響させない
		}
		return chunk;
	}
}
