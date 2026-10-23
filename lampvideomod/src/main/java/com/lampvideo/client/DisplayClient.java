package com.lampvideo.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.lampvideo.display.DisplayBlockEntity;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** ディスプレイのクライアント側: 再生の開始/停止、音量、メディアフォルダ。 */
public final class DisplayClient {

	/** これより遠いと、再生を止める(近づくと、経過時間から途中で始め直す)。 */
	private static final double MAX_DISTANCE = 64.0;
	/** 音が聞こえる距離。この距離で無音になる。 */
	private static final double HEAR_DISTANCE = 32.0;

	private static final List<MediaSession> OPEN = new ArrayList<>();

	private DisplayClient() {
	}

	public static void init() {
		BlockEntityRendererRegistry.register(com.lampvideo.display.ModDisplay.DISPLAY_ENTITY, DisplayRenderer::new);
		DisplayBlockEntity.CLIENT_TICK = DisplayClient::tick;
		DisplayBlockEntity.CLIENT_REMOVED = DisplayClient::release;
		DisplayBlockEntity.OPEN_GUI = pos -> Minecraft.getInstance().setScreenAndShow(new DisplayScreen(pos));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> closeAll());
		prepareFolder();
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

	/** ブロックエンティティが毎 tick 呼ぶ。 */
	static void tick(DisplayBlockEntity be) {
		Minecraft mc = Minecraft.getInstance();
		MediaSession s = (MediaSession) be.clientSession;
		BlockPos pos = be.getBlockPos();
		double dist = mc.player == null ? Double.MAX_VALUE
				: Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
		if (!be.playing || be.media.isEmpty() || dist > MAX_DISTANCE) {
			release(be);
			return;
		}
		if (s != null && (s.generation != be.generation || !s.media.equals(be.media))) {
			release(be);
			s = null;
		}
		if (s == null) {
			Path file = resolve(be.media);
			long seek = Math.max(0, System.currentTimeMillis() - be.startMs);
			if (file == null) {
				return;
			}
			s = new MediaSession(file, be.media, be.generation, be.mode, seek);
			OPEN.add(s);
			be.clientSession = s;
		}
		s.mode = be.mode;
		s.setVolume((float) Math.max(0.0, 1.0 - dist / HEAR_DISTANCE));
	}

	static void release(DisplayBlockEntity be) {
		MediaSession s = (MediaSession) be.clientSession;
		if (s != null) {
			s.close();
			OPEN.remove(s);
			be.clientSession = null;
		}
	}

	static void closeAll() {
		for (MediaSession s : new ArrayList<>(OPEN)) {
			s.close();
		}
		OPEN.clear();
	}

	/** 描画用: 映像が1枚でも来ていれば、そのテクスチャ。なければ null(消灯の見た目のまま)。 */
	static Identifier textureFor(DisplayBlockEntity be) {
		MediaSession s = (MediaSession) be.clientSession;
		return s != null && !s.failed && s.update() ? s.textureId : null;
	}

	static String errorOf(DisplayBlockEntity be) {
		MediaSession s = (MediaSession) be.clientSession;
		return s == null ? "" : s.error;
	}
}
