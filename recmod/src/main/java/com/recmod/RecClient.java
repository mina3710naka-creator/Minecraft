package com.recmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * クライアント側: メニュー(Esc)に「録画」ボタンを足す。押すと録画を始め、録画中は「停止」になって、押すと保存する。
 */
public class RecClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen) {
				Button video = Button.builder(videoLabel(), b -> {
					Recorder.toggle(Minecraft.getInstance());
					b.setMessage(videoLabel());
				}).bounds(6, 6, 110, 20).build();
				Button audio = Button.builder(audioLabel(), b -> {
					Recorder.toggleAudio(Minecraft.getInstance());
					b.setMessage(audioLabel());
				}).bounds(6, 30, 110, 20).build();
				Screens.getWidgets(screen).add(video);
				Screens.getWidgets(screen).add(audio);
				ScreenEvents.afterTick(screen).register(s -> {
					video.setMessage(videoLabel());
					audio.setMessage(audioLabel());
					// 録画中は録音できない、録音中は録画できない(どちらか 1 つだけ)
					video.active = !Recorder.isAudioOnly();
					audio.active = !(Recorder.isRecording() && !Recorder.isAudioOnly());
				});
			}
		});

		// 録画中の目印(画面の左上に、赤い点と時間)。ホットバーなどと同じで、録画には映らない
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath("recmod", "indicator"),
				(graphics, tickCounter) -> {
					if (Recorder.isRecording()) {
						long s = Recorder.seconds();
						Minecraft mc = Minecraft.getInstance();
						graphics.text(mc.font, String.format("%s %s %d:%02d", s % 2 == 0 ? "●" : "○", Recorder.isAudioOnly() ? "AUDIO" : "REC", s / 60, s % 60), 6, 6, 0xFFFF4040);
					}
				});

		// 世界から出る・ゲームを終えるときは、録画を止めて保存する
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (Recorder.isRecording()) {
				Recorder.stop(client);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Recorder.stopAndWait());
	}

	private static Component videoLabel() {
		if (Recorder.isRecording() && !Recorder.isAudioOnly()) {
			long s = Recorder.seconds();
			return Component.literal(String.format("■ 停止 %d:%02d", s / 60, s % 60));
		}
		return Component.literal("● 録画");
	}

	private static Component audioLabel() {
		if (Recorder.isAudioOnly()) {
			long s = Recorder.seconds();
			return Component.literal(String.format("■ 録音停止 %d:%02d", s / 60, s % 60));
		}
		return Component.literal("♪ 録音");
	}
}
