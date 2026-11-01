package com.povreplay;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 録画一覧。保存してある録画(最大 10 件)を並べて、いつでも、動画に書き出したり、削除したりできる。
 * 書き出しは、ワールドを再生し直すので、ワールドの中で押すと、ワールドを出てから始まる。
 */
public class ClipListScreen extends Screen {

	private final Screen parent;
	/** 削除の確認中の行(1 回目の「削除」で「本当に?」になり、もう一度押すと消す)。 */
	private int confirmDelete = -1;

	public ClipListScreen(Screen parent) {
		super(Component.literal("録画一覧"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		List<ExportJob> clips = LiveRecording.clips();
		int left = this.width / 2 - 170;
		for (int i = 0; i < clips.size(); i++) {
			ExportJob job = clips.get(i);
			int y = 36 + i * 22;
			boolean busy = ReplayEngine.busyWith(job);
			Button export = Button.builder(Component.literal(busy ? "書き出し中" : "書き出し"), b -> exportClip(job)).bounds(left + 230, y, 66, 20).build();
			export.active = !busy;
			addRenderableWidget(export);
			int index = i;
			addRenderableWidget(Button.builder(Component.literal(confirmDelete == i ? "本当に?" : "削除"), b -> {
				if (confirmDelete == index) {
					job.delete(this.minecraft.gameDirectory.toPath());
					LiveRecording.removeClip(job);
					confirmDelete = -1;
				} else {
					confirmDelete = index;
				}
				this.rebuildWidgets();
			}).bounds(left + 300, y, 40, 20).build());
		}
		addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose()).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
	}

	private void exportClip(ExportJob job) {
		this.minecraft.gui.setScreen(null);
		ReplayEngine.exportClip(this.minecraft, job);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		List<ExportJob> clips = LiveRecording.clips();
		graphics.centeredText(this.font, Component.literal("録画一覧(" + clips.size() + " / " + ExportJob.MAX_CLIPS + " 件)"), this.width / 2, 14, 0xFFFFFFFF);
		int left = this.width / 2 - 170;
		if (clips.isEmpty()) {
			graphics.centeredText(this.font, Component.literal("保存してある録画はありません"), this.width / 2, 60, 0xFFA0A0A0);
		}
		for (int i = 0; i < clips.size(); i++) {
			ExportJob job = clips.get(i);
			int y = 36 + i * 22 + 6;
			String text = String.format("%d  %s  %d:%02d%s", i + 1, job.createdText(), (int) job.seconds() / 60, (int) job.seconds() % 60,
					job.exported() ? "  (書き出し済み)" : "");
			graphics.text(this.font, text, left, y, job.exported() ? 0xFF80C080 : 0xFFFFFFFF);
		}
		String note = this.minecraft.level != null ? "書き出すときは、ワールドを出ます。書き出した録画も、削除するまで残ります" : "書き出した録画も、削除するまで残ります";
		graphics.centeredText(this.font, Component.literal(note), this.width / 2, this.height - 44, 0xFF909090);
	}
}
