# 最強のブーツ (OP Boots) — Minecraft データパック

装備するだけで**耐性Ⅴ常時（全ダメージ無効）・不可壊・跳躍力強化Ⅳ・移動速度アップ**になり、
**Shift+ジャンプで真上に10マスジャンプ**、**Shiftを3秒長押ししてからジャンプで爆発を伴う10マスジャンプ**が
発動する「最強のブーツ」を追加するデータパックです。すべて `function`（コマンド）で実装しています。
10マスジャンプは `tp` を使わず、レビテーション（浮遊効果）の上昇力だけで真上に打ち上げます。

対応バージョン: **Minecraft Java Edition 26.2**（データパック形式 **107** / `min_format` `max_format` 方式）

---

## 効果一覧

| 効果 | 内容 |
| --- | --- |
| 耐性Ⅴ（常時） | ダメージを100%軽減。さらに毎tick体力を監視し、万一減っていたら即座に全回復させることで、奈落や `/kill` のような耐性で防ぎきれない特殊なダメージも実質無効化 |
| 炎耐性（常時） | 炎・溶岩ダメージを無効化 |
| 跳躍力強化Ⅳ（常時） | 通常のジャンプが高くなる |
| 移動速度上昇（常時） | 歩行速度が上がる（初期値：速度Ⅱ相当） |
| 不可壊 | ブーツの耐久値が減らない |
| Shift + ジャンプ | その場から真上に約10マスジャンプ（`tp` は使わずレビテーションの上昇力のみ） |
| Shiftを3秒長押し + ジャンプ | 爆発の演出とともに周囲を吹き飛ばしつつ、同じ10マスジャンプを発動（本人はブーツの効果で無傷、ブロックは壊さない） |

---

## 導入方法

1. このリポジトリの `opboots` フォルダを、ワールドの `datapacks` フォルダにコピーします。

   ```
   .minecraft/saves/<ワールド名>/datapacks/opboots/
   ├── pack.mcmeta
   └── data/
   ```

2. ワールドに入って `/reload` を実行します。
   チャットに `[最強のブーツ] 読み込み完了` と表示されれば成功です。

3. アイテムを受け取ります。

   ```
   /function opboots:give
   ```

4. 受け取った「最強のブーツ」をブーツ枠に装備します。

   * **Shift（スニーク）を押しながらジャンプ** → 真上に10マスジャンプ
   * **Shiftを3秒押し続けてからジャンプ** → 爆発演出付きの10マスジャンプ

### アンインストール

```
/function opboots:uninstall
```
を実行してから、データパックのフォルダを削除してください。

---

## 調整できる数値

| 内容 | ファイル | 該当箇所 |
| --- | --- | --- |
| 耐性・跳躍強化・速度・炎耐性の強さ | `function/effects.mcfunction` | `effect give` 各行のamplifier（例：`minecraft:speed 1000000 1 true` の `1` が速度Ⅱ） |
| 溜め時間（3秒） | `function/jump/detect.mcfunction` | `ob.sneak matches 60..`（60tick=3秒） |
| ジャンプの高さ | `function/jump/leap.mcfunction` の `minecraft:levitation 4 4 true`（amplifier）と `function/jump/tick.mcfunction` の `ob.leapt matches 44..`（レビテーションをかける長さ＝tick数） | amplifierを上げる、または効果を切るまでのtick数を増やすほど高くジャンプする。現在の値（amplifier 4・44tick）でおおよそ10マス相当 |
| 爆発で吹き飛ばす範囲・威力 | `function/jump/explosive.mcfunction` | `damage @e[type=!player,distance=..4] 4 minecraft:explosion at ~ ~ ~` の `distance` と威力の値 |
| 装備判定に使うアイテム | `function/give.mcfunction` | ベースアイテム（`minecraft:netherite_boots`）と `minecraft:custom_data={opboots:1b}` |
| 対応バージョン範囲 | `pack.mcmeta` | `min_format` / `max_format`（26.2 = 107） |

---

## 実装メモ

