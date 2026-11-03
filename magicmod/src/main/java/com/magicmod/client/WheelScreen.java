package com.magicmod.client;

import java.util.List;

import com.magicmod.Element;
import com.magicmod.MagicMod;
import com.magicmod.Net;
import com.magicmod.Vfx;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 魔法ホイール: キーを押している間だけ開き、マウスを向けた魔法を、キーを離した瞬間に選ぶ。
 * 杖に入っている魔法が多いときは、内側と外側の 2 重の輪になる。
 */
public class WheelScreen extends Screen {

	private final int keyCode;
	private int hover;

	public WheelScreen(int keyCode) {
		super(Component.literal("魔法ホイール"));
		this.keyCode = keyCode;
		this.hover = ClientState.sel;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	/** i 番目の魔法の中心(画面座標)。 */
	private int[] pos(int i, int n) {
		int cx = width / 2, cy = height / 2;
		int inner = n <= 12 ? n : (n + 1) / 2;
		boolean outer = n > 12 && i >= inner;
		int idx = outer ? i - inner : i;
		int count = outer ? n - inner : inner;
		double r = n <= 12 ? 60 : outer ? 98 : 56;
		double a = -Math.PI / 2 + 2 * Math.PI * idx / Math.max(1, count) + (outer ? Math.PI / Math.max(1, count) : 0);
		return new int[] { (int) Math.round(cx + Math.cos(a) * r), (int) Math.round(cy + Math.sin(a) * r) };
	}

	@Override
	public void tick() {
		if (!InputConstants.isKeyDown(keyCode)) {
			finish();
		}
	}

	private void finish() {
		if (hover >= 0 && hover < ClientState.wheel.size() && hover != ClientState.sel) {
			ClientPlayNetworking.send(new Net.KeyPayload("select", hover));
		}
		onClose();
	}

	private static ItemStack icon(String id) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MagicMod.MOD_ID, id)));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		List<ClientState.WheelEntry> l = ClientState.wheel;
		int n = l.size();
		int cx = width / 2, cy = height / 2;
		// 暗い円盤
		int R = n > 12 ? 120 : 82;
		for (int dy = -R; dy <= R; dy++) {
			int w = (int) Math.sqrt((double) R * R - (double) dy * dy);
			g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, 0xA0101018);
		}
		if (n == 0) {
			g.centeredText(font, "杖に魔法がありません", cx, cy - 4, 0xFFAAAAAA);
			return;
		}
		// マウスに一番近い魔法
		double best = 30 * 30;
		for (int i = 0; i < n; i++) {
			int[] p = pos(i, n);
			double d = (mx - p[0]) * (double) (mx - p[0]) + (my - p[1]) * (double) (my - p[1]);
			if (d < best) {
				best = d;
				hover = i;
			}
		}
		for (int i = 0; i < n; i++) {
			int[] p = pos(i, n);
			ClientState.WheelEntry e = l.get(i);
			Element el = Element.byId(e.element());
			int col = el == null ? 0xFFFFFFFF : 0xFF000000 | Vfx.palette(el)[0];
			boolean h = i == hover, cur = i == ClientState.sel;
			if (h || cur) {
				int s = h ? 14 : 12;
				g.fill(p[0] - s, p[1] - s, p[0] + s, p[1] + s, h ? (col & 0x00FFFFFF) | 0xC0000000 : 0x80FFFFFF);
				g.fill(p[0] - s + 2, p[1] - s + 2, p[0] + s - 2, p[1] + s - 2, 0xE0101018);
			}
			g.item(icon(e.icon()), p[0] - 8, p[1] - 8);
		}
		ClientState.WheelEntry e = l.get(Math.max(0, Math.min(n - 1, hover)));
		Element el = Element.byId(e.element());
		int col = el == null ? 0xFFFFFFFF : 0xFF000000 | Vfx.palette(el)[el == Element.DARK ? 2 : 0];
		g.centeredText(font, e.name() + (e.synth() ? " ◆" : ""), cx, cy - 12, col);
		g.centeredText(font, (el == null ? "" : el.ja + "  ") + "MP " + e.mp(), cx, cy, 0xFFCFE0FF);
		g.centeredText(font, "§7キーを離すと選択", cx, cy + 12, 0xFFFFFFFF);
	}
}
