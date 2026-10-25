package com.lampvideo.client;

import java.lang.reflect.Field;

import com.lampvideo.pc.HeadphoneSlot;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import com.lampvideo.pc.HeadphoneTogglePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

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
			if (screen instanceof CreativeModeInventoryScreen) {
				setUpCreative(screen);
			} else if (screen instanceof InventoryScreen) {
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
			initReflection();
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

	// ---- クリエイティブ ----
	// クリエイティブの持ち物画面には、スロットを足せない。代わりに、兜のスロット(54,6)を指したとき、その横に、
	// ヘッドホンを「着ける/外す」ボタンを出す(アイテムは、いくらでも出せるので、スロットでなくても、困らない)。

	private static void setUpCreative(Screen screen) {
		final boolean[] shown = { false };
		final long[] lastHover = { 0 };
		final Button button = Button.builder(Component.literal("聴"), b -> ClientPlayNetworking.send(new HeadphoneTogglePayload()))
				.bounds(0, 0, 18, 18).build();
		ScreenEvents.afterTick(screen).register(s -> {
			long now = System.currentTimeMillis();
			boolean visible = false;
			if (!reflectionTried) {
				initReflection();
			}
			if (hoveredField != null && positionField(screen)) {
				try {
					Slot hovered = (Slot) hoveredField.get(screen);
					if (hovered != null && hovered.x == 54 && hovered.y == 6) {
						lastHover[0] = now;
					}
				} catch (IllegalAccessException | RuntimeException e) {
					// 読めなければ、出さない
				}
				visible = now - lastHover[0] < HOLD_MS;
			}
			java.util.List<AbstractWidget> widgets = Screens.getWidgets(screen);
			if (visible && !shown[0]) {
				button.setPosition(creativeLeft + 54 + 20, creativeTop + 5);
				widgets.add(button);
				shown[0] = true;
			} else if (!visible && shown[0]) {
				widgets.remove(button);
				shown[0] = false;
			}
		});
	}

	private static Field leftPosField;
	private static Field topPosField;
	private static int creativeLeft;
	private static int creativeTop;

	private static void initReflection() {
		reflectionTried = true;
		try {
			hoveredField = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
			hoveredField.setAccessible(true);
			leftPosField = AbstractContainerScreen.class.getDeclaredField("leftPos");
			leftPosField.setAccessible(true);
			topPosField = AbstractContainerScreen.class.getDeclaredField("topPos");
			topPosField.setAccessible(true);
		} catch (ReflectiveOperationException | RuntimeException e) {
			hoveredField = null;
		}
	}

	/** 画面の左上の位置を読む。読めなければ、画面の中央に置いたときの位置。 */
	private static boolean positionField(Screen screen) {
		try {
			creativeLeft = leftPosField != null ? leftPosField.getInt(screen) : (screen.width - 195) / 2;
			creativeTop = topPosField != null ? topPosField.getInt(screen) : (screen.height - 136) / 2;
			return true;
		} catch (IllegalAccessException | RuntimeException e) {
			return false;
		}
	}
}
