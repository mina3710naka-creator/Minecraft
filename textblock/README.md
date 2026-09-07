# テキストブロック (Text to Blocks) — Minecraft データパック

指定した**文字**を指定した**ブロック**で組み立てて建築してくれるデータパックです。
`/function` コマンドに文字列とブロックを渡すだけで、自作の 3x5 ドット文字フォントを使い、
プレイヤーが向いている方角（東西南北）の少し前方に、地面から立ち上がる形で文字の壁を組み立てます。

対応バージョン: **Minecraft Java Edition 26.2**（データパック形式 **107** / `min_format` `max_format` 方式）

---

## 使い方

1. このリポジトリの `textblock` フォルダを、ワールドの `datapacks` フォルダにコピーします。

   ```
   .minecraft/saves/<ワールド名>/datapacks/textblock/
   ├── pack.mcmeta
   └── data/
   ```

2. ワールドに入って `/reload` を実行します。
   チャットに `[テキストブロック] 読み込み完了` と表示されれば成功です。

3. 建てたい方向を向いてから、次のコマンドを実行します。

   ```
   /function textblock:build {text:"HELLO",block:"minecraft:white_concrete"}
   ```

   `text` に表示したい文字列、`block` に使いたいブロックの ID を、**どちらもダブルクォートで囲んで**指定してください。
   自分の立っている場所の2ブロック前方・足元より少し上から、向いている方角に沿って文字が組み上がります。

4. もっと大きく作りたいときは `build_scaled` を使い、`scale`（1マスあたりのブロック数）を指定します。

   ```
   /function textblock:build_scaled {text:"HI",block:"minecraft:redstone_block",scale:3}
   ```

5. 直前の建築を取り消したいときは次のコマンドを実行します（元あった場所の座標を記録しているので、
   その範囲を空気ブロックに戻します）。

   ```
   /function textblock:undo
   ```

---

## 対応している文字

* アルファベット `A`-`Z`（大文字・小文字とも同じ形で表示されます）
* 数字 `0`-`9`
* 半角スペース（空白としてそのまま隙間になります）
* 記号 `! ? . , - : '`

上記にない文字（ひらがな・漢字・全角文字など）は無視され、その分の隙間だけ空けて次の文字に進みます。
また `text` の中に `"`（ダブルクォート）を含めることはできません。

---

## 仕組み

| 順番 | 処理 |
| --- | --- |
| 1 | `textblock:font/init` が、A-Z・a-z・0-9・記号ぶんの **3x5 ピクセルパターン**を `storage textblock:font` に読み込む（`/reload` のたびに再構築） |
| 2 | `textblock:build` / `textblock:build_scaled` が、渡された `text` と `block`、`scale` を `storage textblock:job` にまとめる |
| 3 | `textblock:facing` が、プレイヤーの `yaw` を 90° ごとの4方向（南西北東）にスナップし、「2ブロック前方」を建築の基準点にする |
| 4 | `textblock:char_loop` が `data modify ... set string` で **1文字ずつ**文字列から取り出し、取り出せなくなったら完了処理へ |
| 5 | `textblock:draw_glyph` が、その文字に対応するピクセルパターンを `storage textblock:font` から検索（マクロ機能でリストを絞り込み） |
| 6 | `textblock:draw_pixel` が、点灯しているピクセル1つにつき `fill` を1回実行（`scale` が2以上なら `scale × scale` の正方形になる） |
| 7 | 全文字を処理し終えたら `textblock:finish` が建築範囲のバウンディングボックスを記録し、完了メッセージを表示 |

プレイヤーの座標・向きの取得や1文字ごとのオフセット計算はすべて `scoreboard` の四則演算で行っており、
`tp` や `execute positioned ^ ^ ^`（カレット座標）は使わず、常にワールド座標（東西南北）に沿って
ブロックを配置しています。座標がすべて整数の軸に揃うため、`textblock:undo` で正確に元通りに戻せます。

---

## 調整できる数値

| 内容 | ファイル | 該当箇所 |
| --- | --- | --- |
| 建築を始める前方距離 | `facing.mcfunction` | `scoreboard players set #fwd tb 2`（プレイヤーから何ブロック前方に建てるか） |
| 文字の幅・文字間の隙間 | `char_found.mcfunction` | `scoreboard players set #step tb 4`（3マスの文字幅＋1マスの隙間） |
| 文字の高さ | `draw_pixel.mcfunction` / `finish.mcfunction` | `4`（5行フォントの最大行インデックス）/ `5`（フォントの行数） |
| フォントのドットパターン | `font/init.mcfunction` | 各文字の `px:[{x:..,y:..},...]` |

### 制限事項

* 対応方角は南北東西の4方向へのスナップです（斜め向きでは建てられません）。
* 一度に建てられる文字数が非常に多い場合、Minecraft の関数呼び出し回数の上限に達する可能性があります。
  目安として1回あたり30文字程度までを推奨します。
