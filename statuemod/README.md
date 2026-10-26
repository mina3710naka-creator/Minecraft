# Character Statue(キャラクターの像 MOD)

JSON ファイルを読み込んで、キャラクターの像を **1層目(いちばん下)から順に** 建てる Fabric MOD です。

## 使い方
1. `statuemod/dist/statuemod-mc26.3.jar` を `.minecraft/mods/` に入れる(Fabric Loader と Fabric API が必要)。
2. 起動すると `.minecraft/statues/` フォルダができ、サンプル像(ルフィ・クリーパー・スティーブ・ブタ・雪だるま・スライム・エンダーマン)の JSON が入ります。
   古い版のサンプルは `.json.bak` に控えを残して新しい版に差し替わります(自分で作ったファイルは触りません)。
3. 建てたい場所に立ち、建てたい方向を向いて `/statue build creeper` と打つ。
   - プレイヤーの 1 ブロック先から奥へ建ち、像の正面はプレイヤー側を向きます。
   - 自分の JSON は `.minecraft/statues/` に入れるだけ(ファイル名が像の名前)。毎回読み直すので再起動は不要です。

## ルフィの像(`/statue build luffy`)
- **150 ブロック = 1 m**。身長 174 cm = 約 261 ブロック、麦わら帽子を入れて高さ約 294(幅 124 × 奥行 76)。約 30 万ブロック。
- ポーズは、胸の前で両手を合わせて立つ立ち姿(参考画像に合わせたもの)。赤いジャケット(前開き)・胸の傷と腹筋・黄色いサッシュ(右腰の結び目と垂れ)・ふちに白いファーの付いた青い短パン・ぞうり・左目の下の傷・傾いた麦わら帽子。背中側はジャケットのすそと後ろ髪を再現。
- **高さが約 294 あるので、世界の高さの上限(Y=319)を超えない場所に立つ必要があります**(Y=25 以下。スーパーフラットなら地面が Y=-60 なので問題なし。普通のワールドの地面 Y=63 だと上が切れます。範囲を超える場合は建築前に警告が出ます)。
- `tools/make_luffy.py` で形・色を調整して作り直せます。

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
手元では JDK 25 と Gradle 9.5 以上で `gradle build`。サンプル JSON は `python3 tools/make_statues.py`(ルフィは `tools/make_luffy.py`)で作り直せます。
