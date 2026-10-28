package com.lampvideo.client;

import java.util.List;

import com.lampvideo.trolley.TrolleyNetwork;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** トロッコムービーの特殊ブロックの、曲選びの画面。 */
public class TrolleySongScreen extends Screen {

	private static final int ROWS = 7;

	private final TrolleyNetwork.OpenPayload st;
	private String current;
	private int page;

	public TrolleySongScreen(TrolleyNetwork.OpenPayload st) {
		super(Component.literal("トロッコムービー"));
		this.st = st;
		this.current = st.current();
	}

	/** 開いている間も、ゲーム(サーバー)を止めない。 */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int w = 240;
		int top = Math.max(4, this.height / 2 - 100);
		Button title = Button.builder(Component.literal("トロッコムービー  ─  曲を選ぶ"), b -> { }).bounds(cx - w / 2, top, w, 20).build();
		title.active = false;
		addRenderableWidget(title);
		List<String> files = st.files();
		int pages = Math.max(1, (files.size() + ROWS - 1) / ROWS);
		page = Math.min(page, pages - 1);
		int y = top + 24;
		for (int i = 0; i < ROWS; i++) {
			int idx = page * ROWS + i;
			if (idx >= files.size()) {
				break;
			}
			String name = files.get(idx);
			addRenderableWidget(Button.builder(Component.literal((name.equals(current) ? "▶ " : "") + name), b -> {
				current = name;
				ClientPlayNetworking.send(new TrolleyNetwork.SelectPayload(st.pos(), name));
				rebuildWidgets();
			}).bounds(cx - w / 2, y + i * 20, w, 20).build());
		}
		if (files.isEmpty()) {
			Button none = Button.builder(Component.literal("(.minecraft/lampvideo に動画を入れてください)"), b -> { })
					.bounds(cx - w / 2, y, w, 20).build();
			none.active = false;
			addRenderableWidget(none);
		}
		y += ROWS * 20 + 4;
		Button prev = Button.builder(Component.literal("◀"), b -> {
			page = Math.max(0, page - 1);
			rebuildWidgets();
		}).bounds(cx - w / 2, y, 40, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		Button label = Button.builder(Component.literal((page + 1) + " / " + pages), b -> { }).bounds(cx - 50, y, 100, 20).build();
		label.active = false;
		addRenderableWidget(label);
		Button next = Button.builder(Component.literal("▶"), b -> {
			page = Math.min(pages - 1, page + 1);
			rebuildWidgets();
		}).bounds(cx + w / 2 - 40, y, 40, 20).build();
		next.active = page < pages - 1;
		addRenderableWidget(next);
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose()).bounds(cx - w / 2, y, w, 20).build());
	}
}
