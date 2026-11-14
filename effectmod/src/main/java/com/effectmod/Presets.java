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
			new String[] {"再生", "装備している間 再生 1"},
			// ---- どのアイテムにも
			new String[] {"隕石", "右クリックで隕石を落とす 4"},
			new String[] {"竜巻", "右クリックで竜巻 3"},
			new String[] {"引き寄せ", "右クリックで敵を引き寄せる 3"},
			new String[] {"連鎖雷", "攻撃したとき連鎖雷 3"},
			new String[] {"反撃", "ダメージを受けたとき反撃 3"},
			new String[] {"バリア", "右クリックでバリア 3"},
			new String[] {"無敵", "右クリックで無敵 3秒"},
			new String[] {"時間停止", "右クリックで時間停止 4秒"},
			new String[] {"経験値", "倒したとき経験値ボーナス 3"},
			new String[] {"ドロップ追加", "倒したとき財宝を落とす 3"},
			new String[] {"満腹回復", "右クリックで満腹回復 3"},
			new String[] {"浄化", "右クリックで浄化"},
			new String[] {"火炎放射", "右クリックで火炎放射 4"},
			new String[] {"吹雪", "右クリックで吹雪 4"},
			new String[] {"毒霧", "右クリックで毒霧 3"},
			new String[] {"癒しの光", "右クリックで癒しの光 3"},
			new String[] {"索敵", "右クリックで索敵 10秒"},
			new String[] {"背後に回る", "攻撃したとき背後テレポート 3"},
			new String[] {"スタン", "攻撃したとき30%の確率でスタン 3秒"},
			new String[] {"処刑", "攻撃したとき処刑"},
			new String[] {"自己修復", "装備している間 自己修復 2"},
			new String[] {"回収", "装備している間 回収 3"},
			new String[] {"追加ダメージ", "攻撃したとき追加ダメージ 3"},
			new String[] {"骨粉", "右クリックで成長 3"},
			// ---- 剣
			new String[] {"居合", "右クリックで居合 4"},
			new String[] {"旋風斬", "右クリックで旋風斬 4"},
			// ---- 斧
			new String[] {"地割れ", "右クリックで地割れ 4"},
			new String[] {"伐採", "掘ったとき伐採 3"},
			// ---- ツルハシ
			new String[] {"鉱脈採掘", "掘ったとき鉱脈採掘 3"},
			new String[] {"広範囲採掘", "掘ったとき広範囲採掘 2"},
			// ---- シャベル
			new String[] {"砂嵐", "右クリックで砂嵐 3"},
			new String[] {"落とし穴", "攻撃したとき落とし穴 3"},
			// ---- クワ
			new String[] {"豊作", "右クリックで豊作 3"},
			new String[] {"大鎌", "右クリックで大鎌 4"},
			// ---- 弓・クロスボウ
			new String[] {"連射", "右クリックで連射 4"},
			new String[] {"矢の雨", "右クリックで矢の雨 4"},
			new String[] {"散弾", "右クリックで散弾 4"},
			new String[] {"貫通弾", "右クリックで貫通弾 4"},
			// ---- トライデント
			new String[] {"雷神の槍", "右クリックで雷神の槍 4"},
			new String[] {"渦潮", "右クリックで渦潮 4"},
			// ---- メイス
			new String[] {"叩きつけ", "右クリックで叩きつけ 4"},
			new String[] {"重力井戸", "右クリックで重力井戸 4"},
			// ---- 盾
			new String[] {"反射", "ダメージを受けたとき反射 3"},
			new String[] {"聖域", "ダメージを受けたとき聖域 3"},
			// ---- 防具
			new String[] {"千里眼", "装備している間 千里眼"},
			new String[] {"見切り", "ダメージを受けたとき見切り 3"},
			new String[] {"不屈", "ダメージを受けたとき不屈 3"},
			new String[] {"棘の鎧", "ダメージを受けたとき棘 3"},
			new String[] {"隠密", "しゃがんだとき隠密 5秒"},
			new String[] {"韋駄天", "走っている間 韋駄天 3"},
			new String[] {"水面歩行", "歩いている間 水面歩行"},
			new String[] {"着地衝撃", "着地したとき着地衝撃 3"},
			// ---- エリトラ・釣り竿・ハサミ
			new String[] {"ターボ飛行", "装備している間 ターボ 3"},
			new String[] {"衝撃飛行", "衝撃飛行 3"},
			new String[] {"フック", "右クリックでフック 3"},
			new String[] {"大漁", "右クリックで大漁 3"},
			new String[] {"一斉刈り", "右クリックで一斉刈り 3"},
			new String[] {"剪定", "掘ったとき剪定 3"});

	/** 対象のアイテムに付けられる効果だけに絞る(アイテムが空なら、どれにも付けられる効果だけ)。 */
	public static List<String[]> forItem(net.minecraft.world.item.ItemStack st) {
		List<String[]> out = new java.util.ArrayList<>();
		for (String[] p : LIST) {
			Parser.Result r = Parser.parse(p[1], st);
			if (!r.ok()) {
				continue;
			}
			if (st.isEmpty() && !Parser.Action.valueOf(r.entry().action()).cats.isEmpty()) {
				continue;
			}
			out.add(p);
		}
		return out;
	}

	private Presets() {
	}
}
