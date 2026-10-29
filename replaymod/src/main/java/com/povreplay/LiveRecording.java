package com.povreplay;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 録画ボタンの中身。プレイ中にすることは、とても少ない:
 *  - 始める: 記録(SessionLog)の中の、いまの時刻を「開始」として覚える。音の録音(AudioCapture)を始める。
 *  - 毎フレーム: 視点の状態(FrameSample)を、記録に書き足す。
 *  - 止める: 終わりの時刻を覚えて、「ここからここまでを動画にする」という仕事(ExportJob)を作る。動画への変換は、ワールドを出たときに行う。
 */
public final class LiveRecording {

	private static volatile boolean active;
	private static int startMs;
	private static long startNanos;
	private static AudioCapture audio;
	private static SessionLog log;
	/** 動画にするのを待っている仕事。 */
	private static final List<ExportJob> PENDING = new ArrayList<>();

	private LiveRecording() {
	}

	public static boolean isRecording() {
		return active;
	}

	public static long seconds() {
		return active ? (System.nanoTime() - startNanos) / 1_000_000_000L : 0;
	}

	public static synchronized List<ExportJob> pending() {
		return new ArrayList<>(PENDING);
	}

	public static synchronized void addPending(List<ExportJob> jobs) {
		PENDING.addAll(jobs);
	}

	public static synchronized void removePending(ExportJob job) {
		PENDING.remove(job);
	}

	public static void toggle(Minecraft mc) {
		if (active) {
			stop(mc);
		} else {
			start(mc);
		}
	}

	public static void start(Minecraft mc) {
		if (active) {
			return;
		}
		SessionLog session = Sessions.current();
		if (session == null || ReplayMode.active() || mc.level == null) {
			PovReplay.message(mc, Component.literal("[録画] ワールドの中でだけ、録画できます").withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (ReplayEngine.running()) {
			return;
		}
		try {
			Path dir = mc.gameDirectory.toPath().resolve("povreplay").resolve("tmp");
			java.nio.file.Files.createDirectories(dir);
			String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			Path pcm = dir.resolve("audio-" + stamp + ".pcm");
			log = session;
			startNanos = System.nanoTime();
			startMs = session.now();
			audio = new AudioCapture(pcm, startNanos);
			active = true;
			session.add(SessionLog.MARK, new byte[] { 1 });
			PovReplay.message(mc, Component.literal("[録画] 録画を始めました。止めたあと、ワールドを出ると、動画にして保存します").withStyle(ChatFormatting.GREEN));
		} catch (IOException | RuntimeException e) {
			PovReplay.LOGGER.error("録画を始められませんでした", e);
			PovReplay.message(mc, Component.literal("[録画] 始められませんでした: " + e.getMessage()).withStyle(ChatFormatting.RED));
		}
	}

	public static void stop(Minecraft mc) {
		if (!active) {
			return;
		}
		active = false;
		int endMs = log.now();
		log.add(SessionLog.MARK, new byte[] { 2 });
		audio.stop();
		Path pcm = audio.file();
		audio = null;
		if (endMs - startMs < 500) {
			PovReplay.message(mc, Component.literal("[録画] 短すぎるので、保存しませんでした").withStyle(ChatFormatting.YELLOW));
			try {
				java.nio.file.Files.deleteIfExists(pcm);
			} catch (IOException ignored) {
				// 消せなくても続ける
			}
			return;
		}
		try {
			String name = "clip-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			ExportJob job = ExportJob.create(mc.gameDirectory.toPath(), log.path(), startMs, endMs, pcm, name);
			addPending(List.of(job));
			PovReplay.message(mc, Component.literal(String.format("[録画] 録画を止めました(%.1f 秒)。ワールドから出ると、動画にして保存します(メニューの「保存して出る」でも)",
					(endMs - startMs) / 1000.0)).withStyle(ChatFormatting.GREEN));
		} catch (IOException e) {
			PovReplay.LOGGER.error("保存の仕事を作れませんでした", e);
			PovReplay.message(mc, Component.literal("[録画] 保存の準備に失敗しました: " + e.getMessage()).withStyle(ChatFormatting.RED));
		}
	}

	/** 世界から出るとき: 録画中なら、止めて仕事にする。 */
	public static void stopIfRecording(Minecraft mc) {
		if (active) {
			stop(mc);
		}
	}

	/** 描画のたびに、視点の状態を記録に書き足す(LevelExtractorMixin から)。 */
	public static void record(FrameSample sample) {
		SessionLog session = log;
		if (active && session != null) {
			sample.time = session.now();
			session.add(SessionLog.CAMERA, sample.toBytes());
		}
	}
}
