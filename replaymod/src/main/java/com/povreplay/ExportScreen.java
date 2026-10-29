package com.povreplay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 動画にしている間に出す画面。進み具合を見せる。Esc で中止できる。 */
public class ExportScreen extends Screen {

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
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.isEscape()) {
			ReplayEngine.cancel();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xFF101010);
		int cx = this.width / 2;
		int y = this.height / 2 - 30;
		graphics.centeredText(this.font, Component.literal("動画を作っています"), cx, y, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.literal(ReplayEngine.statusText()), cx, y + 16, 0xFFC0C0C0);
		int barW = Math.min(300, this.width - 40);
		int x0 = cx - barW / 2;
		graphics.fill(x0, y + 36, x0 + barW, y + 46, 0xFF404040);
		graphics.fill(x0, y + 36, x0 + (int) (barW * ReplayEngine.progress()), y + 46, 0xFF40C040);
		graphics.centeredText(this.font, Component.literal("Esc で中止"), cx, y + 58, 0xFF808080);
	}
}
