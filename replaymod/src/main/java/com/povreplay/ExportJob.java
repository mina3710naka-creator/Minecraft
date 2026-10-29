package com.povreplay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * 「ここからここまでを動画にする」という仕事 1 件。録画を止めたときに作り、ワールドを出たときに、リプレイを再生して動画にする。
 * ゲームを閉じても消えないよう、ファイル(povreplay/jobs/*.job)にも書いておく。次にゲームを起動したときに続きができる。
 */
public record ExportJob(Path manifest, Path session, int startMs, int endMs, Path pcm, String name) {

	static Path jobsDir(Path gameDirectory) {
		return gameDirectory.resolve("povreplay").resolve("jobs");
	}

	static ExportJob create(Path gameDirectory, Path session, int startMs, int endMs, Path pcm, String name) throws IOException {
		Path manifest = jobsDir(gameDirectory).resolve(name + ".job");
		Files.createDirectories(manifest.getParent());
		Properties p = new Properties();
		p.setProperty("session", session.toString());
		p.setProperty("start", Integer.toString(startMs));
		p.setProperty("end", Integer.toString(endMs));
		p.setProperty("pcm", pcm.toString());
		p.setProperty("name", name);
		try (var out = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
			p.store(out, "povreplay export job");
		}
		return new ExportJob(manifest, session, startMs, endMs, pcm, name);
	}

	/** 残っている仕事を、すべて読む(壊れたものは飛ばす)。 */
	static List<ExportJob> loadAll(Path gameDirectory) {
		List<ExportJob> jobs = new ArrayList<>();
		Path dir = jobsDir(gameDirectory);
		if (!Files.isDirectory(dir)) {
			return jobs;
		}
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(f -> f.getFileName().toString().endsWith(".job")).sorted().forEach(f -> {
				try (var in = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
					Properties p = new Properties();
					p.load(in);
					Path session = Path.of(p.getProperty("session"));
					Path pcm = Path.of(p.getProperty("pcm"));
					if (Files.exists(session) && Files.exists(pcm)) {
						jobs.add(new ExportJob(f, session, Integer.parseInt(p.getProperty("start")), Integer.parseInt(p.getProperty("end")), pcm,
								p.getProperty("name")));
					} else {
						Files.deleteIfExists(f);
					}
				} catch (IOException | RuntimeException e) {
					PovReplay.LOGGER.warn("保存待ちの仕事を読めませんでした: " + f, e);
				}
			});
		} catch (IOException e) {
			PovReplay.LOGGER.warn("保存待ちの仕事の一覧を読めませんでした", e);
		}
		return jobs;
	}

	/** 仕事が終わったので、仕事のファイルと音の一時ファイルを消す。セッションの記録は、ほかに使う仕事がなければ消す。 */
	void finish(Path gameDirectory) {
		try {
			Files.deleteIfExists(manifest);
			Files.deleteIfExists(pcm);
			boolean used = loadAll(gameDirectory).stream().anyMatch(j -> j.session().equals(session));
			if (!used && !isCurrentSession(session)) {
				Files.deleteIfExists(session);
			}
		} catch (IOException e) {
			PovReplay.LOGGER.warn("一時ファイルを消せませんでした", e);
		}
	}

	private static boolean isCurrentSession(Path session) {
		SessionLog current = Sessions.current();
		return current != null && current.path().equals(session);
	}
}
