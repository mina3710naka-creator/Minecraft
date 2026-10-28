package com.recmod;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.ListenerTransform;
import com.mojang.blaze3d.audio.SoundBuffer;

/**
 * ゲームの音を、録画のために自分で混ぜる。
 * ゲームの音は OpenAL がスピーカーへ直接出すので、途中を横取りできない。そこで、ゲームが Channel に出す命令
 * (音量・高さ・位置・バッファ)を写しておいて(Voice)、同じ音を、同じ位置・音量で混ぜ直す。
 */
public final class AudioMixer {

	static final int RATE = 44100;

	interface Sink {
		void audio(byte[] pcm, int length);
	}

	// ---- ゲームの音の写し(録画していないときも、いつも更新しておく。録画が音の途中から始まっても、続きを鳴らせるように) ----

	private static final Map<Channel, Voice> VOICES = new ConcurrentHashMap<>();
	/** 静的な音のデータ。SoundBuffer が使われなくなれば、一緒に消える。 */
	private static final Map<SoundBuffer, java.nio.ByteBuffer> BUFFERS = new WeakHashMap<>();
	private static volatile ListenerTransform listener;

	public static Voice voice(Channel channel) {
		return VOICES.computeIfAbsent(channel, c -> new Voice());
	}

	public static void forget(Channel channel) {
		VOICES.remove(channel);
	}

	public static void setListener(ListenerTransform transform) {
		listener = transform;
	}

	public static void rememberBuffer(SoundBuffer buffer, java.nio.ByteBuffer data) {
		synchronized (BUFFERS) {
			BUFFERS.put(buffer, data.duplicate().order(data.order()));
		}
	}

	static java.nio.ByteBuffer dataOf(SoundBuffer buffer) {
		synchronized (BUFFERS) {
			return BUFFERS.get(buffer);
		}
	}

	// ---- 録画中の音づくり ----

	private final long startNanos;
	private final Sink sink;
	private final int generation;
	private volatile boolean running = true;
	private Thread thread;

	private static int generations;

	AudioMixer(long startNanos, Sink sink) {
		this.startNanos = startNanos;
		this.sink = sink;
		this.generation = ++generations;
	}

	void start() {
		thread = new Thread(this::loop, "recmod-audio");
		thread.setDaemon(true);
		thread.start();
	}

	void stop() {
		running = false;
		try {
			if (thread != null) {
				thread.join(2000);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void loop() {
		long produced = 0;
		float[] accL = new float[RATE];
		float[] accR = new float[RATE];
		byte[] out = new byte[RATE * 4];
		while (running) {
			long target = (System.nanoTime() - startNanos) * RATE / 1_000_000_000L;
			while (produced < target && running) {
				int n = (int) Math.min(target - produced, 1024);
				java.util.Arrays.fill(accL, 0, n, 0F);
				java.util.Arrays.fill(accR, 0, n, 0F);
				long now = System.nanoTime();
				ListenerTransform l = listener;
				for (Voice v : VOICES.values()) {
					synchronized (v) {
						try {
							mixVoice(v, accL, accR, n, l, now);
						} catch (RuntimeException e) {
							v.ended = true;   // 1 つの音の失敗で、録画を止めない
						}
					}
				}
				for (int i = 0; i < n; i++) {
					int a = clip(accL[i]);
					int b = clip(accR[i]);
					out[i * 4] = (byte) a;
					out[i * 4 + 1] = (byte) (a >> 8);
					out[i * 4 + 2] = (byte) b;
					out[i * 4 + 3] = (byte) (b >> 8);
				}
				sink.audio(out, n * 4);
				produced += n;
			}
			try {
				Thread.sleep(8);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static int clip(float v) {
		int s = Math.round(v * 32767F);
		return Math.max(-32768, Math.min(32767, s));
	}

	private void mixVoice(Voice v, float[] accL, float[] accR, int n, ListenerTransform l, long now) {
		if (!v.playing || v.paused || v.ended) {
			return;
		}
		if (v.generation != generation) {
			// この録画で初めて見る音: 途中から鳴っているなら、鳴っているはずの位置にそろえる
			v.generation = generation;
			v.lastL = Float.NaN;
			if (!v.stream) {
				java.nio.ByteBuffer data = v.staticSource instanceof SoundBuffer sb ? dataOf(sb) : null;
				if (data == null) {
					v.ended = true;
					return;
				}
				v.current = Pcm.of(data, ((SoundBuffer) v.staticSource).format());
				double frame = v.playedSeconds(now) * v.current.rate * v.pitch;
				int frames = v.current.frames();
				if (frame >= frames && !v.looping) {
					v.ended = true;
					return;
				}
				v.pos = frames == 0 ? 0 : frame % frames;
			}
		}
		if (!v.stream && v.current == null) {
			return;
		}
		boolean stereo = (v.current != null ? v.current.channels : 1) == 2;
		if (v.stream && v.current == null) {
			v.current = v.queue.pollFirst();
			v.pos = 0;
			if (v.current == null) {
				return;
			}
			stereo = v.current.channels == 2;
		}
		float[] g = v.spatial(l, stereo);
		if (Float.isNaN(v.lastL)) {
			v.lastL = g[0];
			v.lastR = g[1];
		}
		float stepL = (g[0] - v.lastL) / n;
		float stepR = (g[1] - v.lastR) / n;
		float gl = v.lastL;
		float gr = v.lastR;
		for (int i = 0; i < n; i++) {
			Pcm p = v.current;
			if (p == null) {
				break;
			}
			int frames = p.frames();
			int i0 = (int) v.pos;
			if (i0 >= frames) {
				if (!advance(v)) {
					break;
				}
				p = v.current;
				frames = p.frames();
				i0 = (int) v.pos;
			}
			int i1 = i0 + 1 < frames ? i0 + 1 : i0;
			float f = (float) (v.pos - i0);
			float sl, sr;
			if (p.channels == 2) {
				sl = (p.data[i0 * 2] * (1 - f) + p.data[i1 * 2] * f) / 32768F;
				sr = (p.data[i0 * 2 + 1] * (1 - f) + p.data[i1 * 2 + 1] * f) / 32768F;
			} else {
				sl = (p.data[i0] * (1 - f) + p.data[i1] * f) / 32768F;
				sr = sl;
			}
			gl += stepL;
			gr += stepR;
			accL[i] += sl * gl;
			accR[i] += sr * gr;
			v.pos += p.rate / RATE * v.pitch;
		}
		v.lastL = g[0];
		v.lastR = g[1];
	}

	/** いまのデータの終わりに来た。ループなら先頭へ、ストリームなら次のデータへ。続きがなければ false。 */
	private static boolean advance(Voice v) {
		if (v.stream) {
			Pcm next = v.queue.pollFirst();
			if (next == null) {
				v.current = null;   // 続きが届くまで待つ(ゲームが読み込み中)
				return false;
			}
			v.pos -= v.current.frames();
			v.current = next;
			return true;
		}
		if (v.looping && v.current.frames() > 0) {
			v.pos -= v.current.frames();
			return true;
		}
		v.ended = true;
		v.current = null;
		return false;
	}
}
