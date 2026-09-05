# フックショット (Hookshot) — Minecraft データパック

釣り竿を右クリックすると、視点方向へ**透明な防具立て（フック）**が飛んでいき、
壁や床に当たるとそこに**マーカーが固定**され、プレイヤーが**滑らかに引き寄せられる**
ワイヤーアクション用データパックです。すべて `function`（コマンド）で実装しています。

対応バージョン: **Minecraft Java Edition 26.2**（データパック形式 **107** / `min_format` `max_format` 方式）

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
| 引き寄せの初速 | `pull/start.mcfunction` | `hs.spd 20`（= 0.2 ブロック / tick） |
| 加速度・最高速度 | `pull/tick.mcfunction` | `add @s hs.spd 6` / `matches 90..`（= 0.9 ブロック / tick） |
| 減速ゾーン | `pull/move.mcfunction` | `distance=1.5..3` → 0.3、`distance=3..5` → 0.6 ブロック / tick に制限 |
| 引き寄せの制限時間 | `pull/tick.mcfunction` | `hs.pt matches 60..`（60 tick = 3 秒） |
| 到達とみなす距離 | `pull/move.mcfunction` / `pull/step.mcfunction` | `distance=..1.5` |
| 通り抜けるブロック | `data/hookshot/tags/block/passable.json` | 草・水・松明などフックが貫通するブロック |
| 落下ダメージ無効化（解除後） | `release.mcfunction` | 最終行の `effect give ... slow_falling` のコメントを外す |
| 移動を安全確認する分割数 | `pull/move.mcfunction` | `scoreboard players set @s hs.sub 4`（1 tick を何分割するか） |
| 対応バージョン範囲 | `pack.mcmeta` | `min_format` / `max_format`（26.2 = 107） |

---

## 実装メモ

* **滑らかな移動について** — Java 版のバニラでは、プレイヤーの `Motion`（速度）NBT を
  コマンドで書き換えることはできません（`/data` はプレイヤーの移動データを変更できない）。
  そのため本パックは速度ベクトルをスコア `hs.spd`（1/100 スケール）として保持し、
  毎ティック `execute ... facing entity <マーカー> feet ... run tp @s ^ ^ ^<ステップ>`
  で位置だけを更新する方式で「motion 相当」を再現しています。
  * 回転を書き換えないので視点がブレません。
  * 速度を毎ティック少しずつ加算（イーズイン）し、着弾点に近づくと段階的に
    減速する（イーズアウト）ため、動き出しも止まる瞬間もカクつきません。
  * `/tp` は毎ティック落下距離をリセットするので、引き寄せ中に落下ダメージは入りません。
    ただし tick の合間にクライアント側で重力落下が予測され、次の tp で軌道へ
    引き戻される……という綱引きが体感的な「ガクつき」の原因になるため、
    引き寄せ中は `minecraft:slow_falling` を毎ティック付与して重力の影響を
    ほぼ消し、この綱引きを抑えています（`pull/start.mcfunction` /
    `pull/tick.mcfunction`）。
* **壁へのめり込み対策** — 1 ティック分の移動を `pull/step.mcfunction` で
  4 分割し、フックの飛行（`hook/step.mcfunction`）と同じように **0.25 ブロック
  ステップごとに着弾判定をしてから進む**ようにしています。これにより、
  「まず大きく移動してから着地点を確認する」旧方式で起きていた、壁の目の前
  まで一気に踏み込んでめり込む問題を解消しています。壁や床にぶつかったら
  その場で即座に解除されます。
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
