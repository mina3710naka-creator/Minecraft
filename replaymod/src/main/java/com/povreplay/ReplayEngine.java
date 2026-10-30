package com.povreplay;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.povreplay.mixin.MinecraftAccessor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.Connection;
import net.minecraft.network.UnconfiguredPipelineHandler;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlags;

/**
 * 記録したセッションを、ゲームの中で再生して、プレイヤー視点を 1 フレームずつ動画にする。
 *
 * 本物のサーバーの代わりに、記録した通信を、偽の接続(EmbeddedChannel)からゲームに流し込む。ゲームは、サーバーから届いたものと
 * 同じように処理するので、ワールドが組み立て直される。ゲームの時計は、1 フレームごとに 1/30 秒だけ進む仮想の時計にして、
 * 絵が読み出し終わるまでは進めない。だから、重い場面でも、絵は飛ばず、いつも 30fps の動画になる。
 * プレイヤーの視点・手の動きは、録画のときに記録した値で上書きする。
 */
public final class ReplayEngine {

	public static final int FPS = 30;
	/** 1 フレームで進む時間(ミリ秒)。 */
	private static final double FRAME_MS = 1000.0 / FPS;
	/** 録画の始まりの、これだけ手前から再生して、ワールドを落ち着かせる(エンティティの動きなど)。 */
	private static final int PREROLL_MS = 2000;
	/** 視点の記録を、これだけ先まで読んでおく(なめらかにつなぐために、あとの値が要る)。 */
	private static final int LOOKAHEAD_MS = 300;

	private enum Phase {
		LOADING, PREROLL, CAPTURE, DRAIN
	}

	private static final class State {
		ExportJob job;
		SessionReader reader;
		Connection connection;
		EmbeddedChannel channel;
		Phase phase = Phase.LOADING;
		final ArrayDeque<SessionReader.Entry> frames = new ArrayDeque<>();
		final ArrayDeque<FrameSample> samples = new ArrayDeque<>();
		FrameSample previous;
		boolean eof;
		boolean playPhase;
		double sessionMs;
		long gameBase;
		double gameElapsed;
		int loadedTicks;
		FrameCapture capture;
		Path videoTemp;
		int frameIndex;
		boolean frameCaptured = true;
		int totalFrames;
		FrameSample current;
		boolean aborting;
		boolean failed;
	}

	private static State state;
	private static boolean savedHideGui;
	/** 動画に変換している最中の仕事(もう一度始めないように)。 */
	private static final java.util.Set<ExportJob> EXPORTING = new java.util.HashSet<>();
	private static String status = "";
	private static float progress;
	private static boolean cancelRequested;

	private ReplayEngine() {
	}

	/** いま、描画の大きさを、動画の大きさに固定してよいか(絵を取っている間)。 */
	public static boolean forcesRenderSize() {
		State s = state;
		return s != null;
	}

	private static final java.util.concurrent.atomic.AtomicInteger EXPORT_THREADS = new java.util.concurrent.atomic.AtomicInteger();

	/** 動画に変換している最中か(裏のスレッドで)。 */
	public static boolean exporting() {
		return EXPORT_THREADS.get() > 0;
	}

	public static boolean running() {
		return state != null;
	}

	public static FrameSample currentSample() {
		State s = state;
		return s == null ? null : s.current;
	}

	public static String statusText() {
		return status;
	}

	public static float progress() {
		return progress;
	}

	public static void cancel() {
		cancelRequested = true;
	}

	// ---- 始める ----

	/** 保存待ちの仕事があれば、1 件目を始める。ワールドの外(タイトル画面など)で呼ぶ。 */
	public static void runPending(Minecraft mc) {
		if (state != null || mc.level != null || LiveRecording.isRecording()) {
			return;
		}
		for (ExportJob job : LiveRecording.pending()) {
			if (!EXPORTING.contains(job)) {
				start(mc, job);
				return;
			}
		}
	}

