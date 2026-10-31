package com.povreplay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * 動画にしている間に出す画面。進み具合と、ボタン(最小化・プレビューの入/切・中止)を出す。
 *  - 大きい表示(標準): 画面の真ん中に、進み具合のパネル。
 *  - 小さい表示(最小化): 画面の右上に、進み具合と「拡大」のボタンだけ。「拡大」のボタン(または Esc)で、大きい表示に戻る。
 * プレビュー(再生しているワールドの表示)は、切ると、背景が真っ暗になる。
 */
public class ExportScreen extends Screen {

	/** 小さい表示にしているか(次に画面を作り直しても、引き継ぐ)。 */
	private static boolean minimized;
	/** 再生しているワールドを、背景に見せるか。 */
	private static boolean preview = true;

	public ExportScreen() {
		super(Component.literal("動画を作っています"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void init() {
		if (minimized) {
			// 最小化: 「拡大」のボタンだけ残す(進み具合は、その上に小さく出す)
			int w = 62;
			addRenderableWidget(Button.builder(Component.literal("拡大"), b -> setMinimized(false)).bounds(this.width - 4 - w - 66, 36, w, 16).build());
		} else {
			int cx = this.width / 2;
			int y = this.height / 2 + 18;
			addRenderableWidget(Button.builder(Component.literal("最小化"), b -> setMinimized(true)).bounds(cx - 155, y, 100, 20).build());
			addRenderableWidget(Button.builder(previewLabel(false), b -> togglePreview(b)).bounds(cx - 50, y, 100, 20).build());
			addRenderableWidget(Button.builder(Component.literal("中止"), b -> ReplayEngine.cancel()).bounds(cx + 55, y, 100, 20).build());
			// 右上の最小化ボタン(ウィンドウの「－」のように)
			addRenderableWidget(Button.builder(Component.literal("－"), b -> setMinimized(true)).bounds(this.width - 24, 4, 20, 20).build());
		}
	}

	private static Component previewLabel(boolean compact) {
		return Component.literal((compact ? "表示: " : "プレビュー: ") + (preview ? "入" : "切"));
	}

	private void togglePreview(Button button) {
		preview = !preview;
		this.rebuildWidgets();
	}

	private void setMinimized(boolean value) {
		minimized = value;
		this.rebuildWidgets();
	}

	/** 背景のぼかしと暗幕は、なし(再生しているワールドを、そのまま見せる。「プレビュー: 切」のときは、自分で黒く塗る)。 */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			setMinimized(!minimized);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (!preview) {
			graphics.fill(0, 0, this.width, this.height, 0xFF101010);
		}
		int percent = Math.round(ReplayEngine.progress() * 100);
		if (minimized) {
			int w = 194;
			int x = this.width - 4 - w;
			graphics.fill(x, 2, x + w, 56, 0xC0000000);
			graphics.text(this.font, Component.literal("動画を保存中 " + percent + "%"), x + 6, 6, 0xFFFFD040);
			graphics.text(this.font, Component.literal(shorten(ReplayEngine.statusText(), w - 12)), x + 6, 18, 0xFFC0C0C0);
			graphics.fill(x + 6, 29, x + w - 6, 33, 0xFF404040);
			graphics.fill(x + 6, 29, x + 6 + (int) ((w - 12) * ReplayEngine.progress()), 33, 0xFF40C040);
		} else {
			int cx = this.width / 2;
			int y = this.height / 2 - 50;
			int panelW = Math.min(340, this.width - 20);
			graphics.fill(cx - panelW / 2, y - 10, cx + panelW / 2, y + 70, 0xC0000000);
			graphics.centeredText(this.font, Component.literal("動画を作っています  " + percent + "%"), cx, y, 0xFFFFFFFF);
			graphics.centeredText(this.font, Component.literal(shorten(ReplayEngine.statusText(), panelW - 20)), cx, y + 14, 0xFFC0C0C0);
			int barW = panelW - 30;
			int x0 = cx - barW / 2;
			graphics.fill(x0, y + 28, x0 + barW, y + 38, 0xFF404040);
			graphics.fill(x0, y + 28, x0 + (int) (barW * ReplayEngine.progress()), y + 38, 0xFF40C040);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** 長い文は、幅に収まるように切る。 */
	private String shorten(String text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) {
			return text;
		}
		String t = text;
		while (t.length() > 1 && this.font.width(t + "…") > maxWidth) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}
}
