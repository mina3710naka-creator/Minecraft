package com.songmod.client;

import java.util.List;

import com.songmod.SongNetwork;
import com.songmod.SongPlayback;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 選曲台の、曲の一覧画面。曲をクリックすると選ばれて、画面が閉じる。「消す」でこの台が建てた回路を消す。 */
public class SongSelectScreen extends Screen {

	private static final int ROWS = 5;

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

	/** 再生位置の表示(画面を開いた時点のもの。操作すると更新される) */
	private String status() {
		if (st.status().nowTenths() < 0) {
			return "再生していません";
		}
		return fmt(st.status().nowTenths()) + " / " + fmt(st.status().totalTenths()) + (st.status().paused() ? "  (一時停止)" : "");
	}

	private static String fmt(int tenths) {
		int s = tenths / 10;
		return String.format("%d:%02d", s / 60, s % 60);
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int w = 260;
		int top = Math.max(4, this.height / 2 - 105);
		List<String> songs = st.songs();
		Button title = Button.builder(Component.literal("選曲台  (" + songs.size() + " 曲)  ─  " + status()), b -> { })
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
		// 建てるときの設定(速度・移調・音色)。押すたびに次の値になり、次に建てるときから反映される
		SongNetwork.Options op = st.options();
		String[] optLabels = { "速度 " + op.speedPct() + "%", "移調 " + (op.transpose() > 0 ? "+" : "") + op.transpose(), "音色 " + op.instLabel() };
		int ow = (w - 2 * 2) / 3;
		for (int i = 0; i < 3; i++) {
			final int kind = i;
			addRenderableWidget(Button.builder(Component.literal(optLabels[i]), b ->
					ClientPlayNetworking.send(new SongNetwork.OptionPayload(st.pos(), kind)))
					.bounds(cx - w / 2 + i * (ow + 2), y, ow, 20).build());
		}
		y += 24;
		// 全体の音量(建てた回路にも、すぐ反映される)
		addRenderableWidget(Button.builder(Component.literal("全体の音量 " + op.volumePct() + "%  (音符ごとの強さにかける)"), b ->
				ClientPlayNetworking.send(new SongNetwork.OptionPayload(st.pos(), 3)))
				.bounds(cx - w / 2, y, w, 20).build());
		y += 24;
		// 再生位置の操作(この台が建てた回路に対して)
		int bw = (w - 3 * 2) / 4;
		String[] labels = { "⏮ 頭から", "−30秒", "+30秒", st.status().paused() ? "▶ 再開" : "⏸ 一時停止" };
		int[][] acts = { { SongPlayback.RESTART, 0 }, { SongPlayback.SEEK, -30 }, { SongPlayback.SEEK, 30 }, { SongPlayback.PAUSE, 0 } };
		for (int i = 0; i < 4; i++) {
			final int[] act = acts[i];
			addRenderableWidget(Button.builder(Component.literal(labels[i]), b ->
					ClientPlayNetworking.send(new SongNetwork.ControlPayload(st.pos(), act[0], act[1])))
					.bounds(cx - w / 2 + i * (bw + 2), y, bw, 20).build());
		}
		y += 24;
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
		addRenderableWidget(Button.builder(Component.literal("この台で建てた回路を消す"), b -> {
			ClientPlayNetworking.send(new SongNetwork.ErasePayload(st.pos()));
			onClose();
		}).bounds(cx - w / 2, y, w / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose()).bounds(cx + 2, y, w / 2 - 2, 20).build());
	}
}
