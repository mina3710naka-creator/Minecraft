# Lamp Video MOD（レッドストーンランプ動画）

動画ファイル（`.lvf`）を、レッドストーンランプの点灯/消灯（白黒）で再生する Fabric MOD です。
データパックと違って、**動画データを外部ファイルに置き、再生中に少しずつ読む**ので、
`/reload` やワールドを開くときに重くなりません（コマンドを大量に解析しないため）。
区間パックの切り替えや、1つずつ置いて外す導入作業も要りません。

映像の**暗い部分が点灯、明るい部分が消灯**です（線画なら線が光ります）。
全フレームを使い、各フレームを3tickずつ表示します（約0.28倍速。作るときに変えられます）。

> **ビルドと動作確認は、この環境ではできていません。** Minecraft 26.x の API に合わせて書きましたが、
> 実際にビルドしていないので、コンパイルエラーが出るかもしれません。その場合は、エラーをそのまま教えてください。
> （動画ファイルの読み書きだけは、Python で書いたものを Java で読んで、一致することを確認済みです。）

## 同梱のもの（`sample/`）
| ファイル | 内容 |
| --- | --- |
| `video240.lvf`（3.3MB） | 240×240 の動画。Y=80 以下で `setup` する |
| `video360.lvf`（8.3MB） | 360×360 の動画。Y=-40 以下（スーパーフラットの地面など）で `setup` する |
| `lampvideo_rp/` | 音声（約11分30秒）とランプの単色テクスチャのリソースパック |

## 1. MOD をビルドして入れる
`healingblock` と同じ手順です（`healingblock/README.md` の「手順1」以降を参照）。

1. https://fabricmc.net/develop/template/ で、Mod ID を `lampvideo`、Package Name を `com.lampvideo`、
   遊んでいる Minecraft のバージョンを選んでテンプレートを生成する（Advanced Options はすべてチェックなし）。
2. 生成されたフォルダの `src/main/java/` に、このフォルダの `src/main/java/com/lampvideo/` をコピーし、
   `src/main/resources/fabric.mod.json` を、このフォルダのものに置き換える。
   （`build.gradle` と `gradle.properties` は、**生成されたものをそのまま**使ってください。）
3. `./gradlew build` で、`build/libs/` にできた jar（`-sources` が付かない方）を、`.minecraft/mods/` に入れる。
   Fabric API も必要です。

## 2. 動画ファイルとリソースパックを置く
- `video240.lvf`（または `video360.lvf`）を、`.minecraft/lampvideo/` に置く（フォルダがなければ作る）。
- `lampvideo_rp` を、`.minecraft/resourcepacks/` に置いて、有効にする（音声とテクスチャ用。映像だけなら不要）。

## 3. 再生する
平らな場所に立って、次を実行します。
```
/lampvideo setup video240     # 足元が画面の左下角。南側(+Z)に立って北を向いて見る
/lampvideo play               # 再生（音声も鳴る）
/lampvideo stop               # 停止
/lampvideo remove             # 画面を片付ける
```
- 画面は、足元から右（+X）と上（+Y）に広がり、手前に明るさ15の光源ブロックを敷きます。
- 画面の範囲は、常に読み込まれるようになります（`remove` で解除）。
- 画面の高さ分だけ上に伸びるので、**Y=319 を超えないように**してください（超える場合は `setup` がエラーで止まります）。
- コマンドの権限は設定していません。サーバーで使う場合は、オペレーター以外も実行できます。

## 自分の動画を作る
```
pip install numpy pillow     # ffmpeg も必要
python3 ../lampvideo/make_lvf.py input.mp4 --out out --name myvideo --width 240 --hold 3
```
`out/myvideo.lvf` と `out/lampvideo_rp/` ができます。`--hold N` は全フレームを N tick ずつ表示します
（再生速度と音声もそれに合わせて 1/N になります）。`--width` はランプの横の数です。

## 仕組み（メモ）
- ランプは、隣のブロックに通知しない設定（`setBlock` のフラグ2）で、`lit` を直接切り替えます。
  無電力で点灯したランプが約4tick後に勝手に消える現象は、隣への通知があるときだけ起きるので、これで防げます。
  データパック版にあった「背面の電源ブロック」は要りません。
- フレームは、前のフレームとの差分の長方形だけを持ちます。100フレームで全面を1周描き直し（帯状に少しずつ）、
  書き込みに失敗しても直ります。
- 形式は `src/main/java/com/lampvideo/LvfVideo.java` の説明を参照してください。
