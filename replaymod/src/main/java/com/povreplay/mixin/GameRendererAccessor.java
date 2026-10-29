package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Accessor("gameRenderState")
	GameRenderState povreplay$gameRenderState();
}
