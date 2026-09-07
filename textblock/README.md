# テキストブロック (Text to Blocks) — Minecraft データパック

指定した**文字**を指定した**ブロック**で組み立てて建築してくれるデータパックです。
`/function` コマンドに文字列とブロックを渡すだけで、8x8 ドットの日本語ビットマップフォントを使い、
プレイヤーが向いている方角（東西南北）の少し前方に、地面から立ち上がる形で文字の壁を組み立てます。
**ひらがな・カタカナ・常用漢字2136字**をすべて収録しているので、日本語の文章もそのまま建築できます。

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
   チャットに `[テキストブロック] 読み込み完了` と表示されれば成功です
   （フォントデータが2400字以上あるため、読み込みに数秒かかることがあります）。

3. 建てたい方向を向いてから、次のコマンドを実行します。

   ```
   /function textblock:build {text:"HELLO",block:"minecraft:white_concrete"}
   ```

   ```
   /function textblock:build {text:"こんにちは",block:"minecraft:red_wool"}
   ```

   `text` に表示したい文字列（**20文字まで**）、`block` に使いたいブロックの ID を、
   **どちらもダブルクォートで囲んで**指定してください。
   自分の立っている場所の2ブロック前方・足元より少し上から、向いている方角に沿って文字が組み上がります。

4. もっと大きく作りたいときは `build_scaled` を使い、`scale`（1マスあたりのブロック数）を指定します。

   ```
   /function textblock:build_scaled {text:"祝",block:"minecraft:redstone_block",scale:3}
   ```

5. 直前の建築を取り消したいときは次のコマンドを実行します（元あった場所の座標を記録しているので、
   その範囲を空気ブロックに戻します）。

   ```
   /function textblock:undo
   ```

---

## 対応している文字

* 半角英数記号（ASCII 0x20-0x7E 全て）: `A`-`Z` `a`-`z` `0`-`9` 半角スペース、`!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~` などの記号
* ひらがな・カタカナ（小書き文字・濁点/半濁点付き・長音符「ー」・中黒「・」なども含めて全て）
* **常用漢字 2,136字**（2010年内閣告示の常用漢字表に基づく、日本語の新聞・公用文で使われる範囲の漢字をすべて収録）

上記にない文字（常用漢字表にない漢字や絵文字など）は無視され、半角1文字ぶんの隙間だけ空けて次の文字に進みます。
また `text` の中に `"`（ダブルクォート）を含めることはできません。

**1回の建築で指定できる文字数は20文字までです。** それを超えるとエラーメッセージが出て建築されません
（漢字は横幅が半角の2倍あるため、20文字の漢字だけの文章でも横幅は最大 160 マス〔scale:1 のとき〕になります）。

---

## 仕組み

| 順番 | 処理 |
| --- | --- |
| 1 | `textblock:font/init` が、半角英数記号・かな・常用漢字ぶんの **8x8 ドットパターン**（約2,400字）を `storage textblock:font` に読み込む（`/reload` のたびに再構築） |
| 2 | `textblock:build` / `textblock:build_scaled` が、渡された `text` と `block`、`scale` を `storage textblock:job` にまとめる |
| 3 | `textblock:check_length` が、21文字目が存在するかを `data modify ... set string` で確認し、20文字を超えていればエラーで中断する |
| 4 | `textblock:facing` が、プレイヤーの `yaw` を 90° ごとの4方向（南西北東）にスナップし、「2ブロック前方」を建築の基準点にする |
| 5 | `textblock:char_loop` が `data modify ... set string` で **1文字ずつ**文字列から取り出し、取り出せなくなったら完了処理へ |
| 6 | `textblock:draw_glyph` が、その文字に対応するピクセルパターンと文字幅（半角4／全角8）を `storage textblock:font` から検索（マクロ機能でリストを絞り込み） |
| 7 | `textblock:draw_pixel` が、点灯しているピクセル1つにつき `fill` を1回実行（`scale` が2以上なら `scale × scale` の正方形になる） |
| 8 | 全文字を処理し終えたら `textblock:finish` が建築範囲のバウンディングボックスを記録し、完了メッセージを表示 |

プレイヤーの座標・向きの取得や1文字ごとのオフセット計算はすべて `scoreboard` の四則演算で行っており、
`tp` や `execute positioned ^ ^ ^`（カレット座標）は使わず、常にワールド座標（東西南北）に沿って
ブロックを配置しています。座標がすべて整数の軸に揃うため、`textblock:undo` で正確に元通りに戻せます。

### フォントの出典

ドットパターンは [美咲フォント](http://littlelimit.net/misaki.htm)（8x8 dot Japanese font, Copyright (C) 2002-2015 Num Kadoma,
"These fonts are free softwares. Unlimited permission is granted to use, copy, and distribute it,
with or without modification, either commercially and noncommercially."）を、
Arduboy 向けに変換した [emutyworks/8x8DotJPFont](https://github.com/emutyworks/8x8DotJPFont)（MIT License）の
ヘッダファイルから、常用漢字表（2,136字）・ひらがな・カタカナ・半角英数記号にあたる文字を抽出して
`font/init.mcfunction` のドット座標データに変換したものです。

---

## 調整できる数値

| 内容 | ファイル | 該当箇所 |
| --- | --- | --- |
| 建築を始める前方距離 | `facing.mcfunction` | `scoreboard players set #fwd tb 2`（プレイヤーから何ブロック前方に建てるか） |
| 1回に建てられる最大文字数 | `check_length.mcfunction` | `set string storage textblock:job state.text 20 21`（21文字目の存在チェック） |
| 文字の高さ | `draw_pixel.mcfunction` / `finish.mcfunction` | `6`（7行フォントの最大行インデックス）/ `7`（フォントの行数） |
| フォントのドットパターン・文字幅 | `font/init.mcfunction` | 各文字の `w`（半角4／全角8）と `px:[{x:..,y:..},...]` |

### 制限事項

* 対応方角は南北東西の4方向へのスナップです（斜め向きでは建てられません）。
* 1回の建築で指定できるのは20文字までです（`check_length.mcfunction` が事前にチェックします）。
* 常用漢字表にない漢字（人名用漢字や旧字体、異体字など）や、ひらがな・カタカナ・常用漢字・半角記号以外の文字
  （絵文字、ハングル、繁体字など）には対応していません。
