package com.lampvideo.client;

import java.lang.reflect.Field;

import com.lampvideo.pc.HeadphoneSlot;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;

/**
 * ヘッドホン専用スロットは、持ち物画面で、兜のスロットにカーソルを合わせたときだけ、その横に出てくる。
 * (スロット自体は、画面を開いている間ずっとあるが、兜のスロットの近くにカーソルがないときは、描画も、クリックも受け付けない)
 * 画面が持っている「いま指しているスロット」を見て、兜のスロットか、ヘッドホンのスロットを指している間(と、離れてから少しの間)だけ出す。
 */
public final class HeadphoneUi {

	/** 兜のスロットの番号(InventoryMenu: 0=作業結果、1〜4=作業台、5〜8=防具(5が兜))。 */
	private static final int HELMET_SLOT_INDEX = 5;
	/** カーソルが兜のスロットからヘッドホンのスロットへ動く間に消えないよう、離れてからも出しておく時間(ミリ秒)。 */
	private static final long HOLD_MS = 350;

	private static Screen current;
	private static Field hoveredField;
	private static boolean reflectionTried;
	private static long lastHoverMs;

	private HeadphoneUi() {
	}

	public static void init() {
		HeadphoneSlot.ACTIVE = HeadphoneUi::visible;
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof InventoryScreen) {
				current = screen;
				lastHoverMs = 0;
				ScreenEvents.remove(screen).register(closed -> {
					if (current == closed) {
						current = null;
					}
				});
			}
		});
	}

	private static boolean visible() {
		Screen s = current;
		if (s == null) {
			return false;
		}
		if (!reflectionTried) {
			reflectionTried = true;
			try {
				hoveredField = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
				hoveredField.setAccessible(true);
			} catch (ReflectiveOperationException | RuntimeException e) {
				hoveredField = null;
			}
		}
		if (hoveredField == null) {
			return true;   // 指しているスロットを読めない環境では、いつも出しておく(使えなくなるよりよい)
		}
		long now = System.currentTimeMillis();
		try {
			Slot hovered = (Slot) hoveredField.get(s);
			if (hovered != null && (hovered.container instanceof com.lampvideo.pc.HeadphoneContainer
					|| hovered.index == HELMET_SLOT_INDEX)) {
				lastHoverMs = now;
			}
		} catch (IllegalAccessException | RuntimeException e) {
			return true;
		}
		return now - lastHoverMs < HOLD_MS;
	}
}
