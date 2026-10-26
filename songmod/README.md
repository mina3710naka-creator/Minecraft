# Song Builder(曲を音符ブロックの回路にする MOD)

mp3 / mp4 の曲を音程に変換し、**リピーターと音符ブロックの演奏回路**をマイクラ内に建てる Fabric MOD です。

## 手順
1. 曲を JSON に変換する(ffmpeg と Python が必要):
   ```
   pip install numpy scipy soundfile onnxruntime basic-pitch pretty_midi
   python3 tools/audio_to_notes.py 曲.mp3 -o 曲名.json --length 60
   ```
   音程は Spotify の basic-pitch(和音も読める)で推定します。主なオプション: `--start/--length`(秒)、`--max-poly`(同時発音数、標準3)、`--onset`(小さいほど多く拾う)、`--transpose`、`--speed`。`.mid` も読めます。
2. 変換した JSON を `.minecraft/songs/` に入れ、`songmod-mc26.3.jar` を `mods/` に入れる(Fabric Loader / Fabric API が必要)。
3. 建てたい場所で建てたい方向を向いて `/song build 曲名`。始点のボタンを押すと演奏が始まります。

## コマンド
`/song list` / `/song build <名前> [1列のマス数]` / `/song stop` / `/song undo`(置いた分を元に戻す)

## 回路のしくみ
石ブロック B とリピーター R を `B R B R …` と交互に並べた 1 本の線(長いので蛇行)。R の遅延(1〜4)の合計が時刻で、音が鳴る時刻の B の横(と上)に音符ブロックを置きます。音域で楽器が決まります(低:ベース、中:ハープ、高:ベル)。

## 制限
- 時間の細かさはリピーター 1 目盛り = 0.1 秒。
- 同時に鳴らせるのは 1 時刻 3 音まで(超えた分は変換時に弱い音から省略)。
- 音域は F#1〜F#7。範囲外は 1 オクターブずつ折り返します。
- 長い曲は回路が大きくなります(1 分 ≒ 数十×数十マス)。音符ブロックの音が聞こえる範囲は約 48 ブロックなので、中央付近で聞くのがおすすめです。
- ドラムなどの打楽器音は音程として読むため、そのままは再現できません。
