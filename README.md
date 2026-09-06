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
| 5 | 見えない台車（コウモリ）にプレイヤーを **ride で乗せ**、台車の方だけを
    **フックと同じ tp ステップ方式で**マーカーへ引き寄せる |
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
| 台車（引き寄せ）の初速 | `pull/carrier_init.mcfunction` | `hs.spd 20`（= 0.2 ブロック / tick） |
| 加速度・最高速度 | `pull/tick.mcfunction` | `add @s hs.spd 6` / `matches 90..`（= 0.9 ブロック / tick） |
| 減速ゾーン | `pull/move.mcfunction` | `distance=1.3..3` → 0.3、`distance=3..5` → 0.6 ブロック / tick に制限 |
| 引き寄せの制限時間 | `pull/tick.mcfunction` | `hs.pt matches 60..`（60 tick = 3 秒） |
| 到達とみなす距離 | `pull/move.mcfunction` / `pull/step.mcfunction` | `distance=..1.3` |
| 通り抜けるブロック | `data/hookshot/tags/block/passable.json` | 草・水・松明などフックが貫通するブロック |
| 落下ダメージ無効化（解除後） | `release.mcfunction` | 最終行の `effect give ... slow_falling` のコメントを外す |
| 対応バージョン範囲 | `pack.mcmeta` | `min_format` / `max_format`（26.2 = 107） |

---

## 実装メモ

* **プレイヤーを直接 tp せず、見えない台車に乗せて台車を tp する** —
  バニラのコマンドでは、実在する**プレイヤー**の `Motion`（速度）NBT を
  書き換えることはできません。かといって、プレイヤー自身を毎ティック
  直接 `tp` すると、クライアント側の予測処理とサーバーからの位置修正が
  ぶつかり合ってカクついて見えてしまいます。
  そこで本パックは、見えない**台車（コウモリ）**を着弾と同時に出現させ、
  `/ride` コマンドでプレイヤーをその台車に乗せます。**動かすのは台車の
  方だけ**（フックの飛行と同じ `tp` ステップ方式）で、プレイヤーは
  ただ乗っているだけです。乗り物に乗っている間の位置追従はゲーム側の
  仕組みで滑らかに補間されるため、プレイヤー自身を直接 tp するより
  カクつきにくくなります（乗っている間、WASD 等の自力移動は効きません。
  視点操作はプレイヤー自身が自由に行えます）。
  * 台車には防具立てではなくコウモリ（`NoAI` で自力飛行だけ止めたモブ）を
    使っています。台車自体は見えないので、`tp` で座標・向きを書き換えても
    見た目に違和感はありません。実体としての当たり判定も持っているので、
    仮に手動の判定をすり抜けても壁の中に入り込みにくい、という保険にも
    なっています。
  * 台車の 1 tick 分の移動は 4 分割し、フックの飛行（`hook/step.mcfunction`）
    と全く同じ考え方で、**0.25 ブロックずつ着弾判定をしてから進みます**
    （`pull/move.mcfunction` / `pull/step.mcfunction`）。壁や床にぶつかれば
    その場で切り離されるので、めり込みは起こりません。
  * 速度を毎ティック少しずつ加算（イーズイン）し、着弾点に近づくと段階的に
    減速する（イーズアウト）ため、動き出しも止まる瞬間も急になりません。
  * 解除時は `ride @s dismount` でプレイヤーの操作を返してから、台車を
    死亡演出（音・パーティクル）なしで消します（`release.mcfunction` /
    `util/cleanup.mcfunction` / `util/quiet_kill.mcfunction`）。
  * **既知の制約（バニラの仕様上、コマンドでは解消できません）** —
    生物（モブ・防具立て）に `/ride` で乗っている間は、バニラの仕様として
    ①満腹度バーの位置に**台車の HP バー**が表示される、②プレイヤーが
    **座りポーズ**になる、という 2 点が必ず起こります。台車は無敵
    （`Invulnerable:1b`）で常に満タン表示のままなので実害はありません。
  * ちなみに、当初は台車の `Motion`（速度）を直接書き換えて動かす方式を
    試しましたが、**乗られている（ride されている）モブは `Motion` を
    書き換えても実際には動かない**ことが分かったため、この tp ベースの
    方式に変更しています。
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

装備すると**耐性V相当の全ダメージ無効・移動速度+120%・ジャンプ力アップ**が常時発動し、
**しゃがみ中にジャンプで前方10ブロックジャンプ、3秒長押しすると爆発しながら前方へ約10ブロック飛ぶ**、
さらに**空中でもう一度ジャンプすると二段ジャンプ**もできる、最強のブーツです。

## 機能

