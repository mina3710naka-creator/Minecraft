#!/usr/bin/env python3
"""ピアノロール動画(Synthesia 風。下に 88 鍵の鍵盤、弾いている鍵が色づく)を、songmod 用の音符 JSON に変換する。

鍵盤の光り方を 1 フレームずつ読むので、音声から推定するより音程・タイミングが正確。
    python3 video_to_notes.py 曲.mp4 -o 曲名.json --row-length 61

必要なもの: ffmpeg、pip install numpy
動画は 16:9、鍵盤が画面の下端(縦 280〜360/360 あたり)に 88 鍵(A0〜C8)ぴったり横幅いっぱい、という配置を想定。
違う配置なら --kb-* で調整する(座標は 640x360 に縮めたときの値)。
"""
import argparse
import subprocess
import sys

import numpy as np

import songtools

W, H = 640, 360
CROP_Y, CROP_H = 255, 104  # 鍵盤まわりだけ切り出す(縦は偶数)
FPS = 30
WHITE_PATTERN = [0, 2, 3, 5, 7, 8, 10]  # A B C D E F G の A からの半音数


def layout(first_midi=21, white_keys=52):
    """[(MIDI, x, 'w' or 'b')]。A0 から始まる鍵盤"""
    kw = W / white_keys
    keys = []
    for i in range(white_keys):
        m = first_midi + (i // 7) * 12 + WHITE_PATTERN[i % 7]
        keys.append((m, (i + 0.5) * kw, "w"))
        if i % 7 in (0, 2, 3, 5, 6) and i < white_keys - 1:
            keys.append((m + 1, (i + 1) * kw, "b"))
    return sorted(keys)


def lit_matrix(path, keys, args):
    cmd = ["ffmpeg", "-loglevel", "error", "-i", path, "-an",
           "-vf", f"fps={FPS},scale={W}:{H},crop={W}:{CROP_H}:0:{CROP_Y}",
           "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE)
    fsize = W * CROP_H * 3
    wy = (args.white_y - CROP_Y, args.white_y - CROP_Y + 7)   # 白鍵の下のほう(黒鍵がない所)
    by = (args.black_y - CROP_Y, args.black_y - CROP_Y + 7)   # 黒鍵の真ん中あたり
    rows = []
    while True:
        buf = proc.stdout.read(fsize * 300)
        if not buf:
            break
        n = len(buf) // fsize
        blk = np.frombuffer(buf, np.uint8, n * fsize).reshape(n, CROP_H, W, 3).astype(np.int16)
        lit = np.zeros((n, len(keys)), bool)
        for j, (_, x, t) in enumerate(keys):
            x0 = int(round(x))
            reg = blk[:, wy[0]:wy[1], x0 - 2:x0 + 3] if t == "w" else blk[:, by[0]:by[1], x0 - 1:x0 + 2]
            colored = ((reg.max(-1) - reg.min(-1)) > args.sat) & (reg.max(-1) > 90)
            lit[:, j] = colored.mean((1, 2)) > 0.5
        rows.append(lit)
    proc.wait()
    if not rows:
        sys.exit("動画からフレームを読めませんでした")
    return np.concatenate(rows)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input")
    ap.add_argument("-o", "--output")
    ap.add_argument("--name")
    ap.add_argument("--start", type=float, default=0, help="この秒より前の音は捨てる")
    ap.add_argument("--end", type=float, default=0, help="この秒より後の音は捨てる(0 なら最後まで)")
    ap.add_argument("--max-poly", type=int, default=3, help="同時に鳴らす音の上限(標準 3)")
    ap.add_argument("--no-spread", action="store_true", help="あふれた音を隣の 0.1 秒へずらさず、省く")
    ap.add_argument("--row-length", type=int, default=0, help="回路を何マスで折り返すか(0 なら自動)")
    ap.add_argument("--speed", type=float, default=1.0)
    ap.add_argument("--transpose", type=int, default=0)
    ap.add_argument("--white-y", type=int, default=340, help="白鍵の色を見る縦位置(640x360 換算)")
    ap.add_argument("--black-y", type=int, default=300, help="黒鍵の色を見る縦位置")
    ap.add_argument("--sat", type=int, default=60, help="光っていると見なす色の濃さ")
    args = ap.parse_args()

    keys = layout()
    lit = lit_matrix(args.input, keys, args)
    nframes = lit.shape[0]
    rises = []  # (frame, midi)
    for j, (m, _, _) in enumerate(keys):
        col = lit[:, j]
        for f in np.where(col[1:] & ~col[:-1])[0] + 1:
            rises.append((int(f), m))
    rises = [(f, m) for f, m in rises
             if f / FPS >= args.start and (not args.end or f / FPS <= args.end)]
    if not rises:
        sys.exit("光っている鍵盤が見つかりません。--white-y / --black-y / --sat を調整してください")
    f0 = min(f for f, _ in rises)
    by_time = {}
    for f, m in rises:
        t = int(round((f - f0) / FPS / args.speed / songtools.TICK))
        by_time.setdefault(t, []).append((1.0, songtools.fold(m + args.transpose)))
    by_time, dropped, moved = songtools.limit_polyphony(by_time, args.max_poly, not args.no_spread)
    name = args.name or args.input.rsplit("/", 1)[-1].rsplit(".", 1)[0]
    out = args.output or name + ".json"
    count, seconds = songtools.write_json(out, name, by_time, args.row_length)
    songtools.report(out, count, seconds, dropped, moved)


if __name__ == "__main__":
    main()
