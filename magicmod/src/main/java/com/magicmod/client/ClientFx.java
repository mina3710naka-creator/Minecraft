package com.magicmod.client;

import net.minecraft.util.RandomSource;

/** 画面の揺れ・光(サーバーからの合図で始まり、時間で弱まる)。 */
public final class ClientFx {

	private static float shake;
	private static int shakeLeft, shakeTotal = 1;
	private static int flashLeft, flashTotal = 1, flashColor;
	private static float flashAlpha;
	private static final RandomSource R = RandomSource.create();

	private ClientFx() {
	}

	static void start(String kind, float strength, int ticks, int color) {
		if (kind.equals("shake")) {
			if (strength * ticks >= shake * shakeLeft) {
				shake = Math.min(2.5f, strength);
				shakeLeft = shakeTotal = Math.max(1, ticks);
			}
		} else if (kind.equals("flash")) {
			flashColor = color;
			flashAlpha = Math.min(0.85f, strength);
			flashLeft = flashTotal = Math.max(1, ticks);
		}
	}

	static void tick() {
		if (shakeLeft > 0) {
			shakeLeft--;
		}
		if (flashLeft > 0) {
			flashLeft--;
		}
	}

	/** カメラに足す角度(度)。 */
	public static float[] shakeOffset() {
		if (shakeLeft <= 0) {
			return null;
		}
		float s = shake * shakeLeft / shakeTotal * 1.6f;
		return new float[] { (R.nextFloat() - 0.5f) * 2 * s, (R.nextFloat() - 0.5f) * 2 * s };
	}

	/** 画面全体に重ねる色(ARGB、無ければ 0)。 */
	static int flashArgb() {
		if (flashLeft <= 0) {
			return 0;
		}
		float a = flashAlpha * flashLeft / flashTotal;
		return ((int) (a * 255) << 24) | (flashColor & 0xFFFFFF);
	}
}
