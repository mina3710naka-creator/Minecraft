package com.lampvideo.client;

import java.util.List;

import com.lampvideo.pc.PanelRequestPayload;
import com.lampvideo.pc.PanelStatePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 管理パネルの画面: リモコンの進化版。近くの PC と画面の一覧から、
 * 画面を選んで、好きな PC に接続/解除し、名前・音量・ミュートを設定する。選んだ PC につながる画面を、まとめて止められる。
 */
public class PanelScreen extends Screen {

	private static final int ROWS = 5;
	private static final int[] VOLUMES = { 100, 75, 50, 25, 0 };

	private static int pcIndex = 0;
	private static BlockPos selectedDisplay;

	private final PanelStatePayload st;
	private int page;
	private EditBox name;

	public PanelScreen(PanelStatePayload st) {
		super(Component.literal("管理パネル"));
		this.st = st;
	}

	private void send(int action, BlockPos pc, BlockPos display, String text, int value) {
		ClientPlayNetworking.send(new PanelRequestPayload(st.panel(), action, pc, display, text, value));
	}

	private PanelStatePayload.PcInfo pc() {
		List<PanelStatePayload.PcInfo> pcs = st.pcs();
		return pcs.isEmpty() ? null : pcs.get(Math.min(pcIndex, pcs.size() - 1));
	}

	private PanelStatePayload.DisplayInfo selected() {
		for (PanelStatePayload.DisplayInfo d : st.displays()) {
			if (d.pos().equals(selectedDisplay)) {
				return d;
			}
		}
		return null;
	}

