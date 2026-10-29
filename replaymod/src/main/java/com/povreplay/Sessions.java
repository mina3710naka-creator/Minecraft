package com.povreplay;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.client.Minecraft;

/** いまの接続(ワールドに入ってから出るまで)の記録。ワールドに入るたびに新しく作り、出るときに閉じる。 */
public final class Sessions {

	private static volatile SessionLog current;

	private Sessions() {
	}

	public static SessionLog current() {
		return current;
	}

	/** 接続を始めるときに呼ぶ。記録を始められなければ null。 */
	public static synchronized SessionLog begin() {
		if (current != null) {
			current.close();
			current = null;
		}
		try {
			Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("povreplay").resolve("sessions");
			String name = "session-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".povr";
			current = new SessionLog(dir.resolve(name));
			return current;
		} catch (IOException e) {
			PovReplay.LOGGER.error("セッションの記録を始められませんでした", e);
			return null;
		}
	}

	public static synchronized void end() {
		if (current != null) {
			current.close();
			current = null;
		}
	}
}
