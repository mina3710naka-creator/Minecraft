package com.effectmod.client;

import com.effectmod.Entry;
import com.effectmod.ModContent;
import com.effectmod.Net;
import com.effectmod.Parser;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

public class EffectClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModContent.BENCH_MENU, BenchScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(Net.ResultPayload.TYPE, (pl, ctx) -> ctx.client().execute(() -> {
			if (ctx.client().gui.screen() instanceof BenchScreen s) {
				s.status(pl.message(), pl.ok());
			}
		}));
		ItemTooltipCallback.EVENT.register((stack, tctx, flag, lines) -> {
			var es = stack.get(ModContent.EFFECTS);
			if (es == null) {
				return;
			}
			for (Entry e : es) {
				lines.add(Component.literal("✦ " + Parser.label(e)).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		});
	}
}
