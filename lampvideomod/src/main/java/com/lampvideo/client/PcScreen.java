package com.lampvideo.client;

import java.util.List;

import com.lampvideo.pc.PcRequestPayload;
import com.lampvideo.pc.PcStatePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * PC の操作画面(PC・キーボード・管理パネル・リモコンから開く)。
 * ロックされていて未解錠なら、パスワード入力だけを出す。解錠済みなら、登録された画面の再生操作と、持ち主向けの管理(パスワード・登録)を出す。
 */
public class PcScreen extends Screen {

	private static final String[] MODES = { "カラー", "モノクロ(緑)", "モノクロ(アンバー)", "モノクロ(グレー)", "ランプ風" };
	private static final int ROWS = 5;

	private static int modeIndex = 0;
	private static String selected = "";

	private final PcStatePayload st;
	private List<String> files = List.of();
	private int page;
	private EditBox password;
	private EditBox newPassword;

	public PcScreen(PcStatePayload st) {
		super(Component.literal("PC"));
		this.st = st;
	}

	private void send(int action, String text, int value) {
		ClientPlayNetworking.send(new PcRequestPayload(st.pc(), action, text, value, modeIndex));
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int w = 220;
		int top = Math.max(4, this.height / 2 - 125);
		Button title = Button.builder(Component.literal("PC  ─  画面 " + st.linked() + " 枚 / スピーカー " + st.speakers() + " 台"
				+ (st.hasPassword() ? "  🔒" : "")), b -> { }).bounds(cx - w / 2, top, w, 20).build();
		title.active = false;
		addRenderableWidget(title);
		if (!st.message().isEmpty()) {
			Button note = Button.builder(Component.literal(st.message()), b -> { })
					.bounds(cx - w / 2, Math.max(top + 330, this.height - 24), w, 20).build();
			note.active = false;
			addRenderableWidget(note);
		}
		int y = top + 24;

		if (!st.authed()) {
			password = new EditBox(this.font, cx - w / 2, y, w, 20, Component.literal("パスワード"));
			password.setMaxLength(32);
			password.setHint(Component.literal("パスワード"));
			addRenderableWidget(password);
			setInitialFocus(password);
			y += 24;
			addRenderableWidget(Button.builder(Component.literal("解錠"), b -> send(PcRequestPayload.AUTH, password.getValue(), 0))
					.bounds(cx - w / 2, y, w, 20).build());
			y += 24;
			addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose()).bounds(cx - w / 2, y, w, 20).build());
			return;
		}

		files = DisplayClient.listMedia();
		int pages = Math.max(1, (files.size() + ROWS - 1) / ROWS);
		page = Math.min(page, pages - 1);
		for (int i = 0; i < ROWS; i++) {
			int idx = page * ROWS + i;
			if (idx >= files.size()) {
				break;
			}
			String name = files.get(idx);
			addRenderableWidget(Button.builder(Component.literal((name.equals(selected) ? "▶ " : "") + name), b -> {
				selected = name;
				rebuildWidgets();
			}).bounds(cx - w / 2, y + i * 20, w, 20).build());
		}
		if (files.isEmpty()) {
			Button none = Button.builder(Component.literal("(media フォルダにファイルを入れてください)"), b -> { })
					.bounds(cx - w / 2, y, w, 20).build();
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
		addRenderableWidget(Button.builder(Component.literal("モード: " + MODES[modeIndex]), b -> {
			modeIndex = (modeIndex + 1) % MODES.length;
			b.setMessage(Component.literal("モード: " + MODES[modeIndex]));
			send(PcRequestPayload.MODE, "", 0);
		}).bounds(cx - w / 2, y, w, 20).build());

		y += 22;
		boolean idle = st.state() == 0;
		int third = (w - 4) / 3;
		Button back = Button.builder(Component.literal("⏪ 10秒"), b -> send(PcRequestPayload.SEEK, "", -10_000))
				.bounds(cx - w / 2, y, third, 20).build();
		back.active = !idle;
		addRenderableWidget(back);
		Button pause = Button.builder(Component.literal(st.state() == 2 ? "▶ 再開" : "⏸ 一時停止"), b -> send(PcRequestPayload.PAUSE, "", 0))
				.bounds(cx - w / 2 + third + 2, y, third, 20).build();
		pause.active = !idle;
		addRenderableWidget(pause);
		Button fwd = Button.builder(Component.literal("10秒 ⏩"), b -> send(PcRequestPayload.SEEK, "", 10_000))
				.bounds(cx - w / 2 + (third + 2) * 2, y, third, 20).build();
		fwd.active = !idle;
		addRenderableWidget(fwd);

		y += 22;
		Button play = Button.builder(Component.literal("再生"), b -> send(PcRequestPayload.PLAY, selected, 0))
				.bounds(cx - w / 2, y, w / 2 - 2, 20).build();
		play.active = !selected.isEmpty();
		addRenderableWidget(play);
		addRenderableWidget(Button.builder(Component.literal("停止"), b -> send(PcRequestPayload.STOP, "", 0))
				.bounds(cx + 2, y, w / 2 - 2, 20).build());

		if (st.owner()) {
			y += 26;
			newPassword = new EditBox(this.font, cx - w / 2, y, w - 64, 20, Component.literal("新しいパスワード"));
			newPassword.setMaxLength(32);
			newPassword.setHint(Component.literal("新しいパスワード(空で解除)"));
			addRenderableWidget(newPassword);
			addRenderableWidget(Button.builder(Component.literal("設定"), b -> send(PcRequestPayload.SET_PASSWORD, newPassword.getValue(), 0))
					.bounds(cx + w / 2 - 60, y, 60, 20).build());
			y += 22;
			addRenderableWidget(Button.builder(Component.literal("近くの画面を登録"), b -> send(PcRequestPayload.LINK_NEARBY, "", 0))
					.bounds(cx - w / 2, y, w / 2 - 2, 20).build());
			addRenderableWidget(Button.builder(Component.literal("登録を解除"), b -> send(PcRequestPayload.UNLINK_ALL, "", 0))
					.bounds(cx + 2, y, w / 2 - 2, 20).build());
		}
		y += 22;
		addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose()).bounds(cx - w / 2, y, w, 20).build());
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(null);
	}
}
