package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Camera;

@Mixin(Camera.class)
public interface CameraAccessor {
	@Accessor("hudFov")
	float povreplay$getHudFov();
}
