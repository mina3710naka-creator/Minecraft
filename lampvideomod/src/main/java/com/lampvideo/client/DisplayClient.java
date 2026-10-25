package com.lampvideo.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.lwjgl.system.MemoryUtil;

import com.lampvideo.display.DisplayBlockEntity;
import com.lampvideo.display.DisplayGeometry;
import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ディスプレイのクライアント側。
 * 隣り合う同じ向きのディスプレイは1つのグループ(1枚の大画面)として扱い、左上のパネルが「親」になる。
 * 親だけが再生(ffmpeg)とテクスチャを持ち、他のパネルは、親のテクスチャの自分の部分を描く。
 */
public final class DisplayClient {

	/** これより遠いと、再生を止める(近づくと、経過時間から途中で始め直す)。 */
	private static final double MAX_DISTANCE = 64.0;
	/** 音が聞こえる距離。この距離で無音になる(大画面は、画面の大きさの分だけ遠くまで聞こえる)。 */
	private static final double HEAR_DISTANCE = 32.0;

	private static final List<ClientState> OPEN = new ArrayList<>();
	private static long lastTickNanos;
	/** Esc のメニュー(ポーズ画面)が開いているか。 */
	private static volatile boolean pauseScreenOpen;

	private DisplayClient() {
	}

	/** 親だけが持つ、再生とテクスチャの状態。 */
	private static final class ClientState {
		MediaSession session;
		boolean snapshot;
		DynamicTexture texture;
		Identifier textureId;
		int texW;
		int texH;
		boolean hasFrame;
		int generation = -1;
		String media = "";
		/** この端末で、メニューなどで止まっていた時間(ミリ秒)。再生位置から引く。 */
		long pausedMs;
		static int counter;

		void stopSession() {
			if (session != null) {
				session.close();
				session = null;
			}
		}

		void ensureTexture(int w, int h) {
			if (texture != null && (texW != w || texH != h)) {
				releaseTexture();
			}
			if (texture == null) {
				texture = new DynamicTexture(() -> "lampvideo display", w, h, false);
				textureId = Identifier.fromNamespaceAndPath("lampvideo", "display/tex" + (counter++));
				Minecraft.getInstance().getTextureManager().register(textureId, texture);
				texW = w;
				texH = h;
				hasFrame = false;
			}
		}

		void releaseTexture() {
			if (texture != null) {
				Minecraft.getInstance().getTextureManager().release(textureId);
				texture = null;
				textureId = null;
				hasFrame = false;
			}
		}

		void uploadIfNew() {
			if (session == null || texture == null) {
				return;
			}
			int[] px = session.takeFrame();
			if (px != null && px.length == texW * texH) {
				NativeImage img = texture.getPixels();
				MemoryUtil.memIntBuffer(img.getPointer(), texW * texH).put(0, px);
				texture.upload();
				hasFrame = true;
			}
		}

		void closeAll() {
			stopSession();
			releaseTexture();
		}
	}

