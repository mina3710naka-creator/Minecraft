# 最強のブーツ (OP Boots) — Minecraft データパック

装備するだけで**耐性Ⅴ常時（全ダメージ無効）・不可壊・跳躍力強化Ⅳ・移動速度アップ**になり、
**Shift+ジャンプで前方に10マスジャンプ**、**Shiftを3秒長押ししてからジャンプで爆発を伴う10マスジャンプ**が
発動する「最強のブーツ」を追加するデータパックです。すべて `function`（コマンド）で実装しています。

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
| Shift + ジャンプ | 見ている水平方向へ前方10マスジャンプ（山なりの軌道、着地位置は開始と同じ高さ） |
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

   * **Shift（スニーク）を押しながらジャンプ** → 前方に10マスジャンプ
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
| ジャンプの飛距離・軌道 | `function/jump/step.mcfunction` | 各tickの `^ ^dy ^1.0` の `dy`（山なりの高さ）・`1.0`（前方距離）を10行分調整。現在は前方1.0×10tick=合計10マス |
| ジャンプにかかる時間 | `function/jump/step.mcfunction` | 分岐（`ob.leapt matches 0`〜`9`）の数を増減（1行=1tick） |
| 爆発で吹き飛ばす範囲・威力 | `function/jump/explosive.mcfunction` | `damage @e[type=!player,distance=..4] 4 minecraft:explosion at ~ ~ ~` の `distance` と威力の値 |
| 装備判定に使うアイテム | `function/give.mcfunction` | ベースアイテム（`minecraft:netherite_boots`）と `minecraft:custom_data={opboots:1b}` |
| 壁に当たったときの着地判定 | `data/opboots/tags/block/passable.json` | ジャンプ中に通り抜けられるブロック（これ以外に当たると着地扱いになる） |
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
* **10マスジャンプの軌道** — プレイヤーを直接 `tp` で毎tick動かす方式（フックショットの引き寄せのように
  見えない台車に乗せる方式ではない）。1tickにつき前方1.0マス・上下は山なりに増減する値を
  `execute rotated ~ 0 run tp @s ^ ^dy ^1.0` で加算していく。`rotated ~ 0` でピッチ（上下の視点角度）を
  一時的に0に固定した上でキャレット座標を計算しているため、上や下を見ながらジャンプしても
  必ず水平方向へまっすぐ10マス進み、かつ `tp` の3引数キャレット座標指定はプレイヤー自身の
  向き（見ている方向）を変更しないため、視点はそのまま保たれる。
  * 移動前に着地点となるブロックが `#opboots:passable`（フックショットと同様の「通り抜け可能」タグ）に
    含まれるか毎tickチェックし、壁など通り抜けられないブロックに当たったら即座に着地処理へ切り替える。
  * 短時間（1tickずつ・合計10tick=0.5秒）で `tp` するため、フックショットの引き寄せ処理のように
    複数秒間かけて動かす場合ほどの違和感は出ない想定だが、詳しくは `hookshot` パックの README の
    実装メモ（プレイヤーを直接tpすることの注意点）も参照。
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
        ├── tags/block/passable.json  … ジャンプ中に通り抜けられるブロック
        └── function/
            ├── load / tick                … 初期化・毎tick処理
            ├── effects / heal_check       … 常時効果・被ダメージ無効化の保険
            ├── sneak_track                … シフト保持時間の計測
            ├── give / uninstall
            └── jump/   … ジャンプ検知・通常/爆発ジャンプ・弧の移動・着地
```
