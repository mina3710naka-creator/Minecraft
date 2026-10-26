# Character Statue(キャラクターの像 MOD)

JSON ファイルを読み込んで、キャラクターの像を **1層目(いちばん下)から順に** 建てる Fabric MOD です。

## 使い方
1. `statuemod/dist/statuemod-mc26.3.jar` を `.minecraft/mods/` に入れる(Fabric Loader と Fabric API が必要)。
2. 起動すると `.minecraft/statues/` フォルダができ、サンプル像(クリーパー・スティーブ・ブタ・雪だるま・スライム・エンダーマン)の JSON が入ります。
3. 建てたい場所に立ち、建てたい方向を向いて `/statue build creeper` と打つ。
   - プレイヤーの 1 ブロック先から奥へ建ち、像の正面はプレイヤー側を向きます。
   - 自分の JSON は `.minecraft/statues/` に入れるだけ(ファイル名が像の名前)。毎回読み直すので再起動は不要です。

## コマンド
| コマンド | 内容 |
| --- | --- |
| `/statue list` | statues フォルダにある像の一覧 |
| `/statue build <名前> [1層ごとのtick]` | 今いる場所の前に建てる(省略時は 6 tick = 約0.3秒/層。0 で一気に) |
| `/statue buildat <名前> <座標> <north/east/south/west>` | 座標と建てていく方向を指定して建てる(コマンドブロック用) |
| `/statue stop` | 建築を途中で止める |
| `/statue undo` | 直前に建てた像を元に戻す(置き換えたブロックも復元) |
| `/statue reload` | 全 JSON の書式チェック |

## JSON の形式
```json
{
  "name": "表示名",
  "replace_air": false,
  "palette": {
    "G": "minecraft:lime_concrete",
    "K": "minecraft:black_concrete",
    "S": "minecraft:oak_stairs[facing=north]"
  },
  "layers": [
    [ "GGGG", "GGGG" ],
    [ "GKKG", "GGGG" ]
  ]
}
```
- `layers` の先頭が **1層目(いちばん下)**。上へ順に建ちます。
- 1層は文字列の配列で、**先頭の行が像の正面(手前)**、各行は正面から見て **左→右**。
- 文字は `palette` のキー(1文字)。**空白は何も置きません。**
- `minecraft:air` や `replace_air: false`(既定)のとき、空気のマスは既存ブロックをそのまま残します。`true` にすると空気で上書きします。
- ブロックの向き(`facing=` など)は「北を向いて像を見たとき(正面が南向き)」の向きで書くと、建てる方向に合わせて自動で回転します。

## ビルド
`.github/workflows/build-statuemod.yml`(Actions)で jar を作り `statuemod/dist/` に保存します。
手元では JDK 25 と Gradle 9.5 以上で `gradle build`。サンプル JSON は `python3 tools/make_statues.py` で作り直せます。