	public static void init() {
		BlockEntityRendererRegistry.register(com.lampvideo.display.ModDisplay.DISPLAY_ENTITY, DisplayRenderer::new);
		DisplayBlockEntity.CLIENT_TICK = DisplayClient::tick;
		DisplayBlockEntity.CLIENT_REMOVED = DisplayClient::release;
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> closeAll());
		ClientTickEvents.END_CLIENT_TICK.register(client -> globalTick());
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen) {
				pauseScreenOpen = true;
				ScreenEvents.remove(screen).register(closed -> pauseScreenOpen = false);
			}
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.lampvideo.pc.PcStatePayload.TYPE,
				(payload, context) -> Minecraft.getInstance().execute(
						() -> Minecraft.getInstance().setScreenAndShow(new PcScreen(payload))));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.lampvideo.pc.PanelStatePayload.TYPE,
				(payload, context) -> Minecraft.getInstance().execute(
						() -> Minecraft.getInstance().setScreenAndShow(new PanelScreen(payload))));
		loadAvOffset();
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("lampvideoav")
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("ms",
								com.mojang.brigadier.arguments.IntegerArgumentType.integer(-500, 500)).executes(ctx -> {
							int ms = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ms");
							MediaSession.audioLatencyMs = ms;
							saveAvOffset();
							ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
									"[ディスプレイ] 音の遅れの補正を " + ms + " ms にしました(音が映像より遅れて聞こえるなら、プラスにする)。再生し直すと反映されます"));
							return 1;
						}))
						.executes(ctx -> {
							ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
									"[ディスプレイ] 音の遅れの補正: " + MediaSession.audioLatencyMs + " ms(/lampvideoav <ms> で変更。-500〜500)"));
							return 1;
						})));
		prepareFolder();
	}

	private static Path avFile() {
		return mediaDir().getParent().resolve("display-client.properties");
	}

	private static void loadAvOffset() {
		try {
			if (Files.exists(avFile())) {
				java.util.Properties p = new java.util.Properties();
				try (var in = Files.newInputStream(avFile())) {
					p.load(in);
				}
				MediaSession.audioLatencyMs = Integer.parseInt(p.getProperty("audioLatencyMs", "0"));
			}
		} catch (IOException | NumberFormatException e) {
			MediaSession.audioLatencyMs = 0;
		}
	}

	private static void saveAvOffset() {
		java.util.Properties p = new java.util.Properties();
		p.setProperty("audioLatencyMs", Integer.toString(MediaSession.audioLatencyMs));
		try (var out = Files.newOutputStream(avFile())) {
			p.store(out, "lampvideo display client");
		} catch (IOException e) {
			// 保存できなくても、このゲームの間は有効
		}
	}

	/** Esc のメニューを開いている間(1人用の一時停止を含む)は、ディスプレイも止める。 */
	private static boolean isLocalPaused() {
		Minecraft mc = Minecraft.getInstance();
		return mc.isPaused() || pauseScreenOpen;
	}

	/** 毎 tick(ゲームが一時停止していても呼ばれる): メニューで止まっている間、全部の再生を止めて、止まった時間を数える。 */
	private static void globalTick() {
		long now = System.nanoTime();
		long dt = lastTickNanos == 0 ? 0 : (now - lastTickNanos) / 1_000_000L;
		lastTickNanos = now;
		if (isLocalPaused()) {
			for (ClientState cs : OPEN) {
				cs.stopSession();
				cs.pausedMs += dt;
			}
		}
	}

	public static Path mediaDir() {
		return FabricLoader.getInstance().getGameDir().resolve("lampvideo").resolve("media");
	}

	private static void prepareFolder() {
		try {
			Files.createDirectories(mediaDir());
			Path readme = mediaDir().resolve("README.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, "ここに動画(.mp4 など)や音楽(.mp3 など)を入れると、ディスプレイで再生できます。\n"
						+ "再生には ffmpeg が必要です(コマンドプロンプトで winget install ffmpeg)。\n");
			}
		} catch (IOException e) {
			// フォルダを作れなくても、ゲームは続ける
		}
	}

	/** 再生できるファイルの名前の一覧(media フォルダ。以前の lampvideo フォルダ直下の動画も含む)。 */
	public static List<String> listMedia() {
		List<String> names = new ArrayList<>();
		for (Path dir : List.of(mediaDir(), mediaDir().getParent())) {
			try (Stream<Path> s = Files.list(dir)) {
				s.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).filter(DisplayClient::isMedia)
						.sorted().forEach(n -> {
							if (!names.contains(n)) {
								names.add(n);
							}
						});
			} catch (IOException e) {
				// 無ければ空
			}
		}
		return names;
	}

	private static boolean isMedia(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		int dot = n.lastIndexOf('.');
		return dot >= 0 && MediaSession.extensions().contains(n.substring(dot + 1));
	}

	/** 名前から、実際のファイルを探す。決まったフォルダの中だけを見る(名前にパスの区切りがあっても無視)。 */
	static Path resolve(String name) {
		String n = name.replace('\\', '/');
		n = n.substring(n.lastIndexOf('/') + 1);
		for (Path dir : List.of(mediaDir(), mediaDir().getParent())) {
			Path p = dir.resolve(n);
			if (Files.isRegularFile(p)) {
				return p;
			}
		}
		return null;
	}

	// ---- グループ(連結した大画面) ----

	/** 操作の対象になる「親」(グループの左上のパネル)の位置。 */
	public static BlockPos primaryOf(BlockPos pos) {
		Level level = Minecraft.getInstance().level;
		return level == null ? pos : DisplayGeometry.primaryOf(level, pos);
	}

	// ---- 再生 ----

	/** ブロックエンティティが毎 tick 呼ぶ(ゲームが一時停止している間は呼ばれない)。 */
	static void tick(DisplayBlockEntity be) {
		Minecraft mc = Minecraft.getInstance();
		Level level = be.getLevel();
		if (level == null) {
			return;
		}
		BlockState bs = be.getBlockState();
		BlockPos pos = be.getBlockPos();
		int[] e = DisplayGeometry.extent(level, pos);
		be.cols = e[0] + e[1] + 1;
		be.rows = e[2] + e[3] + 1;
		be.col = e[0];
		be.row = e[2];
		be.primary = DisplayGeometry.primaryOf(level, pos);
		be.edges = e;
		boolean frameless = DisplayGeometry.frameless(bs);
		be.hasStand = DisplayGeometry.groupHasStand(level, pos, e);
		if (e[0] != 0 || e[2] != 0) {
			release(be);
			return;
		}

		ClientState cs = (ClientState) be.clientState;
		if (cs == null) {
			cs = new ClientState();
			be.clientState = cs;
			OPEN.add(cs);
		}
		// グループの中心までの距離
		Vec3i r = DisplayGeometry.rightOffset(bs);
		Vec3i u = DisplayGeometry.upOffset(bs);
		double cx = pos.getX() + 0.5 + r.getX() * (be.cols - 1) / 2.0 - u.getX() * (be.rows - 1) / 2.0;
		double cy = pos.getY() + 0.5 + r.getY() * (be.cols - 1) / 2.0 - u.getY() * (be.rows - 1) / 2.0;
		double cz = pos.getZ() + 0.5 + r.getZ() * (be.cols - 1) / 2.0 - u.getZ() * (be.rows - 1) / 2.0;
		double dist = mc.player == null ? Double.MAX_VALUE : Math.sqrt(mc.player.distanceToSqr(cx, cy, cz));
		double scale = Math.max(1.0, Math.max(be.cols, be.rows) / 2.0);

		// 映像は、画面の見える範囲(枠の内側)と同じ比率の大きさにして、映像の全体が収まるようにする(余りは黒)
		float[] vis = DisplayGeometry.visibleSize(be.cols, be.rows, frameless, be.hasStand);
		if (DisplayGeometry.isDiagonal(bs)) {
			vis[0] *= (float) Math.sqrt(2.0);   // 斜めの画面は、1枚の幅が √2 倍(ブロックの対角線)
		}
		float ppb = Math.min(512f, 1024f / Math.max(vis[0], vis[1]));
		int w = Math.max(32, Math.round(vis[0] * ppb));
		int h = Math.max(32, Math.round(vis[1] * ppb));

		if (be.state == DisplayBlockEntity.STOPPED || be.media.isEmpty()) {
			cs.stopSession();
			cs.releaseTexture();
			cs.generation = be.generation;
			return;
		}
		if (cs.generation != be.generation || !cs.media.equals(be.media) || cs.texW != w || cs.texH != h) {
			cs.stopSession();
			cs.generation = be.generation;
			cs.media = be.media;
			cs.pausedMs = 0;
			cs.snapshot = false;
			cs.ensureTexture(w, h);
		}
		if (isLocalPaused() || dist > MAX_DISTANCE * scale) {
			cs.stopSession();
			return;
		}
		cs.ensureTexture(w, h);
		boolean wantSnapshot = be.state == DisplayBlockEntity.PAUSED;
		if (cs.session != null && cs.snapshot != wantSnapshot) {
			cs.stopSession();
		}
		if (cs.session == null && !(wantSnapshot && cs.hasFrame && cs.snapshot)) {
			Path file = resolve(be.media);
			if (file != null) {
				long pos2 = wantSnapshot ? be.posMs : Math.max(0, be.positionMs() - cs.pausedMs);
				cs.session = new MediaSession(file, w, h, be.mode, pos2, wantSnapshot);
				cs.snapshot = wantSnapshot;
			}
		}
		if (cs.session != null) {
			cs.session.mode = be.mode;
			cs.session.setVolume(be.muted ? 0f : (float) (Math.max(0.0, 1.0 - dist / (HEAR_DISTANCE * scale)) * be.volume / 100.0));
		}
	}

	static void release(DisplayBlockEntity be) {
		ClientState cs = (ClientState) be.clientState;
		if (cs != null) {
			cs.closeAll();
			OPEN.remove(cs);
			be.clientState = null;
		}
	}

	static void closeAll() {
		for (ClientState cs : new ArrayList<>(OPEN)) {
			cs.closeAll();
		}
		OPEN.clear();
	}

	/** 描画用: このパネルが映すテクスチャ(親のもの)。映像が1枚でも来ていれば返す。なければ null(消灯の見た目のまま)。 */
	static Identifier textureFor(DisplayBlockEntity be) {
		Level level = be.getLevel();
		if (level == null || be.primary == null) {
			return null;
		}
		DisplayBlockEntity parent = be.primary.equals(be.getBlockPos()) ? be
				: level.getBlockEntity(be.primary) instanceof DisplayBlockEntity p ? p : null;
		if (parent == null || !(parent.clientState instanceof ClientState cs)) {
			return null;
		}
		cs.uploadIfNew();
		be.texW = cs.texW;
		be.texH = cs.texH;
		return cs.hasFrame ? cs.textureId : null;
	}

	/** 画面の操作で、いまの再生状態を見たいとき用。 */
	public static DisplayBlockEntity blockEntityAt(BlockPos pos) {
		Level level = Minecraft.getInstance().level;
		return level != null && level.getBlockEntity(pos) instanceof DisplayBlockEntity be ? be : null;
	}
}
