package com.lampvideo.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 解析で取り出した音(NoteAnalyzer のイベント)を、ゲーム内の音で鳴らす。
 * 音符ブロックの音(NOTE_BLOCK_*)は、1オクターブ=ピッチ 2 倍で、2オクターブ(ピッチ 0.5〜2.0)の範囲。
 * 効果音の方式(音符ブロックを使わない)は、鳴き声・経験値・アメジストの音などを、ピッチを変えて、音階にする。
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
			switch (kind) {
				case NoteAnalyzer.KICK -> {
					event = SoundEvents.STONE_PLACE;
					p = 0.5f;
					vol *= 1.2f;
				}
				case NoteAnalyzer.SNARE -> {
					event = SoundEvents.WOOD_HIT;
					p = 1.3f;
				}
				case NoteAnalyzer.HAT -> {
					event = ev(SoundEvents.UI_BUTTON_CLICK);
					p = 2.0f;
					vol *= 0.7f;
				}
				default -> {
					if (note < 48) {
						event = SoundEvents.VILLAGER_AMBIENT;
						p = pitch(note, 41);
					} else if (note < 66) {
						event = SoundEvents.VILLAGER_AMBIENT;
						p = pitch(note, 58);
					} else if (note < 84) {
						event = SoundEvents.EXPERIENCE_ORB_PICKUP;
						p = pitch(note, 76);
					} else {
						event = SoundEvents.AMETHYST_BLOCK_CHIME;
						p = pitch(note, 90);
					}
				}
			}
		}
		level.playLocalSound(x, y, z, event, SoundSource.RECORDS, Math.min(2.0f, vol), p, false);
	}
}