	private static void start(Minecraft mc, ExportJob job) {
		RecConfig.load(mc.gameDirectory.toPath());
		State s = new State();
		s.job = job;
		cancelRequested = false;
		try {
			s.reader = new SessionReader(job.session());
			// ログイン中の通信は、使わない(設定の段階から再生する)
			while (s.reader.peek() != null) {
				SessionReader.Entry e = s.reader.next();
				if (e.type() == SessionLog.PROTOCOL && new String(e.data()).equals("CONFIGURATION")) {
					break;
				}
			}
			s.sessionMs = Math.max(0, job.startMs() - PREROLL_MS);
			s.totalFrames = (int) Math.ceil((job.endMs() - job.startMs()) / FRAME_MS);
			s.videoTemp = Path.of(job.pcm() + ".video.tmp");
			s.gameBase = Util.getMillis();
			ReplayMode.setVirtualMillis(s.gameBase);
			ReplayMode.setActive(true);
			// 画面のプレビューも、動画と同じになるよう、ホットバーなどの表示(HUD)は、再生中は隠す
			savedHideGui = mc.gui.hud.isHidden();
			if (!savedHideGui) {
				mc.gui.hud.toggle();
			}
			connect(mc, s);
			state = s;
			status = "ワールドを読み込んでいます";
			progress = 0;
			PovReplay.LOGGER.info("リプレイを始めます: " + job.session() + "(" + job.startMs() + "〜" + job.endMs() + " ms)");
		} catch (IOException | RuntimeException e) {
			PovReplay.LOGGER.error("リプレイを始められませんでした", e);
			ReplayMode.setActive(false);
			restoreHud(mc);
			PovReplay.message(mc, Component.literal("[録画] 動画にできませんでした: " + e.getMessage()).withStyle(ChatFormatting.RED));
			LiveRecording.removePending(job);
		}
	}

	/** 再生中に隠していた HUD を、元に戻す。 */
	private static void restoreHud(Minecraft mc) {
		if (mc.gui.hud.isHidden() != savedHideGui) {
			mc.gui.hud.toggle();
		}
	}

