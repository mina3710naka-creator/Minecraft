package com.povreplay;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * リプレイ方式の録画。メニュー(Esc)の左上に「録画」ボタンを足す。
 * プレイ中は、ゲームの通信と視点の状態を記録するだけ(軽い)。止めたあと、ワールドを出ると、リプレイを再生し直して、高画質の動画にする。
 */
public class PovReplay implements ClientModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("povreplay");

	private static boolean leftoverChecked;
	private static int titleTicks;

	@Override
	public void onInitializeClient() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			// どの画面でも、動画を保存し終わるまで、右上に表示を出す
			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> SaveIndicator.draw(graphics));
			if (screen instanceof PauseScreen) {
				Button record = Button.builder(recordLabel(), b -> {
					LiveRecording.toggle(Minecraft.getInstance());
					b.setMessage(recordLabel());
				}).bounds(6, 6, 130, 20).build();
				Button save = Button.builder(Component.literal("保存して出る"), b -> saveAndQuit(Minecraft.getInstance()))
						.bounds(6, 30, 130, 20).build();
				Screens.getWidgets(screen).add(record);
				Screens.getWidgets(screen).add(save);
				ScreenEvents.afterTick(screen).register(s -> {
					record.setMessage(recordLabel());
					save.active = !LiveRecording.isRecording() && !LiveRecording.pending().isEmpty();
					save.visible = !LiveRecording.pending().isEmpty();
				});
			}
		});

		// 録画中の目印と、保存待ちの数(画面の左上)。ホットバーなどと同じで、動画には映らない
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath("povreplay", "indicator"),
				(graphics, tickCounter) -> {
					Minecraft mc = Minecraft.getInstance();
					SaveIndicator.draw(graphics);
					if (LiveRecording.isRecording()) {
						long s = LiveRecording.seconds();
						graphics.text(mc.font, String.format("%s REC %d:%02d", s % 2 == 0 ? "●" : "○", s / 60, s % 60), 6, 6, 0xFFFF4040);
					} else if (!LiveRecording.pending().isEmpty() && !ReplayMode.active()) {
						graphics.text(mc.font, "保存待ち " + LiveRecording.pending().size() + " 件(ワールドを出ると動画にします)", 6, 6, 0xFFFFD040);
					}
				});

		// ワールドから出たとき: 録画中なら止めて、保存待ちの動画を作り始める
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (ReplayMode.active()) {
				return;
			}
			LiveRecording.stopIfRecording(client);
			java.nio.file.Path session = Sessions.current() == null ? null : Sessions.current().path();
			Sessions.end();
			if (session != null && LiveRecording.pending().stream().noneMatch(j -> j.session().equals(session))) {
				try {
					java.nio.file.Files.deleteIfExists(session);   // 録画に使わなかった記録は、消す
				} catch (java.io.IOException e) {
					LOGGER.warn("記録を消せませんでした: " + session, e);
				}
			}
			ReplayEngine.runNextSoon = true;
		});

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (!leftoverChecked) {
				leftoverChecked = true;
				LiveRecording.addPending(ExportJob.loadAll(client.gameDirectory.toPath()));
				deleteOrphanSessions(client);
				if (!LiveRecording.pending().isEmpty()) {
					LOGGER.info("前回の保存待ちが " + LiveRecording.pending().size() + " 件あります");
					ReplayEngine.runNextSoon = true;
				}
			}
			if (ReplayEngine.runNextSoon && client.level == null && !ReplayEngine.running()) {
				if (client.gui.screen() instanceof TitleScreen) {
					// タイトル画面が出てから、少し待って始める
					if (++titleTicks > 40) {
						titleTicks = 0;
						ReplayEngine.runNextSoon = false;
						ReplayEngine.runPending(client);
					}
				}
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			LiveRecording.stopIfRecording(client);
			Sessions.end();
		});
	}

	/** 前回のゲームが録画に使わないまま残した記録(保存待ちの仕事が使っていないもの)を消す。 */
	private static void deleteOrphanSessions(Minecraft client) {
		deleteOrphanTemp(client);
		java.nio.file.Path dir = client.gameDirectory.toPath().resolve("povreplay").resolve("sessions");
		if (!java.nio.file.Files.isDirectory(dir)) {
			return;
		}
		try (var files = java.nio.file.Files.list(dir)) {
			for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
				if (f.getFileName().toString().endsWith(".povr") && LiveRecording.pending().stream().noneMatch(j -> j.session().equals(f))) {
					java.nio.file.Files.deleteIfExists(f);
				}
			}
		} catch (java.io.IOException e) {
			LOGGER.warn("古い記録を消せませんでした", e);
		}
	}

	/** 保存待ちの仕事が使っていない、音・絵の一時ファイルを消す(失敗したときなどに残ったもの)。 */
	private static void deleteOrphanTemp(Minecraft client) {
		java.nio.file.Path dir = client.gameDirectory.toPath().resolve("povreplay").resolve("tmp");
		if (!java.nio.file.Files.isDirectory(dir)) {
			return;
		}
		try (var files = java.nio.file.Files.list(dir)) {
			for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files::iterator) {
				boolean used = LiveRecording.pending().stream().anyMatch(j -> j.pcm().equals(f) || java.nio.file.Path.of(j.pcm() + ".video.tmp").equals(f));
				if (!used) {
					java.nio.file.Files.deleteIfExists(f);
				}
			}
		} catch (java.io.IOException e) {
			LOGGER.warn("古い一時ファイルを消せませんでした", e);
		}
	}

	private static Component recordLabel() {
		if (LiveRecording.isRecording()) {
			long s = LiveRecording.seconds();
			return Component.literal(String.format("■ 停止 %d:%02d", s / 60, s % 60));
		}
		return Component.literal("● 録画");
	}

	/** ワールドから出て、保存待ちの動画を作る(メニューの「保存して出る」)。 */
	private static void saveAndQuit(Minecraft mc) {
		if (mc.level != null) {
			mc.disconnectFromWorld(Component.translatable("menu.savingLevel"));
			mc.gui.setScreen(new TitleScreen());
		}
	}

	/** チャットに知らせる。タイトル画面ではチャットが見えないので、画面の隅の通知(トースト)も出す。 */
	public static void message(Minecraft mc, Component text) {
		mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(text));
	}

	/** 画面の隅に通知を出す(タイトル画面でも見える)。 */
	public static void toast(Minecraft mc, String title, String message) {
		mc.execute(() -> net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(mc.gui.toastManager(),
				net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(title),
				Component.literal(message)));
	}
}
