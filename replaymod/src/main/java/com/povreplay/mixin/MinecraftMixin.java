package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.povreplay.ReplayEngine;
import com.povreplay.ReplayMode;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/** リプレイの再生中は、ゲームの時計を、1 フレームごとに決まった時間だけ進む仮想の時計にする。 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	@Inject(method = "runTick", at = @At("HEAD"))
	private void povreplay$beforeTick(boolean advanceGameTime, CallbackInfo ci) {
		ReplayEngine.beforeTick();
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private long povreplay$tickTime(Operation<Long> original) {
		return ReplayMode.active() ? ReplayMode.virtualMillis() : original.call();
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private long povreplay$frameTime(Operation<Long> original) {
		return ReplayMode.active() ? ReplayMode.virtualMillis() : original.call();
	}
}
