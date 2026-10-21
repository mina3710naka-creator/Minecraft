# レッドストーンランプ動画 (Lamp Video)

動画をレッドストーンランプの点灯/消灯（白黒）で再生するデータパックを生成します。
MP3 は音声だけで映像がないので、**MP4 などの動画ファイル**を使ってください（音声があれば一緒に再生できます）。

## 速度について
コマンドは 20tick/秒なので、1tick に 1 フレームだと 30fps の動画は **2/3 倍速**になります。
既定（`--speed slow`）はこの 2/3 倍速で、全フレームを使います。音声も 2/3 に伸縮します。
`--speed normal` なら 20fps に間引いて等速で再生します。

## 使い方
```
pip install numpy pillow   # ffmpeg も必要
python3 make_lampvideo.py input.mp4 --out out --width 360 --zip
```
1. `out/lampvideo_dp.zip`（zipのまま）を `datapacks` に、`out/lampvideo_rp` を `resourcepacks` に入れる
2. ワールドで `/reload`
3. 平らな場所で `/function lampvideo:setup`（足元が画面の左下角。南から北向きに見る）
4. `/function lampvideo:play` で再生、`/function lampvideo:stop` で停止

差分のあるランプだけ `fill` でまとめて書き換えます。360x360 は約13万個のランプなので、
画面全体が読み込まれる範囲（シミュレーション距離）に立ち、重い場合は `--width 180` などに下げてください。
`--threshold` で白黒の閾値、`--dither` でディザを調整できます。
