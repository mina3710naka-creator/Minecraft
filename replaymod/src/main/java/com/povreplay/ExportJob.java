package com.povreplay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * 保存してある録画(クリップ)1 件: 記録(セッション)のうち「ここからここまで」と、そのときの音。
 * 最大 10 件まで保存でき、「録画一覧」から、いつでも動画に書き出せる(書き出したあとも、削除するまで残る)。
 * ゲームを閉じても消えないよう、ファイル(povreplay/jobs/*.job)にも書いておく。
 */
public final class ExportJob {

	/** 保存しておける録画の数。 */
	public static final int MAX_CLIPS = 10;

	private final Path manifest;
	private final Path session;
	private final int startMs;
	private final int endMs;
	private final Path pcm;
	private final String name;
	private final long created;
	private volatile boolean exported;

	private ExportJob(Path manifest, Path session, int startMs, int endMs, Path pcm, String name, long created, boolean exported) {
		this.manifest = manifest;
		this.session = session;
		this.startMs = startMs;
		this.endMs = endMs;
		this.pcm = pcm;
		this.name = name;
		this.created = created;
		this.exported = exported;
	}

	public Path session() {
		return session;
	}

	public int startMs() {
		return startMs;
	}

	public int endMs() {
		return endMs;
	}

	public Path pcm() {
		return pcm;
	}

	public String name() {
		return name;
	}

	public boolean exported() {
		return exported;
	}

	/** 録画の長さ(秒)。 */
	public double seconds() {
		return (endMs - startMs) / 1000.0;
	}

	/** 録画した日時(一覧に出す)。 */
	public String createdText() {
		return DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(created));
	}

	static Path jobsDir(Path gameDirectory) {
		return gameDirectory.resolve("povreplay").resolve("jobs");
	}

	static ExportJob create(Path gameDirectory, Path session, int startMs, int endMs, Path pcm, String name) throws IOException {
		Path manifest = jobsDir(gameDirectory).resolve(name + ".job");
		Files.createDirectories(manifest.getParent());
		ExportJob job = new ExportJob(manifest, session, startMs, endMs, pcm, name, System.currentTimeMillis(), false);
		job.save();
		return job;
	}

	private void save() throws IOException {
		Properties p = new Properties();
		p.setProperty("session", session.toString());
		p.setProperty("start", Integer.toString(startMs));
		p.setProperty("end", Integer.toString(endMs));
		p.setProperty("pcm", pcm.toString());
		p.setProperty("name", name);
		p.setProperty("created", Long.toString(created));
		p.setProperty("exported", Boolean.toString(exported));
		try (var out = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
			p.store(out, "povreplay clip");
		}
	}

	/** 動画に書き出した、と覚える(録画は、削除するまで残る)。 */
	void markExported() {
		exported = true;
		try {
			save();
		} catch (IOException e) {
			PovReplay.LOGGER.warn("書き出し済みの印を書けませんでした", e);
		}
	}

	/** 残っている録画を、すべて読む(壊れたものは飛ばす)。 */
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
								p.getProperty("name"), Long.parseLong(p.getProperty("created", Long.toString(Files.getLastModifiedTime(f).toMillis()))),
								Boolean.parseBoolean(p.getProperty("exported", "false"))));
					} else {
						Files.deleteIfExists(f);   // 中身がなくなった録画は、一覧から外す
					}
				} catch (IOException | RuntimeException e) {
					PovReplay.LOGGER.warn("保存してある録画を読めませんでした: " + f, e);
				}
			});
		} catch (IOException e) {
			PovReplay.LOGGER.warn("保存してある録画の一覧を読めませんでした", e);
		}
		return jobs;
	}

	/** この録画を削除する(ファイルごと)。セッションの記録は、ほかの録画が使っていなければ、消す。 */
	void delete(Path gameDirectory) {
		try {
			Files.deleteIfExists(manifest);
			Files.deleteIfExists(pcm);
			boolean used = loadAll(gameDirectory).stream().anyMatch(j -> j.session().equals(session));
			if (!used && !isCurrentSession(session)) {
				Files.deleteIfExists(session);
			}
		} catch (IOException e) {
			PovReplay.LOGGER.warn("録画のファイルを消せませんでした", e);
		}
	}

	private static boolean isCurrentSession(Path session) {
		SessionLog current = Sessions.current();
		return current != null && current.path().equals(session);
	}
}
