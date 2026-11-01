package com.magicmod.client;

import com.magicmod.Element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 画面左上: MP・スタミナ・選択中の魔法とわざ・クールタイム・オーバーヒート・使い魔。 */
public final class Hud {

	private Hud() {
	}

	private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac, int color) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
		g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1, frac))), y + h, color);
	}

	static void draw(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		Font f = mc.font;
		long now = mc.level.getGameTime();
		int x = 4, y = 4;
		bar(g, x, y + 9, 90, 4, ClientState.mp / Math.max(1, ClientState.maxMp), 0xFF3E7BFF);
		g.text(f, "MP " + Math.round(ClientState.mp) + "/" + Math.round(ClientState.maxMp), x, y, 0xFF9FC4FF, true);
		bar(g, x + 100, y + 9, 70, 4, ClientState.st / Math.max(1, ClientState.maxSt), 0xFFB8E04A);
		g.text(f, "ST " + Math.round(ClientState.st) + "/" + Math.round(ClientState.maxSt), x + 100, y, 0xFFD8F09A, true);
		y += 18;
		if (!ClientState.spell.isEmpty()) {
			Element e = Element.byId(ClientState.spellEl);
			int col = e == null ? 0xFFFFFFFF : 0xFF000000 | e.color;
			if (e == Element.DARK) {
				col = 0xFFB090E0;
			}
			g.text(f, "▶ " + ClientState.spell + " §7(" + ClientState.index + "/" + ClientState.count + ")  MP " + ClientState.spellMp, x, y, col,
					true);
			y += 10;
			long left = ClientState.spellCd - now;
			if (left > 0) {
				bar(g, x, y, 90, 2, 1f - left / (float) ClientState.spellCt, 0xFFAAAAAA);
				y += 4;
			}
			if (ClientState.heat >= 1) {
				g.text(f, "オーバーヒート ×" + (int) ClientState.heat, x, y, 0xFFFF8030, true);
				y += 10;
			}
		}
		if (ClientState.castTotal > 0 && ClientState.castT > 0) {
			g.text(f, "詠唱中…", x, y, 0xFFE0D0FF, true);
			bar(g, x + 40, y + 3, 50, 3, 1f - ClientState.castT / (float) ClientState.castTotal, 0xFFC080FF);
			y += 10;
		}
		if (!ClientState.tech.isEmpty()) {
			g.text(f, "わざ: " + ClientState.tech + "  ST " + ClientState.techSt, x, y, 0xFFFFD27A, true);
			y += 10;
			long left = ClientState.techCd - now;
			if (left > 0) {
				g.fill(x, y, x + Math.min(90, (int) left / 2), y + 2, 0xFFAA8844);
				y += 4;
			}
		}
		for (String s : ClientState.familiars) {
			g.text(f, "◆ " + s, x, y, 0xFF9AE6E0, true);
			y += 10;
		}
		if (ClientState.sp > 0) {
			g.text(f, "SP " + ClientState.sp + " [K]", x, y, 0xFF7FE8FF, true);
		}
	}
}
