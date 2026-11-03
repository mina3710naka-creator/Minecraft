package com.magicmod.client;

import com.magicmod.Element;
import com.magicmod.ModItems;
import com.magicmod.Vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** 画面左上: MP・スタミナ・選択中の魔法とわざ・クールタイム・オーバーヒート・使い魔。画面の光もここで重ねる。 */
public final class Hud {

	private Hud() {
	}

	private static int argb(int rgb) {
		return 0xFF000000 | rgb;
	}

	private static int darker(int rgb, float f) {
		int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	/** 枠つき・縦グラデーションのバー。 */
	private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac, int rgb, boolean warn, long t) {
		frac = Math.max(0, Math.min(1, frac));
		int frame = warn && (t / 5) % 2 == 0 ? 0xFFFF4040 : 0xFF101018;
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, frame);
		g.fill(x, y, x + w, y + h, 0xFF26262E);
		int fw = Math.round(w * frac);
		if (fw > 0) {
			g.fillGradient(x, y, x + fw, y + h, argb(rgb), darker(rgb, 0.55f));
			g.fill(x, y, x + fw, y + 1, 0x60FFFFFF);
		}
	}

	static void draw(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		int flash = ClientFx.flashArgb();
		if (flash != 0) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), flash);
		}
		if (mc.player == null || mc.level == null) {
			return;
		}
		Font f = mc.font;
		long now = mc.level.getGameTime();
		// 左端の縦まん中に置く(上はボスバー、下はチャットとホットバーがあるため)
		int panelH = 16 + (ClientState.spell.isEmpty() ? 0 : 22 + (ClientState.heat >= 1 ? 10 : 0)) + (ClientState.castT > 0 ? 11 : 0)
				+ (ClientState.tech.isEmpty() ? 0 : 16) + ClientState.familiars.size() * 10 + (ClientState.sp > 0 ? 10 : 0)
				+ (ClientState.combo > 0 ? 22 : 0) + (ClientState.justLeft > 0 ? 10 : 0);
		int x = 4, y = Math.max(4, g.guiHeight() / 2 - panelH / 2 - 10);
		g.fill(x - 3, y - 3, x + 182, y + panelH, 0x70000000);

		float mpFrac = ClientState.mp / Math.max(1, ClientState.maxMp);
		g.text(f, "MP", x, y, 0xFF9FC4FF, true);
		bar(g, x + 16, y + 1, 70, 6, mpFrac, 0x3E7BFF, mpFrac < 0.2f, now);
		g.text(f, String.valueOf(Math.round(ClientState.mp)), x + 90, y, 0xFFCFE0FF, true);
		g.text(f, "ST", x + 110, y, 0xFFD8F09A, true);
		bar(g, x + 124, y + 1, 50, 6, ClientState.st / Math.max(1, ClientState.maxSt), 0xB8E04A, false, now);
		y += 13;

		if (!ClientState.spell.isEmpty()) {
			Element e = Element.byId(ClientState.spellEl);
			int col = e == null ? 0xFFFFFFFF : argb(Vfx.palette(e)[e == Element.DARK ? 2 : 0]);
			if (e != null) {
				g.item(new ItemStack(ModItems.crystal(e)), x - 1, y - 2);
			}
			g.text(f, ClientState.spell, x + 17, y + 2, col, true);
			g.text(f, "§7" + ClientState.index + "/" + ClientState.count + "  §bMP " + ClientState.spellMp, x + 17 + f.width(ClientState.spell) + 6,
					y + 2, 0xFFFFFFFF, true);
			y += 16;
			long left = ClientState.spellCd - now;
			if (left > 0) {
				bar(g, x + 17, y, 110, 3, 1f - left / (float) ClientState.spellCt, 0xA0A0B8, false, now);
				g.text(f, String.format("%.1fs", left / 20f), x + 132, y - 3, 0xFFAAAAAA, true);
			} else {
				g.text(f, "§a準備完了", x + 17, y - 3, 0xFFFFFFFF, true);
			}
			y += 6;
			if (ClientState.heat >= 1) {
				g.text(f, "オーバーヒート", x + 17, y, 0xFFFF8030, true);
				bar(g, x + 90, y + 2, 60, 3, ClientState.heat / 10f, 0xFF5020, ClientState.heat >= 6, now);
				y += 10;
			}
		}
		if (ClientState.castTotal > 0 && ClientState.castT > 0) {
			Element e = Element.byId(ClientState.spellEl);
			g.text(f, "詠唱中", x + 17, y, 0xFFE0D0FF, true);
			bar(g, x + 55, y + 2, 95, 4, 1f - ClientState.castT / (float) ClientState.castTotal, e == null ? 0xC080FF : Vfx.palette(e)[0], false, now);
			y += 11;
		}
		if (!ClientState.tech.isEmpty()) {
			g.item(mc.player.getMainHandItem(), x - 1, y - 2);
			g.text(f, ClientState.tech + "  §eST " + ClientState.techSt, x + 17, y + 2, 0xFFFFD27A, true);
			y += 14;
			long left = ClientState.techCd - now;
			if (left > 0) {
				g.fill(x + 17, y - 2, x + 17 + Math.min(110, (int) left / 2), y, 0xFFAA8844);
			}
			y += 2;
		}
		if (ClientState.combo > 0) {
			int[] cols = { 0xFFFFFFFF, 0xFFFFE080, 0xFFFFA040, 0xFFFF6030, 0xFFFF40A0 };
			int col = cols[Math.min(4, ClientState.comboTier)];
			g.text(f, "COMBO " + ClientState.combo, x, y + 2, col, true);
			bar(g, x + 70, y + 4, 90, 4, ClientState.comboLeft / 80f, col & 0xFFFFFF, ClientState.comboLeft < 20, now);
			y += 12;
		}
		if (ClientState.justLeft > 0) {
			g.text(f, "ジャスト回避 強化中", x, y, 0xFF9FE8FF, true);
			y += 10;
		}
		for (String s : ClientState.familiars) {
			g.text(f, "◆ " + s, x, y, 0xFF9AE6E0, true);
			y += 10;
		}
		if (ClientState.sp > 0) {
			g.text(f, "スキルポイント " + ClientState.sp + " §7[K]", x, y, 0xFF7FE8FF, true);
		}
	}
}
