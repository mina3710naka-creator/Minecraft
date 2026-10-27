package com.songmod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 追従 ON のプレイヤーを、曲の再生位置(信号の先頭のリピーター)の少し上へなめらかに動かす。
 * 位置が決まっているあいだは重力を切り、速度を距離に比例させて近づける。
 */
public final class SongFollow {

	/** 再生位置の真上、このブロック数の高さに浮かぶ */
	private static final double HOVER = 3.0;
	/** 1 tick あたり距離の何割ぶん進むか、と最高速度(ブロック/tick) */
	private static final double GAIN = 0.25;
	private static final double MAX_SPEED = 2.5;

	private static final Set<UUID> ON = new HashSet<>();
	private static final Set<UUID> FLOATING = new HashSet<>();

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
		for (UUID id : ON) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			Vec3 front = SongRegistry.front((ServerLevel) p.level(), p.position());
			if (front == null) {
				release(p);
				continue;
			}
			if (FLOATING.add(id)) {
				p.setNoGravity(true);
			}
			Vec3 delta = new Vec3(front.x, front.y + HOVER, front.z).subtract(p.position());
			double dist = delta.length();
			if (dist < 1.0E-3) {
				continue;
			}
			double speed = Math.min(dist * GAIN, MAX_SPEED);
			p.setDeltaMovement(delta.scale(speed / dist));
			p.fallDistance = 0;
			p.hurtMarked = true;
		}
	}
}
