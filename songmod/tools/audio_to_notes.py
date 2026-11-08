#!/usr/bin/env python3
"""mp3 / mp4 / wav / mid を、songmod 用の音符 JSON に変換する(音声から音程を推定する)。

使い方:
    python3 audio_to_notes.py 曲.mp3
    python3 audio_to_notes.py 曲1.mp3 曲2.mid --install
    python3 audio_to_notes.py 曲.mp3 --drums --start 10 --length 60

必要なもの: ffmpeg、pip install numpy scipy soundfile onnxruntime basic-pitch pretty_midi
(basic-pitch は Spotify の音程推定モデル。和音も読み取れる)

出力 JSON: {"name":..., "tick":0.1, "notes":[[t, midi], ...]}
  t    = 開始時刻(リピーター 1 目盛り = 0.1 秒 = 1 レッドストーン tick 単位)
  midi = MIDI ノート番号(60 = 真ん中のド)
  打楽器は [t, 0, "basedrum" / "snare" / "hat"]
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile

import songtools

TICK = songtools.TICK
LOW, HIGH = songtools.LOW, songtools.HIGH


def to_wav(src, dst, start, length):
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-vn", "-ac", "1", "-ar", "22050"]
    if start:
        cmd += ["-ss", str(start)]
    if length:
        cmd += ["-t", str(length)]
    subprocess.run(cmd + [dst], check=True)


def predict_events(wav, args):
    """wav から、basic-pitch で音を推定する。返り値: [(開始, 終了, MIDI, 強さ)](秒)"""
    from basic_pitch.inference import predict
    _, _, ev = predict(
        wav,
        onset_threshold=args.onset,
        frame_threshold=args.frame,
        minimum_note_length=args.min_len * 1000,
        minimum_frequency=librosa_hz(LOW),
        maximum_frequency=librosa_hz(HIGH),
        melodia_trick=False,  # 映像から読んだ正解と比べると、切ったほうが誤検出が減った
    )
    return [(float(s), float(e), int(p), float(a)) for s, e, p, a, _ in ev]


def events_from_audio(path, args):
    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, "in.wav")
        to_wav(path, wav, args.start, args.length)
        out = predict_events(wav, args)
        if getattr(args, "drums", False):
            # 打楽器(実験的): 番号 200 以上で表す
            for t, code, amp in songtools.detect_drums(wav):
                out.append((t, t + 0.05, code, amp))
    return out


def librosa_hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def events_from_midi(path, args):
    import pretty_midi
    pm = pretty_midi.PrettyMIDI(path)
    out = []
    for inst in pm.instruments:
        for n in inst.notes:
            if inst.is_drum:
                code = songtools.GM_DRUMS.get(n.pitch)
                if code is None or args.no_drums:
                    continue
                out.append((n.start - args.start, n.end - args.start, code, n.velocity / 127.0))
            else:
                out.append((n.start - args.start, n.end - args.start, n.pitch, n.velocity / 127.0))
    return [e for e in out if e[0] >= 0 and (not args.length or e[0] < args.length)]


def convert(path, args, output=None):
    """1 つのファイルを変換して、出力した JSON のパスを返す"""
    ext = os.path.splitext(path)[1].lower()
    ev = events_from_midi(path, args) if ext in (".mid", ".midi") else events_from_audio(path, args)

    # 0.1 秒の格子に量子化。同じ時刻・同じ音は強いほうだけ残す
    grid = {}
    for s, e, p, a in ev:
        if a < args.min_amp:
            continue
        t = int(round(s / args.speed / TICK))
        p = songtools.fold(p if songtools.is_drum(p) else p + args.transpose)
        key = (t, p)
        if key not in grid or grid[key] < a:
            grid[key] = a
    if not args.no_harmonic_filter:
        grid = songtools.suppress_harmonics(grid)
    by_time = {}
    for (t, p), a in grid.items():
        by_time.setdefault(t, []).append((a, p))
    by_time, dropped, moved = songtools.limit_polyphony(by_time, args.max_poly, False)
    stem = os.path.splitext(os.path.basename(path))[0]
    name = args.name or stem
    out = output or os.path.join(os.path.dirname(os.path.abspath(path)), stem + ".json")
    count, seconds = songtools.write_json(out, name, by_time, -1 if args.one_row else args.row_length)
    songtools.report(out, count, seconds, dropped, moved)
    if args.install:
        songtools.install(out, args.songs_dir)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("inputs", nargs="+", help="変換するファイル(いくつでも)")
    ap.add_argument("-o", "--output", help="出力 JSON(ファイルが 1 つのときだけ)")
    ap.add_argument("--name", help="曲名(省略するとファイル名)")
    ap.add_argument("--install", action="store_true", help="できた JSON を、Minecraft の songs フォルダへコピーする")
    ap.add_argument("--songs-dir", help="コピー先の songs フォルダ(省略すると .minecraft/songs を探す)")
    ap.add_argument("--start", type=float, default=0, help="開始位置(秒)")
    ap.add_argument("--length", type=float, default=0, help="長さ(秒)。0 なら最後まで")
    ap.add_argument("--max-poly", type=int, default=12, help="同時に鳴らす音の上限(標準 12)")
    ap.add_argument("--onset", type=float, default=0.6, help="音の出だしの検出しきい値(小さいほど多く拾う)")
    ap.add_argument("--frame", type=float, default=0.3, help="音の持続の検出しきい値")
    ap.add_argument("--no-harmonic-filter", action="store_true", help="倍音の取り違えを取り除く処理をしない")
    ap.add_argument("--min-len", type=float, default=0.06, help="これより短い音は捨てる(秒)")
    ap.add_argument("--min-amp", type=float, default=0.0, help="これより弱い音は捨てる(0〜1)")
    ap.add_argument("--drums", action="store_true", help="音声から打楽器(バスドラム・スネア・ハイハット)も探す(実験的。MIDI のドラムは、いつも読む)")
    ap.add_argument("--no-drums", action="store_true", help="MIDI のドラムを読まない")
    ap.add_argument("--row-length", type=int, default=0, help="(いまは使われません)")
    ap.add_argument("--one-row", action="store_true", help="(いまは使われません。回路は常に一直線)")
    ap.add_argument("--speed", type=float, default=1.0, help="再生速度(2 なら 2 倍速)")
    ap.add_argument("--transpose", type=int, default=0, help="半音単位で移調")
    args = ap.parse_args()
    if args.output and len(args.inputs) > 1:
        sys.exit("-o は、ファイルが 1 つのときだけ使えます")
    failed = 0
    for path in args.inputs:
        print(f"== {path}", file=sys.stderr)
        try:
            convert(path, args, args.output)
        except Exception as e:  # noqa: BLE001  (1 つ失敗しても、ほかのファイルは続ける)
            failed += 1
            print(f"  失敗: {type(e).__name__}: {e}", file=sys.stderr)
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
