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

	/** 効果音の候補(名前で探す。このバージョンにない名前は、飛ばす)。音域ごとに、使う音を変える。 */
	private static final SoundEvent[] LOW = voices("VILLAGER_AMBIENT", "VILLAGER_YES", "SHEEP_AMBIENT", "PIG_AMBIENT");
	private static final SoundEvent[] MID = voices("EXPERIENCE_ORB_PICKUP", "ITEM_PICKUP", "PLAYER_LEVELUP", "VILLAGER_CELEBRATE");
	private static final SoundEvent[] HIGH = voices("AMETHYST_BLOCK_CHIME", "AMETHYST_CLUSTER_BREAK", "ENDER_EYE_DEATH");
	private static final SoundEvent[] ACCENT = voices("BELL_BLOCK", "ARROW_HIT_PLAYER", "ANVIL_LAND");

	private static SoundEvent[] voices(String... names) {
		java.util.List<SoundEvent> list = new java.util.ArrayList<>();
		for (String n : names) {
			try {
				list.add(ev(SoundEvents.class.getField(n).get(null)));
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				// この名前は、このバージョンにない
			}
		}
		return list.toArray(new SoundEvent[0]);
	}

	/** 音の高さ(元の音域)と強さで、効果音を選ぶ: 低い = 声、中くらい = 経験値の音など、高い = アメジスト、強い音 = ベルなどで目立たせる。 */
	private static SoundEvent effectVoice(int note, int velocity) {
		SoundEvent[][] pool = velocity > 115 && ACCENT.length > 0 ? new SoundEvent[][] { ACCENT }
				: note < 62 ? new SoundEvent[][] { LOW, MID } : note < 74 ? new SoundEvent[][] { MID, HIGH } : new SoundEvent[][] { HIGH, MID };
		for (SoundEvent[] group : pool) {
			if (group.length > 0) {
				return group[(note / 2 + velocity / 16) % group.length];
			}
		}
		return SoundEvents.AMETHYST_BLOCK_CHIME;
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
			event = effectVoice(note, velocity);
			p = scalePitch(note);
		}
		level.playLocalSound(x, y, z, event, SoundSource.RECORDS, Math.min(2.0f, vol), p, false);
	}
}
