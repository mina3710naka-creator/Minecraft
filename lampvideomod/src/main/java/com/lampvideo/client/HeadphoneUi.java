package com.lampvideo.client;

import java.lang.reflect.Field;

import org.lwjgl.glfw.GLFW;

import com.lampvideo.pc.HeadphoneSlot;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * ヘッドホン専用スロットは、持ち物画面で、兜のスロットにカーソルを合わせたときだけ、その横に出てくる。
 * (スロット自体は、画面を開いている間ずっとあるが、カーソルが兜のスロットの近くにないときは、描画も、クリックも受け付けない)
 */
public final class HeadphoneUi {

	private static Screen current;
	private static Field leftPosField;
	private static Field topPosField;
	private static boolean reflectionTried;

	// 描画スレッドだけが使う(毎フレーム何度も呼ばれるので、配列を使い回す)
	private static final double[] CURSOR_X = new double[1];
	private static final double[] CURSOR_Y = new double[1];
	private static final int[] WINDOW_W = new int[1];
	private static final int[] WINDOW_H = new int[1];

	private HeadphoneUi() {
	}

	public static void init() {
		HeadphoneSlot.ACTIVE = HeadphoneUi::visible;
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof InventoryScreen) {
				current = screen;
				ScreenEvents.remove(screen).register(closed -> {
					if (current == closed) {
						current = null;
					}
				});
			}
		});
	}

	private static int field(Field f, Screen s, int fallback) {
		try {
			if (f != null) {
				return f.getInt(s);
			}
		} catch (IllegalAccessException | RuntimeException e) {
			// 読めなければ、画面の中央に置いたときの位置を使う
		}
		return fallback;
	}

	/** カーソルが、兜のスロットとその横(ヘッドホンのスロットが出る場所)の上にあるか。 */
	private static boolean visible() {
		Screen s = current;
		if (s == null) {
			return false;
		}
		if (!reflectionTried) {
			reflectionTried = true;
			try {
				leftPosField = AbstractContainerScreen.class.getDeclaredField("leftPos");
				leftPosField.setAccessible(true);
				topPosField = AbstractContainerScreen.class.getDeclaredField("topPos");
				topPosField.setAccessible(true);
			} catch (ReflectiveOperationException | RuntimeException e) {
				leftPosField = null;
				topPosField = null;
			}
		}
		long window = GLFW.glfwGetCurrentContext();
		if (window == 0L) {
			return false;
		}
		GLFW.glfwGetCursorPos(window, CURSOR_X, CURSOR_Y);
		GLFW.glfwGetWindowSize(window, WINDOW_W, WINDOW_H);
		if (WINDOW_W[0] <= 0 || WINDOW_H[0] <= 0) {
			return false;
		}
		double mx = CURSOR_X[0] * s.width / WINDOW_W[0];
		double my = CURSOR_Y[0] * s.height / WINDOW_H[0];
		int left = field(leftPosField, s, (s.width - 176) / 2);
		int top = field(topPosField, s, (s.height - 166) / 2);
		return mx >= left + 7 && mx <= left + 45 && my >= top + 7 && my <= top + 27;
	}
}
