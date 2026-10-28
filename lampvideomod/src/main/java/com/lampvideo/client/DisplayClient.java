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
	/** スピーカーの音が届く距離。 */
	private static final double SPEAKER_RANGE = 24.0;
	/** ヘッドホンを着けているときに、再生を続ける距離。 */
	private static final double HEADPHONE_DISTANCE = 256.0;

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
		/** 自動の位置合わせ用: 再生を始めた時刻、ずれが続き始めた時刻、最後に始め直した時刻。 */
		boolean audioOn;
		long sessionStartMs;
		/** 効果音・音符ブロックのスピーカー用: 次に鳴らすイベントの番号と、そのときの世代番号。 */
		int synthCursor = -1;
		int synthGeneration = -1;
		long synthLastPos;
		long lastEffectMs;
		long badSince;
		long lagSince;
		long lastResync;
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
			if (px != null) {
				session.recycle(px);   // 書き終えた配列は、使い回す
			}
		}

		void closeAll() {
			stopSession();
			releaseTexture();
		}
	}

	public static void init() {
		BlockEntityRendererRegistry.register(com.lampvideo.display.ModDisplay.DISPLAY_ENTITY, DisplayRenderer::new);
		BlockEntityRendererRegistry.register(com.lampvideo.display.ModDisplay.PROJECTOR_ENTITY, ProjectorRenderer::new);
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
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.lampvideo.trolley.TrolleyNetwork.OpenPayload.TYPE,
				(payload, context) -> Minecraft.getInstance().execute(
						() -> Minecraft.getInstance().setScreenAndShow(new TrolleySongScreen(payload))));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
				com.lampvideo.trolley.TrolleyNetwork.WrapPayload.TYPE,
				(payload, context) -> Minecraft.getInstance().execute(() -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player == null || mc.player.getVehicle() == null) {
						return;
					}
					net.minecraft.world.entity.Entity v = mc.player.getVehicle();
					// 画面に見えている位置から、戻った距離だけ引く(サーバーの位置まで飛ばさない)
					v.getInterpolation().cancel();
					v.snapTo(v.getX() + payload.x(), v.getY() + payload.y(), v.getZ() + payload.z(), v.getYRot(), v.getXRot());
					v.setOldPosAndRot();
					mc.player.snapTo(mc.player.getX() + payload.x(), mc.player.getY() + payload.y(), mc.player.getZ() + payload.z(),
							mc.player.getYRot(), mc.player.getXRot());
					mc.player.setOldPosAndRot();
				}));
		HeadphoneUi.init();
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
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("lampvideoquality")
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("level",
								com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).executes(ctx -> {
							setQuality(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level"));
							saveAvOffset();
							ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
									"[ディスプレイ] 画質を " + qualityLevel + " にしました(0 = 細かい、3 = 軽い)"));
							return 1;
						}))
						.executes(ctx -> {
							ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
									"[ディスプレイ] 画質: " + qualityLevel + "(/lampvideoquality <0〜3>。映像が遅れるときは、大きくする)"));
							return 1;
						})));
		net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("lampvideosynth")
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("ms",
								com.mojang.brigadier.arguments.IntegerArgumentType.integer(-1500, 1500)).executes(ctx -> {
							synthDelayMs = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ms");
							saveAvOffset();
							ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
									"[ディスプレイ] 効果音・音符ブロックの遅らせ量を " + synthDelayMs + " ms にしました(音が先に鳴るなら、プラスに。すぐ反映)"));
							return 1;
						}))
						.executes(ctx -> {
							ctx.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(
									"[ディスプレイ] 効果音・音符ブロックの遅らせ量: " + synthDelayMs + " ms(/lampvideosynth <ms> で変更。-1500〜1500)"));
							return 1;
						})));
		prepareFolder();
	}

	/** 画質の段階(0 = いちばん細かい)。映像のデコードが実時間に追いつかないとき、自動で下げる。/lampvideoquality で手動でも変えられる。 */
	private static final float[] QUALITY = { 1f, 0.7f, 0.5f, 0.35f };
	private static final double[] QUALITY_FPS = { 30.0, 30.0, 20.0, 15.0 };
	static volatile int qualityLevel = 0;

	private static void setQuality(int level) {
		qualityLevel = Math.max(0, Math.min(QUALITY.length - 1, level));
		MediaSession.fpsCap = QUALITY_FPS[qualityLevel];
	}

	/** 効果音・音符ブロックの鳴るタイミングの補正(ミリ秒)。プラス = 遅らせる。音が先に鳴るなら、プラスにする。 */
	static volatile int synthDelayMs = 120;

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
				synthDelayMs = Integer.parseInt(p.getProperty("synthDelayMs", "120"));
				setQuality(Integer.parseInt(p.getProperty("quality", "0")));
			}
		} catch (IOException | NumberFormatException e) {
			MediaSession.audioLatencyMs = 0;
		}
	}

	private static void saveAvOffset() {
		java.util.Properties p = new java.util.Properties();
		p.setProperty("audioLatencyMs", Integer.toString(MediaSession.audioLatencyMs));
		p.setProperty("synthDelayMs", Integer.toString(synthDelayMs));
		p.setProperty("quality", Integer.toString(qualityLevel));
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
		// 連結(隣のパネルとの関係)の計算は、毎 tick ではなく、10 tick に 1 回(隣のブロックを何十回も調べるので、軽くする)
		if (be.geomTicks-- <= 0) {
			be.geomTicks = 10;
			int[] ge = DisplayGeometry.extent(level, pos);
			be.cols = ge[0] + ge[1] + 1;
			be.rows = ge[2] + ge[3] + 1;
			be.col = ge[0];
			be.row = ge[2];
			be.primary = DisplayGeometry.primaryOf(level, pos);
			be.edges = ge;
			be.hasStand = DisplayGeometry.groupHasStand(level, pos, ge);
			if (be instanceof com.lampvideo.display.ProjectorBlockEntity) {
				be.proj = projectionTarget(mc, level, be);
			}
		}
		int[] e = be.edges;
		boolean frameless = DisplayGeometry.frameless(bs);
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
		if (be instanceof com.lampvideo.display.ProjectorBlockEntity) {
			w = 960;   // プロジェクターは、大きく映るので、細かく(16:9)
			h = 540;
		}
		// 映像のデコードが間に合わない端末では、画質(大きさ)を下げる
		float q = QUALITY[qualityLevel];
		w = Math.max(32, Math.round(w * q));
		h = Math.max(32, Math.round(h * q));

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
		boolean headphones = wearingHeadphones(mc);
		// 音声を出すのは、「普通」のスピーカーが接続されているか、ヘッドホンを着けているとき。効果音・音符ブロックのスピーカーだけなら、音声は出さない
		boolean needAudio = headphones || hasSpeakerWithMode(mc, be, 0);
		if (isLocalPaused() || dist > (headphones ? HEADPHONE_DISTANCE : MAX_DISTANCE) * scale) {
			cs.stopSession();
			return;
		}
		cs.ensureTexture(w, h);
		boolean wantSnapshot = be.state == DisplayBlockEntity.PAUSED;
		if (cs.session != null && (cs.snapshot != wantSnapshot || (!cs.snapshot && cs.audioOn != needAudio))) {
			cs.stopSession();
		}
		if (cs.session == null && !(wantSnapshot && cs.hasFrame && cs.snapshot)) {
			Path file = resolve(be.media);
			if (file != null) {
				long pos2 = wantSnapshot ? be.posMs : Math.max(0, be.positionMs() - cs.pausedMs);
				final ClientState csFinal = cs;
				cs.session = new MediaSession(file, w, h, be.mode, pos2, wantSnapshot, needAudio,
						wantSnapshot ? null : () -> be.positionMs() - csFinal.pausedMs);
				cs.snapshot = wantSnapshot;
				cs.audioOn = needAudio;
				cs.sessionStartMs = System.currentTimeMillis();
				cs.badSince = 0;
			}
		}
		playSynth(mc, be, cs, level);
		// 自動の位置合わせ: 音声の再生位置が、本来の位置(サーバーの時刻から出した位置)から 0.3 秒以上ずれたまま 1.5 秒続いたり、
		// 映像のデコードが追いつかなくなったりしたら、本来の位置から、始め直す(始め直しは 4 秒に 1 回まで)
		if (cs.session != null && !cs.snapshot) {
			long nowMs = System.currentTimeMillis();
			if (nowMs - cs.sessionStartMs > 2500) {
				long expected = be.positionMs() - cs.pausedMs;
				// 映像が、時計より 0.7 秒以上遅れ続けている = この端末では、デコードが実時間に追いつかない。画質を下げて、始め直す
				long lag = cs.session.videoLagMs();
				if (lag < 300) {
					cs.lagSince = 0;
				} else if (lag > 700 && cs.lagSince == 0) {
					cs.lagSince = nowMs;
				}
				if (cs.lagSince != 0 && nowMs - cs.lagSince > 2500 && nowMs - cs.lastResync > 4000 && qualityLevel < QUALITY.length - 1) {
					cs.lagSince = 0;
					cs.lastResync = nowMs;
					setQuality(qualityLevel + 1);
					saveAvOffset();
					if (mc.player != null) {
						mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
								"[ディスプレイ] 映像が追いつかないので、画質を下げました(段階 " + qualityLevel + "。/lampvideoquality で変更できます)"));
					}
					return;   // 次の tick で、新しい大きさで、本来の位置から始め直す
				}
				boolean bad = Math.abs(cs.session.rawClockMs() - expected) > 300 || cs.session.videoStalled() || lag > 2000;
				if (!bad) {
					cs.badSince = 0;
				} else if (cs.badSince == 0) {
					cs.badSince = nowMs;
				} else if (nowMs - cs.badSince > 1500 && nowMs - cs.lastResync > 4000) {
					cs.lastResync = nowMs;
					cs.badSince = 0;
					cs.stopSession();   // 次の tick で、本来の位置から、始め直す
					return;
				}
			}
		}
		if (cs.session != null) {
			cs.session.mode = be.mode;
			float[] g = audioGains(mc, be, cx, cy, cz, scale, headphones);
			cs.session.setGains(g[0], g[1]);
		}
	}

	/**
	 * プロジェクターが映す場所: 向いている方向に、まっすぐ光を飛ばして、最初にぶつかった壁(24ブロックまで)。
	 * 戻り値は {壁の上の中心の x,y,z、壁の法線の x,z、映像の幅}。壁までの距離が遠いほど、大きく映る(幅 = 距離 × 0.7。1.5〜14ブロック。サイズを選んでいれば、その幅)。
	 */
	private static double[] projectionTarget(Minecraft mc, Level level, DisplayBlockEntity be) {
		Direction f = be.getBlockState().getValue(com.lampvideo.display.ProjectorBlock.FACING);
		net.minecraft.world.phys.Vec3 from = net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos())
				.add(f.getStepX() * 0.55, 0.0, f.getStepZ() * 0.55);
		net.minecraft.world.phys.Vec3 to = from.add(f.getStepX() * 24.0, 0.0, f.getStepZ() * 24.0);
		net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection().getAxis() == Direction.Axis.Y) {
			return null;
		}
		double d = from.distanceTo(hit.getLocation());
		double width = be.projWidth > 0 ? be.projWidth : Math.max(1.5, Math.min(14.0, d * 0.7));
		net.minecraft.world.phys.Vec3 p = hit.getLocation();
		Direction n = hit.getDirection();
		return new double[] { p.x + n.getStepX() * 0.02, p.y, p.z + n.getStepZ() * 0.02, n.getStepX(), n.getStepZ(), width };
	}

	/** この画面に接続されたスピーカーに、再生方式 mode のものがあるか。 */
	private static boolean hasSpeakerWithMode(Minecraft mc, DisplayBlockEntity be, int mode) {
		for (BlockPos s : be.speakers) {
			if (mc.level != null && mc.level.getBlockEntity(s) instanceof com.lampvideo.pc.SpeakerBlockEntity sb && sb.soundMode == mode) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 効果音・音符ブロックのスピーカー: 曲を解析したイベントを、再生位置に合わせて、スピーカーの位置で鳴らす。
	 * 位置は、サーバーの時刻から出した本来の位置なので、どの端末でも、同じタイミングで鳴る。
	 */
	private static void playSynth(Minecraft mc, DisplayBlockEntity be, ClientState cs, Level level) {
		if (be.state != DisplayBlockEntity.PLAYING || isLocalPaused() || be.muted || mc.level == null || be.speakers.isEmpty()
				|| !hasSpeakerWithMode(mc, be, 1) && !hasSpeakerWithMode(mc, be, 2)) {
			cs.synthCursor = -1;
			return;
		}
		Path file = resolve(be.media);
		if (file == null) {
			return;
		}
		NoteAnalyzer.Events events = SynthCache.get(file);
		if (events == null) {
			return;   // 解析中(曲の長さにもよるが、数秒)
		}
		// 映像と同じ時計(映像のコマを選ぶ時計)で数える。映像の立ち上がりが遅れても、音が先に鳴らない。synthDelayMs で微調整
		long pos = (cs.session != null && !cs.snapshot ? cs.session.clockMs() : be.positionMs() - cs.pausedMs) - synthDelayMs;
		// 最初・再生し直し・シーク(位置が大きく動いた)のときは、いまの位置から数え直す
		if (cs.synthCursor < 0 || cs.synthGeneration != be.generation || Math.abs(pos - cs.synthLastPos) > 400) {
			cs.synthCursor = events.firstIndexAtOrAfter(pos);
			cs.synthGeneration = be.generation;
		}
		cs.synthLastPos = pos;
		int played = 0;
		int best = -1;   // 効果音は、1 音ずつ: 同時に出た音のうち、いちばん強い 1 つだけ
		while (cs.synthCursor < events.size && events.timeMs[cs.synthCursor] <= pos) {
			int i = cs.synthCursor++;
			if (events.timeMs[i] < pos - 150 || played > 24) {
				continue;   // 遅れすぎたイベントや、1 tick に多すぎる分は、鳴らさない
			}
			if (events.kind[i] == NoteAnalyzer.PITCHED && (best < 0 || events.vel[i] > events.vel[best])) {
				best = i;
			}
			for (BlockPos s : be.speakers) {
				if (mc.level.getBlockEntity(s) instanceof com.lampvideo.pc.SpeakerBlockEntity sb && sb.soundMode == NoteVoices.MODE_NOTEBLOCK) {
					NoteVoices.play(mc.level, s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, sb.soundMode, events.kind[i],
							events.note[i], events.vel[i], sb.volume / 100f * be.volume / 100f);
					played++;
				}
			}
		}
		long nowMs = System.currentTimeMillis();
		if (best >= 0 && nowMs - cs.lastEffectMs >= 80) {
			cs.lastEffectMs = nowMs;
			for (BlockPos s : be.speakers) {
				if (mc.level.getBlockEntity(s) instanceof com.lampvideo.pc.SpeakerBlockEntity sb && sb.soundMode == NoteVoices.MODE_EFFECT) {
					NoteVoices.play(mc.level, s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, sb.soundMode, events.kind[best],
							events.note[best], events.vel[best], sb.volume / 100f * be.volume / 100f);
				}
			}
		}
	}

	/** ヘッドホンを頭に着けているか。 */
	private static boolean wearingHeadphones(Minecraft mc) {
		return mc.player != null && com.lampvideo.pc.ModPc.isWearingHeadphones(mc.player);
	}

	/**
	 * 左右の音量。スピーカーが接続されていれば、各スピーカーの位置から(距離で小さく、向きで左右に振り分けて)聞こえる。
	 * なければ、画面の位置から聞こえる。ヘッドホンを着けていると、遠くても、中央で、はっきり聞こえる。
	 */
	private static float[] audioGains(Minecraft mc, DisplayBlockEntity be, double cx, double cy, double cz, double scale,
			boolean headphones) {
		if (mc.player == null || be.muted) {
			return new float[] { 0f, 0f };
		}
		double px = mc.player.getX();
		double py = mc.player.getY();
		double pz = mc.player.getZ();
		double yaw = Math.toRadians(mc.player.getYRot());
		// 聞く人の右向き(水平)。yaw 0 = 南向きのとき、右は西
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double gl = 0;
		double gr = 0;
		// 音は、PC に接続されたスピーカーからだけ聞こえる(接続がなければ無音。ヘッドホンを着けていれば、スピーカーなしでも聞こえる)
		java.util.List<double[]> sources = new ArrayList<>();
		for (BlockPos s : be.speakers) {
			double volume = 1.0;
			if (mc.level != null && mc.level.getBlockEntity(s) instanceof com.lampvideo.pc.SpeakerBlockEntity sb) {
				if (sb.soundMode != 0) {
					continue;   // 効果音・音符ブロックのスピーカーは、曲そのままの音を出さない
				}
				volume = sb.volume / 100.0;
			}
			sources.add(new double[] { s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, SPEAKER_RANGE, volume });
		}
		for (double[] src : sources) {
			double dx = src[0] - px;
			double dz = src[2] - pz;
			double dist = Math.sqrt(dx * dx + (src[1] - py) * (src[1] - py) + dz * dz);
			double g = Math.max(0.0, 1.0 - dist / src[3]) * src[4];
			if (g <= 0) {
				continue;
			}
			double h = Math.sqrt(dx * dx + dz * dz);
			double pan = h < 0.5 ? 0.0 : (rx * dx + rz * dz) / h;   // -1(左)〜1(右)
			gl += g * Math.min(1.0, 1.2 * Math.sqrt((1.0 - pan) / 2.0));
			gr += g * Math.min(1.0, 1.2 * Math.sqrt((1.0 + pan) / 2.0));
		}
		gl = Math.min(1.0, gl);
		gr = Math.min(1.0, gr);
		if (headphones) {
			// ヘッドホンは、スピーカーがなくても、PC につながった画面の音が、遠くても、中央で聞こえる
			double m = Math.max(0.85, Math.max(gl, gr));
			gl = m;
			gr = m;
		}
		float v = be.volume / 100f;
		return new float[] { (float) (gl * v), (float) (gr * v) };
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
