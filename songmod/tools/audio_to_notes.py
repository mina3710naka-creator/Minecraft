#!/usr/bin/env python3
"""mp3 / mp4 / wav / mid を、songmod 用の音符 JSON に変換する。

使い方:
    python3 audio_to_notes.py 曲.mp3 -o twinkle.json
    python3 audio_to_notes.py 曲.mp4 --start 10 --length 60 --max-poly 3

必要なもの: ffmpeg、pip install numpy scipy soundfile onnxruntime basic-pitch pretty_midi
(basic-pitch は Spotify の音程推定モデル。和音も読み取れる)

出力 JSON: {"name":..., "tick":0.1, "notes":[[t, midi], ...]}
  t    = 開始時刻(リピーター 1 目盛り = 0.1 秒 = 1 レッドストーン tick 単位)
  midi = MIDI ノート番号(60 = 真ん中のド)
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile

TICK = 0.1  # レッドストーンリピーター 1 目盛りの秒数
LOW, HIGH = 30, 102  # 音符ブロックで出せる範囲(低音 F#1 〜 高音 F#7)


def to_wav(src, dst, start, length):
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-vn", "-ac", "1", "-ar", "22050"]
    if start:
        cmd += ["-ss", str(start)]
    if length:
        cmd += ["-t", str(length)]
    subprocess.run(cmd + [dst], check=True)


def events_from_audio(path, args):
    from basic_pitch.inference import predict
    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, "in.wav")
        to_wav(path, wav, args.start, args.length)
        _, _, ev = predict(
            wav,
            onset_threshold=args.onset,
            frame_threshold=args.frame,
            minimum_note_length=args.min_len * 1000,
            minimum_frequency=librosa_hz(LOW),
            maximum_frequency=librosa_hz(HIGH),
        )
    # (開始, 終了, MIDI, 強さ, bend)
    return [(float(s), float(e), int(p), float(a)) for s, e, p, a, _ in ev]


def librosa_hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def events_from_midi(path, args):
    import pretty_midi
    pm = pretty_midi.PrettyMIDI(path)
    out = []
    for inst in pm.instruments:
        if inst.is_drum:
            continue
        for n in inst.notes:
            out.append((n.start - args.start, n.end - args.start, n.pitch, n.velocity / 127.0))
    return [e for e in out if e[0] >= 0 and (not args.length or e[0] < args.length)]


def fold(p):
    while p < LOW:
        p += 12
    while p > HIGH:
        p -= 12
    return p


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input")
    ap.add_argument("-o", "--output")
    ap.add_argument("--name", help="曲名(省略するとファイル名)")
    ap.add_argument("--start", type=float, default=0, help="開始位置(秒)")
    ap.add_argument("--length", type=float, default=0, help="長さ(秒)。0 なら最後まで")
    ap.add_argument("--max-poly", type=int, default=3, help="同時に鳴らす音の上限(1 か所の音符ブロック配置の都合で 3 が標準)")
    ap.add_argument("--onset", type=float, default=0.5, help="音の出だしの検出しきい値(小さいほど多く拾う)")
    ap.add_argument("--frame", type=float, default=0.3, help="音の持続の検出しきい値")
    ap.add_argument("--min-len", type=float, default=0.06, help="これより短い音は捨てる(秒)")
    ap.add_argument("--min-amp", type=float, default=0.0, help="これより弱い音は捨てる(0〜1)")
    ap.add_argument("--row-length", type=int, default=0, help="回路を何マスで折り返すか(奇数に切り上げ)。0 なら自動。ゲーム内の /song build <名前> <マス数> で上書きもできる")
    ap.add_argument("--speed", type=float, default=1.0, help="再生速度(2 なら 2 倍速)")
    ap.add_argument("--transpose", type=int, default=0, help="半音単位で移調")
    args = ap.parse_args()

    ext = os.path.splitext(args.input)[1].lower()
    ev = events_from_midi(args.input, args) if ext in (".mid", ".midi") else events_from_audio(args.input, args)

    # 0.1 秒の格子に量子化。同じ時刻・同じ音は強いほうだけ残す
    grid = {}
    for s, e, p, a in ev:
        if a < args.min_amp:
            continue
        t = int(round(s / args.speed / TICK))
        p = fold(p + args.transpose)
        key = (t, p)
        if key not in grid or grid[key] < a:
            grid[key] = a
    by_time = {}
    for (t, p), a in grid.items():
        by_time.setdefault(t, []).append((a, p))
    notes, dropped = [], 0
    for t in sorted(by_time):
        chosen = sorted(by_time[t], reverse=True)
        dropped += max(0, len(chosen) - args.max_poly)
        for a, p in sorted(chosen[: args.max_poly], key=lambda x: x[1]):
            notes.append([t - min(by_time), p])
    name = args.name or os.path.splitext(os.path.basename(args.input))[0]
    out = args.output or (name + ".json")
    with open(out, "w", encoding="utf-8") as f:
        row = '"row_length": %d, ' % args.row_length if args.row_length else ""
        f.write('{"name": %s, "tick": %s, %s"notes": [\n' % (json.dumps(name, ensure_ascii=False), TICK, row))
        f.write(",\n".join(json.dumps(n) for n in notes))
        f.write("\n]}\n")
    dur = notes[-1][0] * TICK if notes else 0
    print(f"{out}: {len(notes)} 音, 長さ {dur:.1f} 秒, 同時発音の上限で落とした音 {dropped}", file=sys.stderr)


if __name__ == "__main__":
    main()
