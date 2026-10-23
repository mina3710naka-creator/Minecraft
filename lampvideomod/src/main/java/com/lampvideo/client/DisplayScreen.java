package com.lampvideo.client;

import java.util.List;

import com.lampvideo.display.DisplayControlPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** ディスプレイの操作画面: ファイルを選んで、モードを決めて、再生/停止。 */
public class DisplayScreen extends Screen {

	private static final String[] MODES = { "カラー", "モノクロ(緑)", "モノクロ(アンバー)", "モノクロ(グレー)", "ランプ風" };
	private static final int ROWS = 6;

	private static int modeIndex = 0;
	private static String selected = "";

	private final BlockPos pos;
	private List<String> files = List.of();
	private int page;

	public DisplayScreen(BlockPos pos) {
		super(Component.literal("ディスプレイ"));
		this.pos = pos;
	}

	@Override
	protected void init() {
		files = DisplayClient.listMedia();
		int cx = this.width / 2;
		int top = Math.max(4, this.height / 2 - 100);
		int w = 200;

		Button title = Button.builder(Component.literal("ディスプレイ  (ファイルは .minecraft/lampvideo/media)"), b -> { })
				.bounds(cx - w / 2, top, w, 20).build();
		title.active = false;
		addRenderableWidget(title);

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
			}).bounds(cx - w / 2, top + 22 + i * 20, w, 20).build());
		}
		if (files.isEmpty()) {
			Button none = Button.builder(Component.literal("(ファイルがありません。media フォルダに入れてください)"), b -> { })
					.bounds(cx - w / 2, top + 22, w, 20).build();
			none.active = false;
			addRenderableWidget(none);
		}

		int y = top + 22 + ROWS * 20 + 2;
		Button prev = Button.builder(Component.literal("◀"), b -> {
			page = Math.max(0, page - 1);
			rebuildWidgets();
		}).bounds(cx - w / 2, y, 40, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);
		Button pageLabel = Button.builder(Component.literal((page + 1) + " / " + pages), b -> { })
				.bounds(cx - 56, y, 112, 20).build();
		pageLabel.active = false;
		addRenderableWidget(pageLabel);
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
			send(DisplayControlPayload.MODE, "");
		}).bounds(cx - w / 2, y, w, 20).build());

		y += 22;
		Button play = Button.builder(Component.literal("再生"), b -> {
			send(DisplayControlPayload.PLAY, selected);
			onClose();
		}).bounds(cx - w / 2, y, w / 2 - 2, 20).build();
		play.active = !selected.isEmpty();
		addRenderableWidget(play);
		addRenderableWidget(Button.builder(Component.literal("停止"), b -> {
			send(DisplayControlPayload.STOP, "");
			onClose();
		}).bounds(cx + 2, y, w / 2 - 2, 20).build());

		addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
				.bounds(cx - w / 2, y + 22, w, 20).build());
	}

	private void send(int action, String media) {
		ClientPlayNetworking.send(new DisplayControlPayload(pos, media, modeIndex, action));
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(null);
	}
}
