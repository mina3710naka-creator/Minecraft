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
 * 管理パネルの画面: リモコンの進化版。近くの PC・画面・スピーカーの一覧から、
 * 画面やスピーカーを選んで、好きな PC に接続/解除し、名前・音量などを設定する。選んだ PC につながる画面を、まとめて止められる。
 * 設定を開いている間は、ゲームを一時停止扱いにしない(1人用でも、サーバーが動き続けて、操作がすぐに反映される)。
 */
public class PanelScreen extends Screen {

	private static final int ROWS = 5;
	private static final int[] VOLUMES = { 100, 75, 50, 25, 0 };

	private static int pcIndex = 0;
	private static boolean showSpeakers = false;
	private static BlockPos selectedDisplay;
	private static BlockPos selectedSpeaker;

	private final PanelStatePayload st;
	private int page;
	private EditBox name;

	public PanelScreen(PanelStatePayload st) {
		super(Component.literal("管理パネル"));
		this.st = st;
	}

	/** 設定を開いている間も、ゲーム(サーバー)を止めない。 */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void send(int action, BlockPos pc, BlockPos target, String text, int value) {
		ClientPlayNetworking.send(new PanelRequestPayload(st.panel(), action, pc, target, text, value));
	}

	private PanelStatePayload.PcInfo pc() {
		List<PanelStatePayload.PcInfo> pcs = st.pcs();
		return pcs.isEmpty() ? null : pcs.get(Math.min(pcIndex, pcs.size() - 1));
	}

	private PanelStatePayload.DisplayInfo selectedDisplay() {
		for (PanelStatePayload.DisplayInfo d : st.displays()) {
			if (d.pos().equals(selectedDisplay)) {
				return d;
			}
		}
		return null;
	}

	private PanelStatePayload.SpeakerInfo selectedSpeaker() {
		for (PanelStatePayload.SpeakerInfo s : st.speakers()) {
			if (s.pos().equals(selectedSpeaker)) {
				return s;
			}
		}
		return null;
	}

	private static String stateMark(int state) {
		return state == 1 ? "▶" : state == 2 ? "⏸" : "■";
	}