* **装備判定** — `execute if items entity @s armor.feet *[minecraft:custom_data~{opboots:1b}]` で、
  ブーツ枠のアイテムに `custom_data` コンポーネント `{opboots:1b}` が付いているかを毎tick判定し、
  該当プレイヤーに `ob.wearing` タグを付ける。以降の効果・ジャンプ判定はこのタグを持つプレイヤーのみ処理する。
* **「全ダメージ無効」の実現方法** — 耐性Ⅴ（amplifier 4）はダメージを100%軽減できるが、
  奈落（`out_of_world`）や `/kill` など、一部のダメージ種別はポーション効果による軽減の対象外（bypass）になっている。
  これらも含めて確実に無効化するため、`scoreboard objectives add ob.hp health` で体力をそのまま反映するスコアを作り、
  毎tick「満タン（20）でなければ `minecraft:instant_health` で即全回復」という保険をかけている
  （`function/heal_check.mcfunction`）。耐性・炎耐性で防げるダメージは体力がそもそも減らないため発動せず、
  すり抜けたダメージだけをその場で打ち消す仕組み。
* **ジャンプ検知** — 統計 `minecraft.custom:minecraft.jump`（＝バニラの「ジャンプ」統計）を
  スコアボード条件でそのまま使い、値が増えた＝ジャンプした瞬間として検知したのち、
  毎tick末尾で `0` にリセットしている（フックショットの `hs.use` と同じ考え方）。
* **シフト判定** — プレイヤーのNBTは `/data` コマンドで読み書きできないため、
  `minecraft:entity_properties` 述語（`flags.is_sneaking`）でスニーク状態を判定している（`predicate/sneaking.json`）。
* **10マスジャンプの上昇方法（`tp` を使わない理由）** — `tp` でプレイヤーを直接動かす方式は、
  フックショットの README にもある通りクライアント側の予測処理とぶつかってカクついて見える上、
  当たり判定や向きの計算も必要になる。今回は真上に飛ばすだけでよいため、代わりに
  `minecraft:levitation`（レビテーション）を短時間だけ付与し、バニラの重力計算そのものに
  上昇させてもらう方式にした。`function/jump/leap.mcfunction` で
  `effect give @s minecraft:levitation 4 4 true`（amplifier 4 = レビテーションV）を付与し、
  `function/jump/tick.mcfunction` が毎tickカウントして **44tick（約2.2秒）経過したら
  `effect clear` で強制的に効果を切る**。レビテーションの上昇速度は時間とともに
  目標速度（amplifierに比例）へ滑らかに近づいていく仕組みなので、この44tickという長さは
  「合計でおよそ10マス分上昇する」ように逆算した値。効果を切った後は通常の重力に戻り、
  その場で自然に落下する（`tp` は一切使っていない）。
  * バニラの物理演算に任せているため、正確に10.0マスになるとは限らない（フックショットの
    打ち上げ演出と同様、目安の値）。もっと高く／低くしたい場合は上の表の通り amplitude か
    tick数を調整する。
* **爆発ジャンプ** — 実際にブロックを破壊する爆発は使わず、`damage @e[...] ... minecraft:explosion at ~ ~ ~`
  （フックショットの打ち上げと同じ「着弾を伴わない爆発扱いのダメージ」コマンド）で周囲のモンスターなどに
  ノックバックとダメージだけを与え、演出として `explosion_emitter` / `explosion` パーティクルと爆発音を鳴らしたのち、
  通常の10マスジャンプ処理をそのまま呼び出している。本人はブーツの耐性・保険により無傷。

---

## ファイル構成

```
opboots/
├── pack.mcmeta
└── data/
    ├── minecraft/tags/function/   … tick / load への登録
    └── opboots/
        ├── predicate/sneaking.json   … スニーク判定
        └── function/
            ├── load / tick                … 初期化・毎tick処理
            ├── effects / heal_check       … 常時効果・被ダメージ無効化の保険
            ├── sneak_track                … シフト保持時間の計測
            ├── give / uninstall
            └── jump/   … ジャンプ検知・通常/爆発ジャンプ・レビテーション管理
```
