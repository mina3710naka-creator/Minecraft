# Minecraft データパック集

このリポジトリには、すべて `function`（コマンド）だけで実装した
Minecraft Java Edition 用データパックが入っています。

対応バージョン: **Minecraft Java Edition 26.2**（データパック形式 **107** / `min_format` `max_format` 方式）

| データパック | 内容 |
| --- | --- |
| [`hookshot/`](#フックショット-hookshot) | 釣り竿でワイヤーアクション |
| [`opboots/`](#opブーツ-op-boots) | 耐性V・全ダメージ無効・高速移動・爆発ジャンプの最強ブーツ |

---

# フックショット (Hookshot)

釣り竿を右クリックすると、視点方向へ**透明な防具立て（フック）**が飛んでいき、
壁や床に当たるとそこに**マーカーが固定**され、プレイヤーが**滑らかに引き寄せられる**
ワイヤーアクション用データパックです。

---

## 動作の流れ

| 順番 | 処理 |
| --- | --- |
| 1 | 釣り竿を使うと、まず**自分が出した釣り針（fishing_bobber）をキル**する |
| 2 | **透明な防具立て**（フック）を視点方向へまっすぐ射出（1 tick に 2 ブロック / 最大 32 ブロック） |
| 3 | 防具立てとプレイヤーの間に**リード**を張り、**キラキラ（end_rod / glow）のロープ**を描画 |
| 4 | 壁や床に着弾すると、その位置に**マーカーが固定**される |
| 5 | マーカーへ向かって、速度を少しずつ上げながら（イーズイン）**カクつかずに滑らかに移動** |
| 6 | **マーカーに到達**するか、**3 秒（60 ティック）引き寄せられた**ら自動で解除 |

* 引き寄せ中にもう一度釣り竿を使うと、その場で解除できます（トグル）。
* 32 ブロック飛んでも何にも当たらなかった場合は自動で解除されます。

---

## 導入方法

1. このリポジトリの `hookshot` フォルダを、ワールドの `datapacks` フォルダにコピーします。

   ```
   .minecraft/saves/<ワールド名>/datapacks/hookshot/
   ├── pack.mcmeta
   └── data/
   ```

   （サーバーの場合は `<サーバーフォルダ>/world/datapacks/hookshot/`）

2. ワールドに入って `/reload` を実行します。
   チャットに `[フックショット] 読み込み完了` と表示されれば成功です。

3. アイテムを受け取ります。

   ```
   /function hookshot:give
   ```

4. 受け取った釣り竿「フックショット」を持って右クリック。

> 通常の釣り竿はそのまま釣りに使えます。フックショットが発動するのは
> `custom_data` に `hookshot` が付いた専用の釣り竿を持っているときだけです。

### 旧バージョン（1.21.x）で使いたい場合

`pack.mcmeta` は 26.2 向けに `min_format` / `max_format` を使っています。
1.21.9 より前では読み込めないので、次の 3 点を戻してください。

1. `pack.mcmeta` を `"pack_format": 48, "supported_formats": {"min_inclusive": 48, "max_inclusive": 88}` に戻す
   （`min_format` / `max_format` は 1.21.9 以降専用、`supported_formats` は 1.21.9 以降では使えません）
2. リードのサウンドを旧 ID に戻す
   （`pull/start.mcfunction` の `item.lead.tied` → `entity.leash_knot.place`、
   `release.mcfunction` の `item.lead.untied` → `entity.leash_knot.break`）
3. アイテム配布は `/function hookshot:give_old`（1.21.4 以前の JSON 文字列表記）を使う

### アンインストール

```
/function hookshot:uninstall
```
を実行してから、データパックのフォルダを削除してください。

---

## 調整できる数値

| 内容 | ファイル | 該当箇所 |
| --- | --- | --- |
| フックの速さ | `hook/tick.mcfunction` | `scoreboard players set @s hs.sub 8`（1 tick のステップ数 × 0.25 ブロック） |
| フックの射程 | `hook/step.mcfunction` | `hs.range matches 128..`（128 × 0.25 = 32 ブロック） |
| 引き寄せの初速 | `pull/start.mcfunction` | `hs.spd 25`（= 0.25 ブロック / tick） |
| 加速度・最高速度 | `pull/tick.mcfunction` | `add @s hs.spd 8` / `matches 135..`（= 1.35 ブロック / tick） |
| 引き寄せの制限時間 | `pull/tick.mcfunction` | `hs.pt matches 60..`（60 tick = 3 秒） |
| 到達とみなす距離 | `pull/move.mcfunction` | `distance=..1.4` |
| 通り抜けるブロック | `data/hookshot/tags/block/passable.json` | 草・水・松明などフックが貫通するブロック |
| 落下ダメージ無効化 | `release.mcfunction` | 最終行の `effect give ... slow_falling` のコメントを外す |
| 対応バージョン範囲 | `pack.mcmeta` | `min_format` / `max_format`（26.2 = 107） |

---

## 実装メモ

* **滑らかな移動について** — Java 版のバニラでは、プレイヤーの `Motion`（速度）NBT を
  コマンドで書き換えることはできません（`/data` はプレイヤーの移動データを変更できない）。
  そのため本パックは速度ベクトルをスコア `hs.spd`（1/100 スケール）として保持し、
  毎ティック `execute ... facing entity <マーカー> feet positioned ^ ^ ^<速度> run tp @s ~ ~ ~`
  で位置だけを更新する方式で「motion 相当」を再現しています。
  * 回転を書き換えないので視点がブレません。
  * 速度を毎ティック少しずつ加算（イーズイン）するため、動き出しがカクつきません。
  * `/tp` は毎ティック落下距離をリセットするので、引き寄せ中に落下ダメージは入りません。
* **リードの NBT** — エンティティに繋ぐ場合の書式は `leash:{UUID:[I;...]}` です
  （フェンスに繋ぐ場合は `leash:[I;x,y,z]`）。そのため
  `data modify entity @s leash.UUID set from entity <プレイヤー> UUID` と、
  `leash` 直下ではなく `leash.UUID` に書き込んでいます。
* **リードについて** — バニラのリードは約 10 ブロックで切れてしまうため、
  リードを結ぶ見えないコウモリ（`NoAI` / 無敵 / 透明）はプレイヤーとフックを結ぶ線上の
  最大 8 ブロック地点に留まるようにしています。そこから先はパーティクルのロープが繋がります。
* **エンティティの後始末** — フック・マーカー・ロープには共通タグ `hs.ent` が付いており、
  12 秒経過したものや `/reload` 時に残っていたものは自動で削除されます。
* リード用のコウモリは `/kill` すると死亡演出とリードのドロップが発生するため、
  解除時は奈落へ飛ばして静かに消滅させています（念のためドロップしたリードも掃除します）。
* 使用検知には統計 `minecraft.used:minecraft.fishing_rod` を使っており、毎ティック 0 にリセットします。
  そのため「釣り竿を使った回数」の統計は増えなくなります。
* 26.x でリード関連のサウンド ID が変更されました
  （`entity.leash_knot.place` / `.break` → `item.lead.tied` / `item.lead.untied` / `item.lead.break`）。
* 1.20.5 より前のバージョンではリードの NBT が `Leash:{UUID:[I;...]}`（大文字始まり）のため、
  `rope/init.mcfunction` と `rope/follow.mcfunction` の書き換えが必要です。

---

## ファイル構成

```
hookshot/
├── pack.mcmeta
└── data/
    ├── minecraft/tags/function/   … tick / load への登録
    └── hookshot/
        ├── tags/block/passable.json  … フックが貫通するブロック
        └── function/
            ├── load / tick               … 初期化・毎ティック処理
            ├── use / fire / release      … 使用検知・発射・解除
            ├── give / give_old / uninstall
            ├── hook/   … フック（防具立て）の飛行・着弾・マーカー設置
            ├── pull/   … 引き寄せ（滑らかな移動）
            ├── rope/   … リードとキラキラのロープ
            └── util/   … 後始末・安全装置
```

---

# OPブーツ (OP Boots)

装備すると**耐性V相当の全ダメージ無効・移動速度アップ・ジャンプ力アップ**が常時発動し、
**しゃがみを3秒間長押しすると爆発しながら前方へ約10ブロック飛ぶ**、最強のブーツです。

## 機能

| 機能 | 内容 |
| --- | --- |
| 全ダメージ無効 | 耐性V（常時・無限）＋火炎耐性に加え、毎ティック体力を最大値へ戻すことで**あらゆるダメージを実質無効化** |
| 移動速度アップ | `attribute_modifiers` で移動速度 **+60%**（装備しているだけで常時有効） |
| ジャンプ力アップ | `attribute_modifiers` でジャンプ力を強化し、通常のジャンプで**約3ブロック**上昇 |
| 爆発ジャンプ | しゃがみを**3秒（60 tick）長押し**すると、爆発演出（見た目・音のみ／ブロックや周囲へのダメージなし）とともに、向いている方向へ放物線を描きながら**約10ブロック**ジャンプする |
| 不可壊 | `unbreakable` コンポーネントによりアイテムの耐久値が減らない |

## 導入方法

1. このリポジトリの `opboots` フォルダを、ワールドの `datapacks` フォルダにコピーします。

   ```
   .minecraft/saves/<ワールド名>/datapacks/opboots/
   ├── pack.mcmeta
   └── data/
   ```

2. ワールドに入って `/reload` を実行します。
   チャットに `[OPブーツ] 読み込み完了` と表示されれば成功です。

3. アイテムを受け取ります。

   ```
   /function opboots:give
   ```

4. ブーツを装備すると自動的に効果が発動します。装備中に**しゃがみを3秒間押し続ける**と爆発ジャンプが発動します。

### アンインストール

```
/function opboots:uninstall
```
を実行してから、データパックのフォルダを削除してください。

---

## 調整できる数値

| 内容 | ファイル | 該当箇所 |
| --- | --- | --- |
| 移動速度の上昇率 | `give.mcfunction` | `attribute_modifiers` の `movement_speed` の `amount:0.6`（+60%） |
| ジャンプ力の上昇量 | `give.mcfunction` | `attribute_modifiers` の `jump_strength` の `amount:0.23`（デフォルト 0.42 に加算） |
| 爆発ジャンプの溜め時間 | `jump/charge.mcfunction` | `ob.charge matches 60..`（60 tick = 3 秒） |
| 爆発ジャンプの初速・重力 | `jump/start.mcfunction` / `jump/tick.mcfunction` | `ob.vv 80`（初速）／ `scoreboard players remove @s ob.vv 8`（重力） |
| 爆発ジャンプの水平速度 | `jump/move.mcfunction` | `positioned ^ ^ ^0.5`（1 tick あたりの前進量） |
| 爆発ジャンプの制限時間 | `jump/tick.mcfunction` | `ob.jt matches 20..`（20 tick = 1 秒） |

## 実装メモ

* **全ダメージ無効について** — 耐性V（アンプリファイア4）は通常ダメージを理論上100%軽減しますが、
  `/kill` や虚空落下などの「真ダメージ」は軽減できません。本パックはこれを補うため、
  装備中は毎ティック `attribute minecraft:max_health get` の結果を `Health` に書き戻し、
  体力を強制的に満タンへ戻すことでほぼ全てのダメージ経路を無効化しています。
* **爆発ジャンプの移動について** — プレイヤーの `Motion` を直接書き換えることはできないため、
  hookshot と同様に発射方向を保持する `marker` エンティティを立てて `positioned ^ ^ ^`（水平前進）と
  `~ ~<垂直速度> ~`（放物線）を毎ティック合成し、`tp` で位置だけを更新する方式を採用しています。
  垂直速度は 0.80 ブロック/tick から毎ティック 0.08 ずつ重力で減衰し、20 tick（1秒）で自然に着地します。
* **爆発演出について** — `minecraft:explosion_emitter` パーティクルと爆発音のみを再生しており、
  ブロック破壊や周囲エンティティへのダメージは一切発生しません（見た目だけの演出）。
* 複数プレイヤーが同時に爆発ジャンプしても混線しないよう、hookshot の `hs.id` と同様に
  `ob.id` でプレイヤーとマーカーを一対一に紐付けています。

## ファイル構成

```
opboots/
├── pack.mcmeta
└── data/
    ├── minecraft/tags/function/   … tick / load への登録
    └── opboots/
        └── function/
            ├── load / tick            … 初期化・毎ティック処理
            ├── give / uninstall
            ├── equip/   … 装備・解除の検知（耐性V・火炎耐性の付与/解除）
            ├── jump/    … しゃがみ長押し検知・爆発ジャンプの放物線移動
            └── util/    … 全ダメージ無効化（体力の強制回復）
```
