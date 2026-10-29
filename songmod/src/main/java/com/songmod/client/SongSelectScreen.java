package com.songmod.client;

import java.util.List;

import com.songmod.SongNetwork;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 選曲台の、曲の一覧画面。曲をクリックすると選ばれて、画面が閉じる。 */
public class SongSelectScreen extends Screen {

	private static final int ROWS = 8;

	private final SongNetwork.OpenPayload st;
	private int page;

	public SongSelectScreen(SongNetwork.OpenPayload st) {
		super(Component.literal("曲を選ぶ"));
		this.st = st;
		// 今選んでいる曲があるページから開く
		int idx = st.songs().indexOf(st.current());
		this.page = idx < 0 ? 0 : idx / ROWS;
	}

	/** 開いている間も、ゲームを止めない。 */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int w = 260;
		int top = Math.max(4, this.height / 2 - 105);
		List<String> songs = st.songs();
		Button title = Button.builder(Component.literal("選曲台  ─  曲を選ぶ  (" + songs.size() + " 曲)"), b -> { })
				.bounds(cx - w / 2, top, w, 20).build();
		title.active = false;
		addRenderableWidget(title);
		int pages = Math.max(1, (songs.size() + ROWS - 1) / ROWS);
		page = Math.min(page, pages - 1);
		int y = top + 24;
		for (int i = 0; i < ROWS; i++) {
			int idx = page * ROWS + i;
			if (idx >= songs.size()) {
				break;
			}
			String name = songs.get(idx);
			addRenderableWidget(Button.builder(Component.literal((name.equals(st.current()) ? "▶ " : "") + name), b -> {
				ClientPlayNetworking.send(new SongNetwork.SelectPayload(st.pos(), name));
				onClose();
			}).bounds(cx - w / 2, y + i * 20, w, 20).build());
		}
		if (songs.isEmpty()) {
			Button none = Button.builder(Component.literal("(.minecraft/songs に曲の .json を入れてください)"), b -> { })
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
