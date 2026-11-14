package com.effectmod.client;

import java.util.List;

import com.effectmod.BenchMenu;
import com.effectmod.Entry;
import com.effectmod.ModContent;
import com.effectmod.Net;
import com.effectmod.Parser;
import com.effectmod.Presets;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 付与台の画面。文章を書いて「付与」を押す。よく使う効果はボタンで入力できる。 */
public class BenchScreen extends AbstractContainerScreen<BenchMenu> {

	private static final int COLS = 2;
	private static final int ROWS = 5;
	private static final int PER_PAGE = COLS * ROWS;

	private EditBox box;
	private int page;
	private List<String[]> presets = List.of();
	private String lastKey = "";
	private String status = "";
	private boolean statusOk = true;

	public BenchScreen(BenchMenu menu, Inventory inv, Component title) {
		super(menu, inv, title, 256, 262);
		this.inventoryLabelX = BenchMenu.INV_X;
		this.inventoryLabelY = BenchMenu.INV_Y - 10;
	}

	public void status(String msg, boolean ok) {
		this.status = msg;
		this.statusOk = ok;
	}

	@Override
	protected void init() {
		super.init();
		int x = leftPos;
		int y = topPos;
		// よく使う効果のボタン(2列 x 5行)。対象のアイテムに付けられるものだけ
		lastKey = itemKey();
		presets = Presets.forItem(menu.target());
		int pages0 = Math.max(1, (presets.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages0 - 1);
		int start = page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && start + i < presets.size(); i++) {
			String[] p = presets.get(start + i);
			int bx = x + 100 + (i % COLS) * 78;
			int by = y + 22 + (i / COLS) * 18;
			addRenderableWidget(Button.builder(Component.literal(p[0]), b -> {
				box.setValue(p[1]);
				box.moveCursorToEnd(false);
			}).bounds(bx, by, 76, 16).build());
		}
		int pages = pages0;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> setPage((page + pages - 1) % pages))
				.bounds(x + 100, y + 113, 20, 14).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> setPage((page + 1) % pages))
				.bounds(x + 122, y + 113, 20, 14).build());

		String old = box == null ? "" : box.getValue();
		box = new EditBox(font, x + 8, y + 133, 240, 18, Component.literal("効果の文章"));
		box.setMaxLength(Parser.MAX_TEXT);
		box.setHint(Component.literal("効果を文章で書く 例: 攻撃したとき雷を落とす 3"));
		box.setValue(old);
		addRenderableWidget(box);
		setInitialFocus(box);

		addRenderableWidget(Button.builder(Component.literal("付与"), b -> {
			ClientPlayNetworking.send(new Net.ApplyPayload(0, box.getValue()));
		}).bounds(x + 8, y + 112, 40, 16).build());
		addRenderableWidget(Button.builder(Component.literal("全削除"), b -> {
			ClientPlayNetworking.send(new Net.ApplyPayload(1, ""));
		}).bounds(x + 50, y + 112, 44, 16).build());
	}

	private String itemKey() {
		ItemStack st = menu.target();
		return st.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (!itemKey().equals(lastKey)) { // 入れたアイテムが変わったら、ボタンを入れ替える
			rebuildWidgets();
		}
	}

	private void setPage(int p) {
		page = p;
		rebuildWidgets();
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == 256) { // Esc
			onClose();
			return true;
		}
		// 文章を打っている間は、E キーなどで画面が閉じないようにする
		return box.keyPressed(e) || box.canConsumeInput() || super.keyPressed(e);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		super.extractBackground(g, mx, my, a);
		int x = leftPos;
		int y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, 0xF01c1830);
		g.outline(x, y, imageWidth, imageHeight, 0xFF9b6bff);
		for (Slot s : menu.slots) {
			g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, 0xFF8b8b8b);
			g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, 0xFF2b2b36);
		}
		g.text(font, title, x + 8, y + 6, 0xFFe8d8ff, false);
		g.text(font, "対象", x + 32, y + 26, 0xFFcfc4e8, false);
		g.text(font, "付与できる数 " + BenchMenu.MAX_EFFECTS + "個まで", x + 8, y + 43, 0xFF8f86a8, false);
		if (menu.target().isEmpty()) {
			g.text(font, "アイテムを入れると専用の効果が出ます", x + 100, y + 8, 0xFFd9b8ff, false);
		}

		// いま付いている効果
		ItemStack st = menu.target();
		List<Entry> es = st.getOrDefault(ModContent.EFFECTS, List.<Entry>of());
		int ty = y + 55;
		for (Entry e : es) {
			for (var line : font.split(Component.literal("・" + Parser.label(e)), 88)) {
				g.text(font, line, x + 8, ty, 0xFFd9b8ff, false);
				ty += 10;
			}
		}
		if (es.isEmpty() && !st.isEmpty()) {
			g.text(font, "効果なし", x + 8, ty, 0xFF8f86a8, false);
		}

		int pages = Math.max(1, (presets.size() + PER_PAGE - 1) / PER_PAGE);
		g.text(font, (page + 1) + " / " + pages, x + 148, y + 116, 0xFFcfc4e8, false);

		// 入力中の文章の解釈(プレビュー)
		String v = box == null ? "" : box.getValue();
		if (!v.isBlank()) {
			Parser.Result r = Parser.parse(v, st);
			if (r.ok()) {
				g.text(font, font.plainSubstrByWidth("解釈: " + Parser.label(r.entry()) + "  (必要Lv" + Parser.cost(r.entry()) + ")", 244),
						x + 8, y + 155, 0xFF9be89b, false);
			} else {
				g.text(font, font.plainSubstrByWidth("解釈できません: " + r.error(), 244), x + 8, y + 155, 0xFFff9b9b, false);
			}
		}
		if (!status.isEmpty()) {
			g.text(font, font.plainSubstrByWidth(status, 244), x + 8, y + 166, statusOk ? 0xFF9be89b : 0xFFff9b9b, false);
		}
	}
}
