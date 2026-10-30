package com.povreplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 動画を保存し終わるまで、画面の右上に出し続ける表示(ワールドの中でも、タイトル画面でも、どの画面でも)。
 * 保存が終わったら、保存した場所を、しばらく出す。
 */
public final class SaveIndicator {

	private static volatile String doneText;
	private static volatile long doneUntilMs;

	private SaveIndicator() {
	}

	/** 保存が終わった(または失敗した)ことを、15 秒間、右上に出す。 */
	public static void done(String text) {
		doneText = text;
		doneUntilMs = System.currentTimeMillis() + 15_000L;
	}

	/** 動画にしている最中か(ワールドを再生中、または、動画に変換中)。 */
	public static boolean busy() {
		return ReplayEngine.running() || ReplayEngine.exporting();
	}

	/** 全体の進み具合(0〜1)。ワールドの再生が前半、動画への変換が後半。 */
	public static float progress() {
		if (ReplayEngine.running()) {
			return Math.min(0.5F, ReplayEngine.progress());
		}
		return 0.5F + 0.5F * Exporter.progress();
	}

	public static void draw(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int width = graphics.guiWidth();
		if (busy()) {
			int percent = Math.round(progress() * 100);
			String where = ReplayEngine.running() ? "ワールドを再生しています" : "動画に変換しています";
			String line1 = "動画を保存中 " + percent + "%";
			String line2 = where;
			int w = Math.max(font.width(line1), font.width(line2));
			int x = width - w - 6;
			graphics.fill(x - 4, 2, width - 2, 36, 0xA0000000);
			graphics.text(font, line1, width - 6 - font.width(line1), 5, 0xFFFFD040);
			graphics.text(font, line2, width - 6 - font.width(line2), 16, 0xFFC0C0C0);
			int barW = w;
			graphics.fill(x, 28, x + barW, 32, 0xFF404040);
			graphics.fill(x, 28, x + (int) (barW * progress()), 32, 0xFF40C040);
		} else if (doneText != null && System.currentTimeMillis() < doneUntilMs) {
			String line = doneText;
			int w = font.width(line);
			graphics.fill(width - w - 10, 2, width - 2, 16, 0xA0000000);
			graphics.text(font, line, width - 6 - w, 5, 0xFF80FF80);
		}
	}
}
