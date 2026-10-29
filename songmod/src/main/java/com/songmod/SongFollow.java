package com.songmod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 追従 ON のプレイヤーを、曲の再生位置の少し上へなめらかに動かす。
 *
 * <p>リピーターの遅延は音の間隔ごとに違うので、回路の上を素直に追うと速さがばらつく。そこで、
 * 信号の先頭が見つかったときに「曲の時刻」を合わせ、あとは時刻から位置を求めて、
 * 前後 {@link #WINDOW} 目盛りぶんの位置の平均へ向かって動かす(曲の先は分かっているので先読みもできる)。
 * 位置が決まっているあいだは重力を切り、速度を距離に比例させて近づける。
 */
public final class SongFollow {

	/** 再生位置の真上、このブロック数の高さに浮かぶ */
	private static final double HOVER = 3.0;
	/** 前後それぞれ何目盛り(1 目盛り = 0.1 秒)ぶんの位置を平均するか。大きいほど速さがなめらか */
	private static final double WINDOW = 30.0;
	private static final int SAMPLES = 13;
	/** 1 tick あたり距離の何割ぶん進むか、と最高速度(ブロック/tick) */
	private static final double GAIN = 0.2;
	private static final double MAX_SPEED = 2.5;

	/** 追従のようす: どの曲か、曲の時刻 0 に当たるゲーム時刻、最後に見た先頭の番号 */
	private static final class Run {
		final SongRegistry.Entry entry;
		long start;
		int last = -1;

		Run(SongRegistry.Entry entry) {
			this.entry = entry;
		}
	}

	private static final Set<UUID> ON = new HashSet<>();
	private static final Set<UUID> FLOATING = new HashSet<>();
	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private SongFollow() {
	}

	public static boolean toggle(ServerPlayer p) {
		if (ON.remove(p.getUUID())) {
			release(p);
			return false;
		}
		ON.add(p.getUUID());
		return true;
	}

	private static void release(ServerPlayer p) {
		RUNS.remove(p.getUUID());
		if (FLOATING.remove(p.getUUID())) {
			p.setNoGravity(false);
		}
	}

	public static void tick(MinecraftServer server) {
		if (ON.isEmpty()) {
			return;
		}
		ON.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		FLOATING.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		RUNS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		for (UUID id : ON) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			ServerLevel level = (ServerLevel) p.level();
			long now = level.getGameTime();

			// 信号の先頭が見つかったら、その時刻で曲の時計を合わせ直す(リピーター 1 目盛り = 2 tick)
			SongRegistry.Playing playing = SongRegistry.playing(level, p.position());
			Run run = RUNS.get(id);
			if (playing != null) {
				if (run == null || run.entry != playing.entry() || playing.index() < run.last) {
					run = new Run(playing.entry());
					RUNS.put(id, run);
				}
				if (playing.index() != run.last) {
					run.last = playing.index();
					run.start = now - 2L * playing.entry().times[playing.index()];
				}
			}
			if (run == null) {
				release(p);
				continue;
			}
			if (playing == null && SongRest.resting(run.entry)) {
				run.start++; // 休符のあいだは曲の時計を止める
			}
			double clock = (now - run.start) / 2.0;
			if (playing == null && clock > run.entry.endTime() + WINDOW) {
				release(p); // 曲が終わった
				continue;
			}

			// 前後の位置の平均
			double sx = 0;
			double sy = 0;
			double sz = 0;
			for (int i = 0; i < SAMPLES; i++) {
				double t = clock + WINDOW * (2.0 * i / (SAMPLES - 1) - 1.0);
				Vec3 v = run.entry.at(t);
				sx += v.x;
				sy += v.y;
				sz += v.z;
			}
			Vec3 target = new Vec3(sx / SAMPLES, sy / SAMPLES + HOVER, sz / SAMPLES);

			if (FLOATING.add(id)) {
				p.setNoGravity(true);
			}
			Vec3 delta = target.subtract(p.position());
			double dist = delta.length();
			if (dist < 1.0E-3) {
				continue;
			}
			double speed = Math.min(dist * GAIN, MAX_SPEED);
			p.setDeltaMovement(delta.scale(speed / dist));
			p.fallDistance = 0;
			p.connection.send(new ClientboundSetEntityMotionPacket(p));
		}
	}
}
