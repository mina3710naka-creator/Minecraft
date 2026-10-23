package com.lampvideo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * リモコンのメニュー。ボタンは、/lampvideo などのコマンドをサーバーに送るだけ(サーバー側の処理は、コマンドと同じ)。
 * 大きさ・向き・速度・ブロック・テクスチャの選択は、画面を開き直しても覚えている。
 */
public class LampVideoMenuScreen extends Screen {

	private static final int[] SIZES = { 360, 540, 720 };
	private static final String[] SPEEDS = { "1", "2", "3", "normal" };

	private static int sizeIndex = 0;
	private static boolean flat = true;
	private static int speedIndex = 2;
	private static final String[] BLOCKS = { "nolight", "solid", "lamp" };
	private static int blockIndex = 0;
	private static boolean flatTexture = true;

	public LampVideoMenuScreen() {
		super(Component.literal("ランプ動画"));
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int top = Math.max(4, this.height / 2 - 110);
		int w = 98;
		int gap = 4;
		int row = 22;

		Button title = Button.builder(Component.literal("ランプ動画 リモコン"), b -> { })
				.bounds(cx - w - gap / 2, top, w * 2 + gap, 20).build();
		title.active = false;
		addRenderableWidget(title);

		Button sizeButton = Button.builder(sizeLabel(), b -> {
			sizeIndex = (sizeIndex + 1) % SIZES.length;
			b.setMessage(sizeLabel());
		}).bounds(cx - w - gap / 2, top + row, w, 20).build();
		addRenderableWidget(sizeButton);

		Button layoutButton = Button.builder(layoutLabel(), b -> {
			flat = !flat;
			b.setMessage(layoutLabel());
		}).bounds(cx + gap / 2, top + row, w, 20).build();
		addRenderableWidget(layoutButton);

		addRenderableWidget(Button.builder(Component.literal("画面を作る"), b -> {
			sendLayout();
			send("lampvideo setup " + SIZES[sizeIndex]);
			onClose();
		}).bounds(cx - w - gap / 2, top + row * 2, w * 2 + gap, 20).build());

		Button speedButton = Button.builder(speedLabel(), b -> {
			speedIndex = (speedIndex + 1) % SPEEDS.length;
			b.setMessage(speedLabel());
		}).bounds(cx - w - gap / 2, top + row * 3, w, 20).build();
		addRenderableWidget(speedButton);

		addRenderableWidget(Button.builder(Component.literal("再生"), b -> {
			send("lampvideo play " + SPEEDS[speedIndex]);
			onClose();
		}).bounds(cx + gap / 2, top + row * 3, w, 20).build());

		addRenderableWidget(Button.builder(Component.literal("画面を作って再生"), b -> {
			sendLayout();
			send("lampvideo start " + SIZES[sizeIndex] + " " + SPEEDS[speedIndex]);
			onClose();
		}).bounds(cx - w - gap / 2, top + row * 4, w * 2 + gap, 20).build());

		addRenderableWidget(Button.builder(Component.literal("停止"), b -> {
			send("lampvideo stop");
			onClose();
		}).bounds(cx - w - gap / 2, top + row * 5, w, 20).build());

		addRenderableWidget(Button.builder(Component.literal("画面を片付ける"), b -> {
			send("lampvideo remove");
			onClose();
		}).bounds(cx + gap / 2, top + row * 5, w, 20).build());

		Button blockButton = Button.builder(blockLabel(), b -> {
			blockIndex = (blockIndex + 1) % BLOCKS.length;
			send("lampvideo set block " + BLOCKS[blockIndex]);
			b.setMessage(blockLabel());
		}).bounds(cx - w - gap / 2, top + row * 6, w, 20).build();
		addRenderableWidget(blockButton);

		Button textureButton = Button.builder(textureLabel(), b -> {
			flatTexture = !flatTexture;
			send("lampvideotexture " + (flatTexture ? "flat" : "normal"));
			b.setMessage(textureLabel());
		}).bounds(cx + gap / 2, top + row * 6, w, 20).build();
		addRenderableWidget(textureButton);

		addRenderableWidget(Button.builder(Component.literal("画面が見える位置へ"), b -> {
			send("lampvideo view");
			onClose();
		}).bounds(cx - w - gap / 2, top + row * 7, w, 20).build());

		Button chunkButton = Button.builder(chunkLabel(), b -> {
			Minecraft mc = Minecraft.getInstance();
			String message = LampVideoClient.applyChunkMode((LampVideoClient.chunkMode() + 1) % 3);
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.literal(message));
			}
			b.setMessage(chunkLabel());
		}).bounds(cx + gap / 2, top + row * 7, w, 20).build();
		addRenderableWidget(chunkButton);

		addRenderableWidget(Button.builder(Component.literal("映像を遅らせる +50ms"), b ->
				send("lampvideo offset add 50")).bounds(cx - w - gap / 2, top + row * 8, w, 20).build());

		addRenderableWidget(Button.builder(Component.literal("映像を早める -50ms"), b ->
				send("lampvideo offset add -50")).bounds(cx + gap / 2, top + row * 8, w, 20).build());

		addRenderableWidget(Button.builder(Component.literal("閉じる"), b -> onClose())
				.bounds(cx - w - gap / 2, top + row * 9, w * 2 + gap, 20).build());
	}

	private static Component sizeLabel() {
		return Component.literal("大きさ: " + SIZES[sizeIndex]);
	}

	private static Component layoutLabel() {
		return Component.literal("向き: " + (flat ? "横(水平)" : "縦(壁)"));
	}

	private static Component speedLabel() {
		return Component.literal("速度: " + SPEEDS[speedIndex]);
	}

	private static Component blockLabel() {
		return Component.literal("ブロック: " + (blockIndex == 0 ? "光らない(軽い)" : blockIndex == 1 ? "コンクリート" : "ランプ"));
	}

	private static Component textureLabel() {
		return Component.literal("テクスチャ: " + (flatTexture ? "単色" : "元"));
	}

	private static Component chunkLabel() {
		int mode = LampVideoClient.chunkMode();
		return Component.literal("チャンク更新: " + (mode == 0 ? "通常" : (mode == 1 ? "近く同期" : "全体同期")));
	}

	private static void sendLayout() {
		send("lampvideo set layout " + (flat ? "flat" : "wall"));
	}

	/** コマンドを、プレイヤーが入力したのと同じようにサーバー(クライアント側のコマンドなら、クライアント)に送る。 */
	private static void send(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.connection.sendCommand(command);
		}
	}
}
