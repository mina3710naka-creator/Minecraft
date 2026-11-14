package com.effectmod;

import java.util.List;

/** 付与台のボタンになる、よく使う効果の文章。ボタンを押すと文章欄に入るので、書き換えて自分好みにできる。 */
public final class Presets {

	/** {ボタンの名前, 文章} */
	public static final List<String[]> LIST = List.of(
			new String[] {"飛ぶ斬撃", "右クリックで斬撃を飛ばす 4"},
			new String[] {"攻撃で斬撃", "攻撃したとき斬撃 3"},
			new String[] {"落雷", "攻撃したとき雷を落とす 3"},
			new String[] {"爆発", "攻撃したとき爆発 2"},
			new String[] {"火球", "右クリックで火球を放つ 3"},
			new String[] {"衝撃波", "右クリックで衝撃波 3"},
			new String[] {"発火", "攻撃したとき相手を燃やす 3"},
			new String[] {"凍結", "攻撃したとき相手を凍らせる 3"},
			new String[] {"毒", "攻撃したとき相手に毒 6秒 2"},
			new String[] {"吹き飛ばし", "攻撃したとき相手を吹き飛ばす 3"},
			new String[] {"吸血", "攻撃したとき吸血 3"},
			new String[] {"ダッシュ", "右クリックでダッシュ 3"},
			new String[] {"大ジャンプ", "右クリックでジャンプ 3"},
			new String[] {"テレポート", "右クリックでテレポート 4"},
			new String[] {"氷の橋", "歩くと足元に氷ブロックを置く"},
			new String[] {"黒曜石の橋", "歩くと足元に黒曜石ブロックを置く"},
			new String[] {"石の壁", "右クリックで石の壁を作る 3"},
			new String[] {"クモの巣罠", "攻撃したとき相手の足元にクモの巣を置く"},
			new String[] {"反撃衝撃波", "ダメージを受けたとき衝撃波 2"},
			new String[] {"被弾で回復", "ダメージを受けたとき回復 2"},
			new String[] {"撃破で回復", "倒したとき回復 3"},
			new String[] {"採掘で爆発", "ブロックを壊したとき爆発 1"},
			new String[] {"速度アップ", "装備している間 速度 2"},
			new String[] {"暗視", "装備している間 暗視"},
			new String[] {"跳躍強化", "装備している間 跳躍強化 2"},
			new String[] {"再生", "装備している間 再生 1"});

	private Presets() {
	}
}
