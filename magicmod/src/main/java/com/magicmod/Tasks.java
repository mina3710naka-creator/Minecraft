package com.magicmod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** 遅れて実行する処理・一定間隔で繰り返す処理(持続する魔法の範囲など)。サーバー側だけ。 */
public final class Tasks {

	private static final class Task {
		int wait;
		final int interval;
		int left;
		int index;
		final IntConsumer body;

		Task(int delay, int interval, int times, IntConsumer body) {
			this.wait = delay;
			this.interval = Math.max(1, interval);
			this.left = times;
			this.body = body;
		}
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Task> ADD = new ArrayList<>();

	private Tasks() {
	}

	public static void later(int delayTicks, Runnable r) {
		ADD.add(new Task(Math.max(0, delayTicks), 1, 1, i -> r.run()));
	}

	/** delay 後から interval ごとに times 回。引数は 0 から数えた回数。 */
	public static void repeat(int delayTicks, int interval, int times, IntConsumer body) {
		ADD.add(new Task(Math.max(0, delayTicks), interval, times, body));
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TASKS.addAll(ADD);
			ADD.clear();
			Iterator<Task> it = TASKS.iterator();
			while (it.hasNext()) {
				Task t = it.next();
				if (t.wait > 0) {
					t.wait--;
					continue;
				}
				try {
					t.body.accept(t.index++);
				} catch (Exception e) {
					MagicMod.LOGGER.warn("[magicmod] task failed", e);
					t.left = 0;
				}
				t.left--;
				if (t.left <= 0) {
					it.remove();
				} else {
					t.wait = t.interval - 1;
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			TASKS.clear();
			ADD.clear();
		});
	}
}