	private static int nextVolume(int current) {
		for (int i = 0; i < VOLUMES.length; i++) {
			if (VOLUMES[i] == current) {
				return VOLUMES[(i + 1) % VOLUMES.length];
			}
		}
		return 100;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int w = 260;
		int top = Math.max(4, this.height / 2 - 140);
		Button title = Button.builder(Component.literal("管理パネル  ─  PC " + st.pcs().size() + " / 画面 " + st.displays().size()
				+ " / スピーカー " + st.speakers().size()), b -> { }).bounds(cx - w / 2, top, w, 20).build();
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
					+ pc.pos().getX() + "," + pc.pos().getY() + "," + pc.pos().getZ() + "  画面" + pc.linked() + " スピーカー" + pc.speakers()
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
		y += 22;
		// ---- 表示の切り替え(画面 / スピーカー) ----
		addRenderableWidget(Button.builder(Component.literal("一覧: " + (showSpeakers ? "スピーカー" : "画面") + "(押すと切り替え)"), b -> {
			showSpeakers = !showSpeakers;
			page = 0;
			rebuildWidgets();
		}).bounds(cx - w / 2, y, w, 20).build());
		y += 24;

		y = showSpeakers ? speakerSection(cx, w, y, pc) : displaySection(cx, w, y, pc);

		if (!st.message().isEmpty()) {
			Button note = Button.builder(Component.literal(st.message()), b -> { }).bounds(cx - w / 2, y, w, 20).build();
			note.active = false;
			addRenderableWidget(note);
			y += 22;
		}
		addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose()).bounds(cx - w / 2, y, w, 20).build());
	}

	private int pager(int cx, int w, int y, int count) {
		int pages = Math.max(1, (count + ROWS - 1) / ROWS);
		page = Math.min(page, pages - 1);
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
		return y + 24;
	}

	private int displaySection(int cx, int w, int y, PanelStatePayload.PcInfo pc) {
		List<PanelStatePayload.DisplayInfo> displays = st.displays();
		for (int i = 0; i < ROWS; i++) {
			int idx = page * ROWS + i;
			if (idx >= displays.size()) {
				break;
			}
			PanelStatePayload.DisplayInfo d = displays.get(idx);
			String owner = d.pcIndex() < 0 ? "未接続" : "PC" + (d.pcIndex() + 1);
			String text = (d.pos().equals(selectedDisplay) ? "▶ " : "") + stateMark(d.state()) + " "
					+ (d.label().isEmpty() ? (d.stand() ? "スタンド" : d.cols() + "x" + d.rows()) : d.label()) + "  ("
					+ d.pos().getX() + "," + d.pos().getY() + "," + d.pos().getZ() + ")  " + owner + (d.muted() ? " 🔇" : "");
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
		y = pager(cx, w, y + ROWS * 20 + 2, displays.size());

		PanelStatePayload.DisplayInfo sel = selectedDisplay();
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
			final int nv = nextVolume(sel.volume());
			addRenderableWidget(Button.builder(Component.literal("音量 " + sel.volume() + "%"),
					b -> send(PanelRequestPayload.VOLUME, BlockPos.ZERO, sel.pos(), "", nv))
					.bounds(cx - w / 2 + w / 3, y, w / 3 - 2, 20).build());
			addRenderableWidget(Button.builder(Component.literal(sel.muted() ? "ミュート: ON" : "ミュート: OFF"),
					b -> send(PanelRequestPayload.MUTE, BlockPos.ZERO, sel.pos(), "", 0))
					.bounds(cx - w / 2 + w / 3 * 2, y, w / 3, 20).build());
			y += 22;
			name = nameBox(cx, w, y, sel.label());
			addRenderableWidget(Button.builder(Component.literal("名前を設定"),
					b -> send(PanelRequestPayload.LABEL, BlockPos.ZERO, sel.pos(), name.getValue(), 0))
					.bounds(cx + w / 2 - 60, y, 60, 20).build());
			y += 24;
		}
		return y;
	}

	private int speakerSection(int cx, int w, int y, PanelStatePayload.PcInfo pc) {
		List<PanelStatePayload.SpeakerInfo> speakers = st.speakers();
		for (int i = 0; i < ROWS; i++) {
			int idx = page * ROWS + i;
			if (idx >= speakers.size()) {
				break;
			}
			PanelStatePayload.SpeakerInfo s = speakers.get(idx);
			String owner = s.pcIndex() < 0 ? "未接続" : "PC" + (s.pcIndex() + 1);
			String text = (s.pos().equals(selectedSpeaker) ? "▶ " : "") + "🔊 " + (s.label().isEmpty() ? "スピーカー" : s.label()) + "  ("
					+ s.pos().getX() + "," + s.pos().getY() + "," + s.pos().getZ() + ")  " + owner + "  " + s.volume() + "%";
			addRenderableWidget(Button.builder(Component.literal(text), b -> {
				selectedSpeaker = s.pos();
				rebuildWidgets();
			}).bounds(cx - w / 2, y + i * 20, w, 20).build());
		}
		if (speakers.isEmpty()) {
			Button none = Button.builder(Component.literal("近くにスピーカーがありません"), b -> { }).bounds(cx - w / 2, y, w, 20).build();
			none.active = false;
			addRenderableWidget(none);
		}
		y = pager(cx, w, y + ROWS * 20 + 2, speakers.size());

		PanelStatePayload.SpeakerInfo sel = selectedSpeaker();
		if (sel != null) {
			boolean connected = sel.pcIndex() >= 0;
			Button link = Button.builder(Component.literal(connected ? "接続を解除" : "選んだ PC に接続"), b -> {
				if (connected) {
					send(PanelRequestPayload.SPK_UNLINK, BlockPos.ZERO, sel.pos(), "", 0);
				} else if (pc != null) {
					send(PanelRequestPayload.SPK_LINK, pc.pos(), sel.pos(), "", 0);
				}
			}).bounds(cx - w / 2, y, w / 2 - 2, 20).build();
			link.active = connected || pc != null;
			addRenderableWidget(link);
			final int nv = nextVolume(sel.volume());
			addRenderableWidget(Button.builder(Component.literal("音量 " + sel.volume() + "%"),
					b -> send(PanelRequestPayload.SPK_VOLUME, BlockPos.ZERO, sel.pos(), "", nv))
					.bounds(cx + 2, y, w / 2 - 2, 20).build());
			y += 22;
			name = nameBox(cx, w, y, sel.label());
			addRenderableWidget(Button.builder(Component.literal("名前を設定"),
					b -> send(PanelRequestPayload.SPK_LABEL, BlockPos.ZERO, sel.pos(), name.getValue(), 0))
					.bounds(cx + w / 2 - 60, y, 60, 20).build());
			y += 24;
		}
		return y;
	}

	private EditBox nameBox(int cx, int w, int y, String value) {
		EditBox box = new EditBox(this.font, cx - w / 2, y, w - 64, 20, Component.literal("名前"));
		box.setMaxLength(24);
		box.setValue(value);
		box.setHint(Component.literal("名前"));
		addRenderableWidget(box);
		return box;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(null);
	}
}
