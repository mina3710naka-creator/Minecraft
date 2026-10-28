package com.recmod;

import java.util.ArrayDeque;

import com.mojang.blaze3d.audio.ListenerTransform;

/**
 * ゲームの音の出口(Channel)1 つぶんの状態。ゲームが Channel に命令するたびに、ここにも写しておく。
 * 録画中は、AudioMixer が、この状態から音を作る。
 * 命令は音のスレッドから、音づくりは別のスレッドから来るので、触るときは synchronized(voice) する。
 */
public final class Voice {
	// ゲームが決めた値
	public float gain = 1.0F;
	public float pitch = 1.0F;
	public boolean looping;
	public boolean relative;
	public boolean attenuated = true;
	public float maxDistance = 16.0F;
	public double x, y, z;
	public boolean playing;
	public boolean paused;

	/** 鳴らしはじめた時刻と、止まっていた時間(録画の途中から鳴っている音の、位置を割り出すため)。 */
	public long playStartNanos;
	public long pausedNanos;
	public long pauseStartNanos;

	/** 静的な音(1 回で全部読み込んだもの)。 */
	public Object staticSource;       // SoundBuffer
	/** ストリームの音の、まだ鳴らしていない分。 */
	public final ArrayDeque<Pcm> queue = new ArrayDeque<>();
	public boolean stream;

	// 音づくり側の状態
	public int generation = -1;
	public Pcm current;
	public double pos;
	public float lastL, lastR;
	public boolean ended;

	public void reset() {
		playing = false;
		paused = false;
		staticSource = null;
		queue.clear();
		stream = false;
		current = null;
		ended = false;
		generation = -1;
		pos = 0;
	}

	public void play(long now) {
		playing = true;
		paused = false;
		pausedNanos = 0;
		playStartNanos = now;
		ended = false;
		generation = -1;
		current = null;
		pos = 0;
	}

	public void pause(long now) {
		if (playing && !paused) {
			paused = true;
			pauseStartNanos = now;
		}
	}

	public void unpause(long now) {
		if (playing && paused) {
			paused = false;
			pausedNanos += now - pauseStartNanos;
		}
	}

	public void enqueue(Pcm pcm) {
		queue.addLast(pcm);
		while (queue.size() > 6) {
			queue.removeFirst();
		}
	}

	/** 録画の始まりから見て、いま何秒目を鳴らしているはずか。 */
	public double playedSeconds(long now) {
		long paused = pausedNanos + (this.paused ? now - pauseStartNanos : 0);
		return Math.max(0, now - playStartNanos - paused) / 1.0e9;
	}

	/** 左右の音量(距離による小さくなり方と、左右の振り分け)。 */
	public float[] spatial(ListenerTransform listener, boolean stereoSource) {
		double dx, dy, dz;
		double right;
		if (relative || listener == null) {
			dx = x;
			dy = y;
			dz = z;
			right = dx;
		} else {
			dx = x - listener.position().x;
			dy = y - listener.position().y;
			dz = z - listener.position().z;
			net.minecraft.world.phys.Vec3 r = listener.right();
			right = dx * r.x + dy * r.y + dz * r.z;
		}
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		float att = 1.0F;
		if (attenuated) {
			att = (float) Math.max(0.0, Math.min(1.0, 1.0 - dist / Math.max(maxDistance, 0.001F)));
		}
		float g = gain * att;
		if (stereoSource || dist < 0.001) {
			return new float[] { g, g };
		}
		double pan = Math.max(-1.0, Math.min(1.0, right / dist));
		double angle = (pan + 1.0) * Math.PI / 4.0;
		// 真ん中で、左右が 0.8 ずつになるようにそろえる(2 つの音量の 2 乗の和が、位置によらず一定)
		return new float[] { (float) (g * Math.cos(angle) * Math.sqrt(2) * 0.8), (float) (g * Math.sin(angle) * Math.sqrt(2) * 0.8) };
	}
}