	private static String stateMark(int state) {
		return state == 1 ? "▶" : state == 2 ? "⏸" : "■";
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int w = 260;
		int top = Math.max(4, this.height / 2 - 140);
		Button title = Button.builder(Component.literal("管理パネル  ─  PC " + st.pcs().size() + " 台 / 画面 " + st.displays().size() + " 枚"),
				b -> { }).bounds(cx - w / 2, top, w, 20).build();
		title.active = false;
		addRenderableWidget(title);
		int y = top + 22;

		// ---- PC の選択 ----
		PanelStatePayload.PcInfo pc = pc();
		if (pc == null) {
			Button none = Button.builder(Component.literal("近くに PC がありません"), b -> { }).bounds(cx - w / 2, y, w, 20).build();
			none.active = false;
			addRenderableWidget(none);
		} else {
			int idx = Math.min(pcIndex, st.pcs().size() - 1);
			addRenderableWidget(Button.builder(Component.literal("PC " + (idx + 1) + "/" + st.pcs().size() + "  "
					+ pc.pos().getX() + "," + pc.pos().getY() + "," + pc.pos().getZ() + "  接続 " + pc.linked() + "枚"
					+ (pc.locked() ? " 🔒" : "") + (pc.owner() ? " ★" : "")), b -> {
				pcIndex = (pcIndex + 1) % st.pcs().size();
				rebuildWidgets();
			}).bounds(cx - w / 2, y, w - 70, 20).build());
			addRenderableWidget(Button.builder(Component.literal("PC操作"), b -> send(PanelRequestPayload.OPEN_PC, pc.pos(), BlockPos.ZERO, "", 0))
					.bounds(cx + w / 2 - 66, y, 66, 20).build());
			y += 22;
			addRenderableWidget(Button.builder(Component.literal("この PC の画面を 全停止"), b -> send(PanelRequestPayload.STOP_ALL, pc.pos(), BlockPos.ZERO, "", 0))
					.bounds(cx - w / 2, y, w / 2 - 2, 20).build());
			addRenderableWidget(Button.builder(Component.literal("一時停止 ⇔ 再開"), b -> send(PanelRequestPayload.PAUSE_ALL, pc.pos(), BlockPos.ZERO, "", 0))
					.bounds(cx + 2, y, w / 2 - 2, 20).build());
		}
		y += 24;

		// ---- 画面の一覧 ----
		List<PanelStatePayload.DisplayInfo> displays = st.displays();
		int pages = Math.max(1, (displays.size() + ROWS - 1) / ROWS);
		page = Math.min(page, pages - 1);
		for (int i = 0; i < ROWS; i++) {
			int idx = page * ROWS + i;
			if (idx >= displays.size()) {
				break;
			}
			PanelStatePayload.DisplayInfo d = displays.get(idx);
			String owner = d.pcIndex() < 0 ? "未接続" : "PC" + (d.pcIndex() + 1);
			String text = (d.pos().equals(selectedDisplay) ? "▶ " : "") + stateMark(d.state()) + " "
					+ (d.label().isEmpty() ? (d.stand() ? "スタンド" : d.cols() + "x" + d.rows()) : d.label()) + "  ("
					+ d.pos().getX() + "," + d.pos().getY() + "," + d.pos().getZ() + ")  " + owner
					+ (d.muted() ? " 🔇" : "");
			addRenderableWidget(Button.builder(Component.literal(text), b -> {
				selectedDisplay = d.pos();
				rebuildWidgets();
			}).bounds(cx - w / 2, y + i * 20, w, 20).build());
		}
		if (displays.isEmpty()) {
			Button none = Button.builder(Component.literal("近くに画面がありません"), b -> { }).bounds(cx - w / 2, y, w, 20).build();
			none.active = false;
			addRenderableWidget(none);
		}
		y += ROWS * 20 + 2;
		Button prev = Button.builder(Component.literal("◀"), b -> {
			page = Math.max(0, page - 1);
			rebuildWidgets();
		}).bounds(cx - w / 2, y, 40, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		Button label = Button.builder(Component.literal((page + 1) + " / " + pages), b -> { }).bounds(cx - 60, y, 120, 20).build();
		label.active = false;
		addRenderableWidget(label);
		Button next = Button.builder(Component.literal("▶"), b -> {
			page = Math.min(pages - 1, page + 1);
			rebuildWidgets();
		}).bounds(cx + w / 2 - 40, y, 40, 20).build();
		next.active = page < pages - 1;
		addRenderableWidget(next);
		y += 24;

		// ---- 選んだ画面の設定 ----
		PanelStatePayload.DisplayInfo sel = selected();
		if (sel != null) {
			boolean connected = sel.pcIndex() >= 0;
			Button link = Button.builder(Component.literal(connected ? "接続を解除" : "選んだ PC に接続"), b -> {
				if (connected) {
					send(PanelRequestPayload.UNLINK, BlockPos.ZERO, sel.pos(), "", 0);
				} else if (pc != null) {
					send(PanelRequestPayload.LINK, pc.pos(), sel.pos(), "", 0);
				}
			}).bounds(cx - w / 2, y, w / 3 - 2, 20).build();
			link.active = connected || pc != null;
			addRenderableWidget(link);
			int next2 = 0;
			for (int i = 0; i < VOLUMES.length; i++) {
				if (VOLUMES[i] == sel.volume()) {
					next2 = (i + 1) % VOLUMES.length;
				}
			}
			final int nextVolume = VOLUMES[next2];
			addRenderableWidget(Button.builder(Component.literal("音量 " + sel.volume() + "%"),
					b -> send(PanelRequestPayload.VOLUME, BlockPos.ZERO, sel.pos(), "", nextVolume))
					.bounds(cx - w / 2 + w / 3, y, w / 3 - 2, 20).build());
			addRenderableWidget(Button.builder(Component.literal(sel.muted() ? "ミュート: ON" : "ミュート: OFF"),
					b -> send(PanelRequestPayload.MUTE, BlockPos.ZERO, sel.pos(), "", 0))
					.bounds(cx - w / 2 + w / 3 * 2, y, w / 3, 20).build());
			y += 22;
			name = new EditBox(this.font, cx - w / 2, y, w - 64, 20, Component.literal("名前"));
			name.setMaxLength(24);
			name.setValue(sel.label());
			name.setHint(Component.literal("この画面の名前"));
			addRenderableWidget(name);
			addRenderableWidget(Button.builder(Component.literal("名前を設定"),
					b -> send(PanelRequestPayload.LABEL, BlockPos.ZERO, sel.pos(), name.getValue(), 0))
					.bounds(cx + w / 2 - 60, y, 60, 20).build());
			y += 24;
		}

		if (!st.message().isEmpty()) {
			Button note = Button.builder(Component.literal(st.message()), b -> { }).bounds(cx - w / 2, y, w, 20).build();
			note.active = false;
			addRenderableWidget(note);
			y += 22;
		}
		addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose()).bounds(cx - w / 2, y, w, 20).build());
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(null);
	}
}