| 機能 | 内容 |
| --- | --- |
| 全ダメージ無効 | 耐性V（常時・無限）＋火炎耐性に加え、毎ティック体力を最大値へ戻すことで**あらゆるダメージを実質無効化** |
| 移動速度アップ | `attribute_modifiers` で移動速度 **+120%**（装備しているだけで常時有効） |
| ジャンプ力アップ | `attribute_modifiers` でジャンプ力を強化し、通常のジャンプで**約3ブロック**上昇 |
| 二段ジャンプ | 空中でもう一度ジャンプ入力すると、着地するまで**1回だけ追加ジャンプ**できる |
| 通常ジャンプ（しゃがみ中） | しゃがみ中に**3秒たまる前にジャンプ**すると、爆発なしで前方へ約10ブロックジャンプする |
| 爆発ジャンプ | しゃがみを**3秒（60 tick）たまるまで長押し**すると、自動で爆発演出（見た目・音のみ／ブロックや周囲へのダメージなし）とともに前方へ約10ブロックジャンプする |
| 氷結・水中高速移動 | Frost Walker Ⅱ / Depth Strider Ⅲ / Soul Speed Ⅲ を付与済み |
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

4. ブーツを装備すると自動的に効果が発動します。
   * しゃがみながらジャンプ … 爆発なしで前方へ約10ブロックジャンプ
   * しゃがみを3秒間押し続ける … 自動で爆発ジャンプが発動
   * 空中でもう一度ジャンプ … 二段ジャンプ（着地するまで1回）

### アンインストール

```
/function opboots:uninstall
```
を実行してから、データパックのフォルダを削除してください。

---

## 調整できる数値

| 内容 | ファイル | 該当箇所 |
| --- | --- | --- |
| 移動速度の上昇率 | `give.mcfunction` | `attribute_modifiers` の `movement_speed` の `amount:1.2`（+120%） |
| ジャンプ力の上昇量 | `give.mcfunction` | `attribute_modifiers` の `jump_strength` の `amount:0.4`（デフォルト 0.42 に加算） |
| 爆発ジャンプの溜め時間 | `jump/charge.mcfunction` | `ob.charge matches 60..`（60 tick = 3 秒） |
| ジャンプの初速・重力 | `jump/start.mcfunction` / `jump/start_plain.mcfunction` / `jump/double_boost.mcfunction` / `jump/tick.mcfunction` | 各 `ob.vv`（初速）／ `scoreboard players remove @s ob.vv 8`（重力） |
| ジャンプの水平速度 | `jump/move.mcfunction` | `positioned ^ ^ ^0.5`（1 tick あたりの前進量） |
| ジャンプの制限時間 | `jump/tick.mcfunction` | `ob.jt matches 20..`（20 tick = 1 秒） |
| 二段ジャンプの強さ | `jump/double_boost.mcfunction` | `ob.vv 45`（通常ジャンプ・爆発ジャンプの `80` より弱め） |

## 実装メモ

* **全ダメージ無効について** — 耐性V（アンプリファイア4）は通常ダメージを理論上100%軽減しますが、
  `/kill` や虚空落下などの「真ダメージ」は軽減できません。本パックはこれを補うため、
  装備中は毎ティック `attribute minecraft:max_health get` の結果を `Health` に書き戻し、
  体力を強制的に満タンへ戻すことでほぼ全てのダメージ経路を無効化しています。
* **ジャンプ入力の検知について** — 統計 `minecraft.custom:minecraft.jump`（hookshot の
  釣り竿使用検知と同じ仕組み）でジャンプ入力を検知しています。しゃがみ長押し中
  （`ob.charge` が 1〜59）にジャンプ入力があれば爆発なしの通常ジャンプ、60 に達したら
  ジャンプ入力を待たずに自動で爆発ジャンプが発動します。二段ジャンプは、地面に
  ついていない・まだ使っていない状態でのジャンプ入力を検知して発動します。
* **ジャンプの移動について** — プレイヤーの `Motion` を直接書き換えることはできないため、
  hookshot と同様に発射方向を保持する `marker` エンティティを立てて `positioned ^ ^ ^`（水平前進）と
  `~ ~<垂直速度> ~`（放物線）を毎ティック合成し、`tp` で位置だけを更新する方式を採用しています。
  通常ジャンプ・爆発ジャンプは垂直速度 0.80 ブロック/tick、二段ジャンプは 0.45 ブロック/tick
  から毎ティック 0.08 ずつ重力で減衰し、自然に着地します（`jump/launch.mcfunction` が
  発射方向のマーカー設置を共通処理として担当し、各ジャンプの初速だけが異なります）。
* **爆発演出について** — `minecraft:explosion_emitter` パーティクルと爆発音のみを再生しており、
  ブロック破壊や周囲エンティティへのダメージは一切発生しません（見た目だけの演出）。
* 複数プレイヤーが同時にジャンプしても混線しないよう、hookshot の `hs.id` と同様に
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
            ├── jump/    … しゃがみ長押し検知・通常/爆発/二段ジャンプの放物線移動
            └── util/    … 全ダメージ無効化（体力の強制回復）
```
