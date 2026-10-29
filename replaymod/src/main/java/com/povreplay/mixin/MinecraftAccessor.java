package com.povreplay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("pendingConnection")
	void povreplay$setPendingConnection(Connection connection);
}
