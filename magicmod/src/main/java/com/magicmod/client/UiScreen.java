package com.magicmod.client;

import java.util.ArrayList;
import java.util.List;

import com.magicmod.Net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** テーブル・祭壇・ステータスの画面(中身はサーバーから届く)。 */
public class UiScreen extends Screen {

	private CompoundTag model;
	private int page;
	private int rowsTop;
	private int perPage;
	private final List<int[]> rowBoxes = new ArrayList<>();
	private final List<List<Component>> rowTips = new ArrayList<>();

	public UiScreen(CompoundTag model) {
		super(Component.literal(model.getStringOr("title", "")));
		this.model = model;
	}

	public String screenId() {
		return model.getStringOr("screen", "");
	}

	public void update(CompoundTag m) {
		this.model = m;
		rebuildWidgets();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void send(String act) {
		ClientPlayNetworking.send(new Net.UiActionPayload(screenId(), act));
	}

	private int w() {
		return Math.min(400, width - 16);
	}

	@Override
	protected void init() {
		int w = w();
		int x0 = (width - w) / 2;
		int y = 18;
		ListTag tabs = model.getListOrEmpty("tabs");
		if (!tabs.isEmpty()) {
			int tw = w / tabs.size();
			for (int i = 0; i < tabs.size(); i++) {
				CompoundTag t = (CompoundTag) tabs.get(i);
				String id = t.getStringOr("id", "");
				boolean on = t.getBooleanOr("on", false);
				Button b = Button.builder(Component.literal((on ? "§e" : "") + t.getStringOr("label", "")), btn -> {
					page = 0;
					send("tab:" + id);
				}).bounds(x0 + i * tw, y, tw - 2, 18).build();
				b.active = !on;
				addRenderableWidget(b);
			}
			y += 20;
		}
		ListTag subs = model.getListOrEmpty("subs");
		if (!subs.isEmpty()) {
			int sw = w / subs.size();
			for (int i = 0; i < subs.size(); i++) {
				CompoundTag t = (CompoundTag) subs.get(i);
				String id = t.getStringOr("id", "");
				boolean on = t.getBooleanOr("on", false);
				Button b = Button.builder(Component.literal((on ? "§e" : "§7") + t.getStringOr("label", "")), btn -> {
					page = 0;
					send("sub:" + id);
				}).bounds(x0 + i * sw, y, sw - 1, 14).build();
				b.active = !on;
				addRenderableWidget(b);
			}
			y += 16;
		}
		int infoLines = 0;
		for (Tag t : model.getListOrEmpty("info")) {
			infoLines += Math.max(1, font.split(Component.literal(t.asString().orElse("")), w).size());
		}
		y += 2 + infoLines * 10 + 2;
		rowsTop = y;
		perPage = Math.max(3, (height - rowsTop - 28) / 20);
		ListTag rows = model.getListOrEmpty("rows");
		int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
		page = Math.min(page, pages - 1);
		rowBoxes.clear();
		rowTips.clear();
		for (int i = 0; i < perPage; i++) {
			int idx = page * perPage + i;
			if (idx >= rows.size()) {
				break;
			}
			CompoundTag r = (CompoundTag) rows.get(idx);
			int ry = rowsTop + i * 20;
			String btn = r.getStringOr("btn", "");
			String btn2 = r.getStringOr("btn2", "");
			int bw = 58, bw2 = btn2.isEmpty() ? 0 : 46;
			if (!btn.isEmpty()) {
				String act = r.getStringOr("act", "");
				Button b = Button.builder(Component.literal(btn), x -> send(act)).bounds(x0 + w - bw - bw2 - (bw2 > 0 ? 2 : 0), ry, bw, 18).build();
				b.active = r.getBooleanOr("en", false);
				addRenderableWidget(b);
			}
			if (!btn2.isEmpty()) {
				String act2 = r.getStringOr("act2", "");
				Button b = Button.builder(Component.literal(btn2), x -> send(act2)).bounds(x0 + w - bw2, ry, bw2, 18).build();
				b.active = r.getBooleanOr("en2", false);
				addRenderableWidget(b);
			}
			int textW = w - (btn.isEmpty() ? 0 : bw + 4) - (bw2 > 0 ? bw2 + 2 : 0);
			rowBoxes.add(new int[] { x0, ry, textW, idx });
			List<Component> tip = new ArrayList<>();
			for (Tag t : r.getListOrEmpty("tip")) {
				tip.add(Component.literal(t.asString().orElse("")));
			}
			rowTips.add(tip);
		}
		int by = height - 24;
		Button prev = Button.builder(Component.literal("◀"), b -> {
			page--;
			rebuildWidgets();
		}).bounds(x0, by, 30, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		Button label = Button.builder(Component.literal((page + 1) + " / " + pages), b -> { }).bounds(x0 + 32, by, 60, 20).build();
		label.active = false;
		addRenderableWidget(label);
		Button next = Button.builder(Component.literal("▶"), b -> {
			page++;
			rebuildWidgets();
		}).bounds(x0 + 94, by, 30, 20).build();
		next.active = page < pages - 1;
		addRenderableWidget(next);
		addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose()).bounds(x0 + w - 80, by, 80, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		super.extractRenderState(g, mx, my, a);
		int w = w();
		int x0 = (width - w) / 2;
		g.centeredText(font, "§l" + model.getStringOr("title", ""), width / 2, 6, 0xFFFFE9A8);
		int y = rowsTop - 2;
		int lines = 0;
		for (Tag t : model.getListOrEmpty("info")) {
			lines += Math.max(1, font.split(Component.literal(t.asString().orElse("")), w).size());
		}
		y -= lines * 10 + 2;
		for (Tag t : model.getListOrEmpty("info")) {
			y += g.textWithWordWrap(font, Component.literal(t.asString().orElse("")), x0, y, w, 0xFFDDDDDD) > 0 ? 0 : 0;
			y += Math.max(1, font.split(Component.literal(t.asString().orElse("")), w).size()) * 10;
		}
		ListTag rows = model.getListOrEmpty("rows");
		for (int i = 0; i < rowBoxes.size(); i++) {
			int[] b = rowBoxes.get(i);
			CompoundTag r = (CompoundTag) rows.get(b[3]);
			String text = r.getStringOr("text", "");
			String icon = r.getStringOr("icon", "");
			int tx = b[0] + 3;
			if (!icon.isEmpty()) {
				net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(com.magicmod.MagicMod.MOD_ID, icon));
				g.item(new net.minecraft.world.item.ItemStack(it), b[0] + 2, b[1] + 1);
				tx += 19;
			}
			String cut = font.plainSubstrByWidth(text, b[2] - 4 - (tx - b[0] - 3));
			if (i % 2 == 0) {
				g.fill(b[0], b[1], b[0] + w, b[1] + 18, 0x30FFFFFF);
			}
			g.text(font, cut, tx, b[1] + 5, 0xFFFFFFFF, true);
			if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + 18 && !rowTips.get(i).isEmpty()) {
				g.setComponentTooltipForNextFrame(font, rowTips.get(i), mx, my);
			}
		}
	}
}
