package com.lampvideo.trolley;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;

/** トロッコムービーで選べる動画。.minecraft/lampvideo(と、その中の media)に入れた動画ファイルを、サーバー側で探す。 */
final class TrolleyMedia {

	private static final Set<String> EXT = Set.of("mp4", "mkv", "webm", "avi", "mov", "m4v");

	private TrolleyMedia() {
	}

	private static List<Path> dirs() {
		Path root = FabricLoader.getInstance().getGameDir().resolve("lampvideo");
		return List.of(root.resolve("media"), root);
	}

	static List<String> list() {
		List<String> out = new ArrayList<>();
		for (Path dir : dirs()) {
			if (!Files.isDirectory(dir)) {
				continue;
			}
			try (Stream<Path> s = Files.list(dir)) {
				s.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).filter(n -> {
					int dot = n.lastIndexOf('.');
					return dot > 0 && EXT.contains(n.substring(dot + 1).toLowerCase(Locale.ROOT));
				}).filter(n -> !out.contains(n)).forEach(out::add);
			} catch (IOException e) {
				// 読めなければ、飛ばす
			}
		}
		java.util.Collections.sort(out);
		return out;
	}

	/** 名前から、実際のファイル(決まったフォルダの中だけ。パスの区切りは無視)。なければ null。 */
	static Path resolve(String name) {
		String n = name.replace('\\', '/');
		n = n.substring(n.lastIndexOf('/') + 1);
		if (n.isEmpty()) {
			return null;
		}
		for (Path dir : dirs()) {
			Path p = dir.resolve(n);
			if (Files.isRegularFile(p)) {
				return p;
			}
		}
		return null;
	}
}
