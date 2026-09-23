# テイムの杖 (Tame Wand) — Minecraft データパック

トライデントで**通常攻撃した野生のMOBを仲間にし**、**Shift+攻撃で仲間に攻撃対象を
指示できる**データパックです。すべて `function`（コマンド）で実装しています。

---

## 動作の流れ

| 操作 | 効果 |
| --- | --- |
| **通常攻撃**でMOBに命中 | そのMOBを**仲間（テイム）**にする。全回復し、以降はプレイヤーにふわふわ追従する |
| **Shift+攻撃**でMOBに命中 | 近くにいる**仲間全員**に、そのMOBへ**突撃して攻撃**するよう命令する |
| 投げる（通常のトライデント操作） | いつもどおり普通のトライデントとして飛んでいく（一切妨げない） |

* 仲間にできるのは**同時に最大5体**まで。
* テイムの効果は**約3分（3600ティック）**で自然に解けて、元の野生のMOB（通常AI）に戻る。
* 仲間はダメージを受けて死ぬこともある、**普通に戦える本物のMOB**。無敵ではない。
* すでに誰かの仲間になっているMOB（自分のものも含む）は、テイムし直したり攻撃対象に
  指定したりできない。
* 仲間は `NoAI` で元のAI（プレイヤーを襲う・徘徊する等）を止め、以降は完全にこの
  データパックが毎ティック「追従」または「攻撃」の動きを直接制御する
  （hookshot の台車と同じ、`facing entity` → `tp ^ ^ ^` のローカル座標移動方式）。

---

## 導入方法

1. このリポジトリの `tamewand` フォルダを、ワールドの `datapacks` フォルダにコピーします。

   ```
   .minecraft/saves/<ワールド名>/datapacks/tamewand/
   ├── pack.mcmeta
   └── data/
   ```

   （サーバーの場合は `<サーバーフォルダ>/world/datapacks/tamewand/`）

2. ワールドに入って `/reload` を実行します。
   チャットに `[テイムの杖] 読み込み完了` と表示されれば成功です。

3. アイテムを受け取ります。

   ```
   /function tamewand:give
   ```

4. 使い方はいつでも次のコマンドで確認できます。

   ```
   /function tamewand:help
   ```

### アンインストール

```
/function tamewand:uninstall
```
を実行すると、テイム中の仲間は全員もとの野生の状態に戻ります。
そのあとでデータパックのフォルダを削除してください。

---

## 調整できる数値

| 内容 | ファイル | 該当箇所 |
| --- | --- | --- |
| 同時にテイムできる数 | `tame/dispatch2.mcfunction` | `matches 5..`（上限チェック）と `limit=6`（数え上げの上限） |
| テイムの持続時間 | `tame/apply.mcfunction` | `scoreboard players set @s tw.life 3600`（3600 tick = 3分） |
| 追従を開始する距離 | `tame/follow_do.mcfunction` | `distance=..3` |
| 追従の速さ | `tame/follow_do.mcfunction` | `tp @s ^ ^ ^0.5`（1 tick あたり 0.5 ブロック） |
| 攻撃命令が届く範囲 | `command/order_allies.mcfunction` | `distance=..16` |
| 突撃の速さ | `tame/attack_do.mcfunction` | `tp @s ^ ^ ^0.6` |
| 攻撃の間隔・威力 | `tame/attack_hit.mcfunction` | `scoreboard players set @s tw.cd 15`（15 tick 間隔）、`damage ... 4` |
| 攻撃命令の目印の有効期限 | `command/mark_target.mcfunction` / `util/target_timeout.mcfunction` | `tw.ttl 600`（600 tick = 30秒放置で解除） |

---

## 実装メモ

* MOBへの命中検知は `minecraft:player_hurt_entity` アドバンスメント（spiderweb系パックと
  同じ手法）。**近接攻撃でのみ**発火するので、トライデントを投げたときの着弾ダメージ
  では反応せず、通常の投擲利用を妨げない。
* 命中した「相手」は、アドバンスメントの報酬関数自体には渡されないため、命中直後の
  プレイヤーの近く（4ブロック以内）にいる最も近い有効なMOBを対象とみなしている
  （hookshot の着弾判定と同じ考え方）。
* 所有者（テイムした本人）との対応付けは、プレイヤーごとに発行する整数ID
  （`tw.pid`）を仲間側の `tw.owner` に持たせる形。hookshot の `hs.id` と同じ
  「NBTストレージ＋マクロ関数」でIDを次の関数へ橋渡しする方式を踏襲している。
* 同時所有数の上限チェックは、スコアで数を持ち回さず、その場で
  `@e[tag=tw.tamed,scores={tw.owner=<pid>}]` を数え直している（`tag add` が
  マッチ数を結果として返す性質を利用）。これにより、仲間が死んで数が減っても
  ズレが発生しない。