	private static void connect(Minecraft mc, State s) {
		Connection connection = new Connection(PacketFlow.CLIENTBOUND);
		s.connection = connection;
		s.channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel channel) {
				Connection.configureInMemoryPipeline(channel.pipeline(), PacketFlow.CLIENTBOUND);
				connection.configurePacketHandler(channel.pipeline());
				// 本物の接続は、最初にハンドシェイクを送って、送信側の段階が「未設定」になる。偽の接続では送らないので、ここで同じ状態にする
				channel.pipeline().replace("encoder", "outbound_config", new UnconfiguredPipelineHandler.Outbound());
				// サーバーとのやりとりが必要になる通信は、再生では使わない
				channel.pipeline().addBefore("packet_handler", "povreplay_filter", new ChannelInboundHandlerAdapter() {
					@Override
					public void channelRead(ChannelHandlerContext ctx, Object msg) {
						String name = msg.getClass().getSimpleName();
						if (name.contains("ResourcePack") || name.contains("Disconnect") || name.contains("Transfer")
								|| name.contains("CookieRequest") || name.contains("CodeOfConduct")) {
							return;
						}
						ctx.fireChannelRead(msg);
					}
				});
			}
		});
		LevelLoadTracker tracker = new LevelLoadTracker(0L);
		CommonListenerCookie cookie = new CommonListenerCookie(tracker, mc.getGameProfile(),
				mc.getTelemetryManager().createWorldSessionManager(false, null, null, java.util.UUID.randomUUID()),
				ClientRegistryLayer.createRegistryAccess().compositeAccess(), FeatureFlags.DEFAULT_FLAGS, null, null, null,
				Map.of(), null, Map.of(), ServerLinks.EMPTY, new HashMap<>(), false);
		ClientConfigurationPacketListenerImpl listener = new ClientConfigurationPacketListenerImpl(mc, connection, cookie);
		connection.setupInboundProtocol(ConfigurationProtocols.CLIENTBOUND, listener);
		connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
		((MinecraftAccessor) mc).povreplay$setPendingConnection(connection);
	}

	// ---- 毎フレーム ----

	/** 毎フレームの最初に呼ばれる(MinecraftMixin)。仮想の時計を進めて、時刻になった通信を流し込む。 */
	public static void beforeTick() {
		State s = state;
		if (s == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		try {
			tickJob(mc, s);
		} catch (RuntimeException e) {
			PovReplay.LOGGER.error("リプレイの再生に失敗しました", e);
			if (state == s) {
				finishJob(mc, s, false);
			}
		}
	}

	private static void tickJob(Minecraft mc, State s) {
		if ((cancelRequested || s.failed || (s.capture != null && s.capture.failed())) && s.phase != Phase.DRAIN) {
			s.aborting = true;
			if (s.capture != null && s.capture.pending() > 0) {
				// 読み出し中の絵が届くまで待ってから、片づける(届くのは、あとのフレームの描画のとき)
				s.phase = Phase.DRAIN;
				status = "中止しています";
				return;
			}
			finishJob(mc, s, false);
			return;
		}
		boolean hold = false;
		switch (s.phase) {
			case LOADING -> {
				// 場面の少し手前までの通信を、まとめて流し込む。ワールドが読み込まれる(画面が消える)まで、記録の時刻は進めない
				readAhead(s, s.sessionMs);
				feed(s, s.sessionMs);
				if (mc.level != null && mc.player != null && (mc.gui.screen() == null || mc.gui.screen() instanceof ExportScreen)) {
					if (++s.loadedTicks > 10) {
						s.phase = Phase.PREROLL;
						status = "再生しています";
					}
				}
			}
			case PREROLL -> {
				s.sessionMs += FRAME_MS;
				readAhead(s, s.sessionMs);
				feed(s, s.sessionMs);
				s.current = sampleAt(s, (int) s.sessionMs);
				applyPlayer(mc, s);
				if (s.sessionMs >= s.job.startMs()) {
					s.phase = Phase.CAPTURE;
					s.sessionMs = s.job.startMs();
					s.frameIndex = 0;
					s.frameCaptured = false;
					try {
						s.capture = new FrameCapture(s.videoTemp, RecConfig.width, RecConfig.height);
					} catch (IOException e) {
						PovReplay.LOGGER.error("一時ファイルを作れませんでした", e);
						s.failed = true;
					}
				}
			}
			case DRAIN -> {
				hold = true;
				if (s.capture == null || s.capture.pending() == 0) {
					finishJob(mc, s, !s.aborting);
					return;
				}
			}
			case CAPTURE -> {
				if (s.frameCaptured && s.capture != null && s.capture.pending() <= 1) {
					// 前のフレームの読み出しを始めた。次のフレームへ進む
					s.frameIndex++;
					s.frameCaptured = false;
					s.sessionMs = s.job.startMs() + s.frameIndex * FRAME_MS;
					if (s.frameIndex >= s.totalFrames) {
						// 最後のフレームまで読み出しを始めた。読み出し中の絵が届くのを待つ(届くのは、あとのフレームの描画のとき)
						s.phase = Phase.DRAIN;
						status = "最後の絵を待っています";
						return;
					}
				} else if (s.frameCaptured) {
					hold = true;
				}
				readAhead(s, s.sessionMs);
				feed(s, s.sessionMs);
				s.current = sampleAt(s, (int) s.sessionMs);
				applyPlayer(mc, s);
				progress = Math.min(1F, s.frameIndex / (float) Math.max(1, s.totalFrames)) * 0.5F;
				status = String.format("再生して絵を取っています %d / %d フレーム", s.frameIndex, s.totalFrames);
			}
		}
		if (!hold) {
			s.gameElapsed += FRAME_MS;
		}
		ReplayMode.setVirtualMillis(s.gameBase + (long) s.gameElapsed);
		// 動画にしている画面を、いつも出しておく(メニューを開こうとしたときも、この画面にする)
		if (s.phase != Phase.LOADING && mc.level != null
				&& (mc.gui.screen() == null || mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen)) {
			mc.gui.setScreen(new ExportScreen());
		}
	}

	/** プレイヤーの位置・向き・持っているスロットを、記録に合わせる(再生中のプレイヤーは、動かさないので)。 */
	private static void applyPlayer(Minecraft mc, State s) {
		FrameSample f = s.current;
		if (f == null || mc.player == null) {
			return;
		}
		var p = mc.player;
		double y = f.camY - p.getEyeHeight();
		p.setPos(f.camX, y, f.camZ);
		p.xo = f.camX;
		p.yo = y;
		p.zo = f.camZ;
		p.xOld = f.camX;
		p.yOld = y;
		p.zOld = f.camZ;
		p.setYRot(f.camYRot);
		p.setXRot(f.camXRot);
		p.yRotO = f.camYRot;
		p.xRotO = f.camXRot;
		if (p.getInventory().getSelectedSlot() != f.selectedSlot) {
			p.getInventory().setSelectedSlot(f.selectedSlot);
		}
	}

	// ---- 記録の読み出し ----

	/** 記録を、untilMs + 先読みの分まで読んで、通信と視点に分ける。 */
	private static void readAhead(State s, double untilMs) {
		if (s.eof) {
			return;
		}
		double limit = untilMs + LOOKAHEAD_MS;
		while (true) {
			SessionReader.Entry e = s.reader.peek();
			if (e == null) {
				s.eof = true;
				return;
			}
			if (e.time() > limit && s.playPhase) {
				return;
			}
			s.reader.next();
			switch (e.type()) {
				case SessionLog.FRAME, SessionLog.PROTOCOL -> s.frames.add(e);
				case SessionLog.CAMERA -> s.samples.add(FrameSample.fromBytes(e.data()));
				default -> {
				}
			}
			if (e.type() == SessionLog.PROTOCOL && new String(e.data()).equals("PLAY")) {
				s.playPhase = true;
			}
		}
	}

	/** 時刻になった通信を、ゲームに流し込む。設定の段階の通信は、時刻にかかわらず、すぐに流す。 */
	private static void feed(State s, double untilMs) {
		try {
			while (true) {
				SessionReader.Entry e = s.frames.peek();
				if (e == null) {
					return;
				}
				if (e.type() == SessionLog.PROTOCOL) {
					s.frames.poll();
					continue;
				}
				boolean configPhase = s.connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl;
				if (!configPhase && e.time() > untilMs) {
					return;
				}
				s.frames.poll();
				ByteBuf buf = Unpooled.wrappedBuffer(e.data());
				s.channel.writeInbound(buf);
				s.channel.checkException();
			}
		} catch (RuntimeException ex) {
			PovReplay.LOGGER.error("リプレイの通信を流し込めませんでした", ex);
			s.failed = true;
		}
	}

	/** 時刻 t の視点の状態。前後の記録をなめらかにつなぐ。 */
	private static FrameSample sampleAt(State s, int t) {
		while (!s.samples.isEmpty() && s.samples.peek().time <= t) {
			s.previous = s.samples.poll();
		}
		FrameSample next = s.samples.peek();
		if (s.previous == null) {
			return next;
		}
		return next == null ? s.previous : FrameSample.between(s.previous, next, t);
	}

	/** GUI を描く直前に呼ばれる(GameRendererMixin)。 */
	public static void onFrame(RenderTarget target) {
		State s = state;
		if (s == null || s.phase != Phase.CAPTURE || s.frameCaptured || s.capture == null) {
			return;
		}
		if (s.capture.capture(target, s.frameIndex)) {
			s.frameCaptured = true;
		}
	}

	// ---- 終わる ----

	private static void finishJob(Minecraft mc, State s, boolean complete) {
		state = null;
		int[] indexes = new int[0];
		boolean ok = complete;
		if (s.capture != null) {
			try {
				indexes = s.capture.finish();
			} catch (IOException | InterruptedException e) {
				PovReplay.LOGGER.error("絵を書き終えられませんでした", e);
				ok = false;
			}
			s.capture.release();
		}
		try {
			s.reader.close();
		} catch (IOException ignored) {
			// 閉じるだけ
		}
		// 再生中の接続を切る(この間は、再生中のままにして、切断の知らせを、本物の接続のものと取り違えない)
		mc.disconnectFromWorld(Component.literal("リプレイが終わりました"));
		ReplayMode.setVirtualMillis(Util.getMillis());
		ReplayMode.setActive(false);
		restoreHud(mc);
		mc.gui.setScreen(new TitleScreen());

		ExportJob job = s.job;
		if (!ok || indexes.length == 0) {
			deleteQuietly(s.videoTemp);
			if (cancelRequested) {
				PovReplay.toast(mc, "録画", "中止しました(次にゲームを起動したときに、もう一度保存します)");
				cancelRequested = false;
				EXPORTING.add(job);   // この起動中は、もう始めない(ファイルは残してあるので、次の起動で続きができる)
				return;
			}
			PovReplay.message(mc, Component.literal("[録画] 動画にできませんでした(ログを見てください)").withStyle(ChatFormatting.RED));
			PovReplay.toast(mc, "録画", "動画にできませんでした(ログを見てください)");
			EXPORTING.add(job);
			return;
		}
		EXPORTING.add(job);
		int[] frames = indexes;
		int padW = s.capture.padW;
		int padH = s.capture.padH;
		status = "動画に変換しています";
		progress = 0.5F;
		PovReplay.toast(mc, "録画", "動画に変換しています…(ゲームは続けて遊べます)");
		EXPORT_THREADS.incrementAndGet();
		Thread t = new Thread(() -> {
			Path saved = null;
			try {
				Path out = chooseFile(mc, job.name());
				saved = Exporter.run(mc, out, s.videoTemp, job.pcm(), RecConfig.width, RecConfig.height, padW, padH, frames, s.totalFrames);
			} catch (IOException | RuntimeException e) {
				PovReplay.LOGGER.error("動画の保存に失敗しました", e);
			} finally {
				deleteQuietly(s.videoTemp);
			}
			Path result = saved;
			EXPORT_THREADS.decrementAndGet();
			SaveIndicator.done(result == null ? "動画を保存できませんでした(ログを見てください)" : "保存しました: " + result);
			mc.execute(() -> {
				if (result == null) {
					PovReplay.message(mc, Component.literal("[録画] 保存できませんでした(ログを見てください)").withStyle(ChatFormatting.RED));
					PovReplay.toast(mc, "録画", "保存できませんでした(ログを見てください)");
					return;
				} else {
					Component link = Component.literal(result.toString()).withStyle(ChatFormatting.UNDERLINE)
							.withStyle(st -> st.withClickEvent(new ClickEvent.OpenFile(result.toAbsolutePath().toFile())));
					PovReplay.message(mc, Component.literal("[録画] 保存しました: ").withStyle(ChatFormatting.GREEN).append(link));
					PovReplay.toast(mc, "録画を保存しました", result.getFileName().toString());
					job.finish(mc.gameDirectory.toPath());
				}
				LiveRecording.removePending(job);
				EXPORTING.remove(job);
				progress = 1F;
			});
		}, "povreplay-export");
		t.setDaemon(true);
		t.start();
		// 続きの仕事があれば、すぐに次を始める(変換は、裏で続く)
		runNextSoon = true;
	}

	/** タイトル画面に戻ったあとに、次の仕事を始めてよいか。 */
	static boolean runNextSoon;

	private static Path chooseFile(Minecraft mc, String name) throws IOException {
		Path dir = Path.of(System.getProperty("user.home", "."), "Downloads");
		if (!Files.isDirectory(dir)) {
			dir = mc.gameDirectory.toPath().resolve("recordings");
			Files.createDirectories(dir);
		}
		return dir.resolve("minecraft-" + name.replace("clip-", "") + ".mp4");
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			PovReplay.LOGGER.warn("一時ファイルを消せませんでした: " + path, e);
		}
	}

	static String stamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
	}
}
