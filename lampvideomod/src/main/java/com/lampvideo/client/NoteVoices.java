package com.lampvideo.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 解析で取り出した音(NoteAnalyzer のイベント)を、ゲーム内の音で鳴らす。
 * 音符ブロックの音(NOTE_BLOCK_*)は、1オクターブ=ピッチ 2 倍で、2オクターブ(ピッチ 0.5〜2.0)の範囲。
 * 効果音の方式(音符ブロックを使わない)は、アメジストの音 1 種類を、ドレミファソラシドの決まった高さで鳴らす。
 * 鳴らす位置は、スピーカーの位置(距離と左右は、ゲームの音の仕組みがそのまま扱う)。
 */
public final class NoteVoices {

	public static final int MODE_EFFECT = 1;
	public static final int MODE_NOTEBLOCK = 2;

	private NoteVoices() {
	}

	private static SoundEvent ev(Object o) {
		return o instanceof Holder<?> h ? (SoundEvent) h.value() : (SoundEvent) o;
	}

	/** ノート番号を、ピッチ(2^((n-基準)/12))にする。範囲は、ゲームの限度 0.5〜2.0 の中に収める。 */
	private static float pitch(int note, int base) {
		int n = note;
		while (n < base - 12) {
			n += 12;
		}
		while (n > base + 12) {
			n -= 12;
		}
		return (float) Math.max(0.5, Math.min(2.0, Math.pow(2.0, (n - base) / 12.0)));
	}

	/** ドレミファソラシドの、半音の番号(ド=0 レ=2 ミ=4 ファ=5 ソ=7 ラ=9 シ=11)。黒鍵は、すぐ下の白鍵にする。 */
	private static final int[] DEGREE = { 0, 0, 2, 2, 4, 5, 5, 7, 7, 9, 9, 11 };

	/** ノート番号を C4〜C6 の中に折りたたんで、ドレミの音(白鍵)にして、ピッチにする。ド(C5)= 1.0、C4 = 0.5、C6 = 2.0。 */
	static float scalePitch(int note) {
		int n = note;
		while (n < 60) {
			n += 12;
		}
		while (n >= 84) {
			n -= 12;
		}
		int snapped = n - n % 12 + DEGREE[n % 12];
		return (float) Math.pow(2.0, (snapped - 72) / 12.0);
	}

	/** 1つのイベントを、位置 (x,y,z) で鳴らす。volume は、スピーカーの音量(0〜1)。 */
	public static void play(ClientLevel level, double x, double y, double z, int mode, byte kind, int note, int velocity, float volume) {
		float v = volume * (0.45f + 0.55f * velocity / 127f);
		if (v <= 0.01f) {
			return;
		}
		SoundEvent event;
		float p;
		float vol = v;
		if (mode == MODE_NOTEBLOCK) {
			// ピアノ(音符ブロックのハープ)のみ。ドラム類は鳴らさず、音程だけを取る
			if (kind == NoteAnalyzer.KICK || kind == NoteAnalyzer.SNARE || kind == NoteAnalyzer.HAT) {
				return;
			}
			event = ev(SoundEvents.NOTE_BLOCK_HARP);
			p = pitch(note, 66);
		} else {
			// 効果音: 1 種類の音を、ドレミファソラシド(C メジャー)の決まった高さだけで鳴らす
			if (kind != NoteAnalyzer.PITCHED) {
				return;
			}
			event = SoundEvents.AMETHYST_BLOCK_CHIME;
			p = scalePitch(note);
		}
		level.playLocalSound(x, y, z, event, SoundSource.RECORDS, Math.min(2.0f, vol), p, false);
	}
}
