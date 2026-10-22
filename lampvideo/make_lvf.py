#!/usr/bin/env python3
"""動画(mp4等) -> lampvideo MOD 用の動画ファイル(.lvf) と、音声・テクスチャ用リソースパックを作る。

使い方:
    python3 make_lvf.py input.mp4 --out out --name myvideo --width 240 --hold 3

出力:
    out/myvideo.lvf     .minecraft/lampvideo/ に置く
    out/lampvideo_rp/   resourcepacks に置く(音声 movie.ogg とランプの単色テクスチャ)

画素の向き: 映像の暗い部分が点灯、明るい部分が消灯。全フレームを使い、各フレームを --hold tick ずつ表示する。
.lvf の形式は MOD 側の LvfVideo.java を参照。
"""
import argparse
import gzip
import json
import os
import shutil
import struct
import subprocess
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_lampvideo import best_rects, has_audio, probe, read_frames, write  # noqa: E402


def pack_rects(rects):
    arr = np.array(rects, dtype=">u2").reshape(-1, 4) if rects else np.zeros((0, 4), dtype=">u2")
    return struct.pack(">i", len(arr)) + arr.tobytes()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input")
    ap.add_argument("--out", default="out")
    ap.add_argument("--name", default="video")
    ap.add_argument("--width", type=int, default=240, help="ランプの横の数")
    ap.add_argument("--height", type=int, help="ランプの縦の数(省略時は縦横比から)")
    ap.add_argument("--hold", type=int, default=3, help="全フレームをそれぞれNtick表示する(再生速度と音声もN分の1)")
    ap.add_argument("--threshold", type=int, default=110, help="白黒の閾値(0-255)")
    ap.add_argument("--hysteresis", type=int, default=24, help="閾値付近のちらつきを減らす(0で無効)")
    ap.add_argument("--keyframe", type=int, default=100,
                    help="Nフレームで全面を1周描き直す(帯状に少しずつ。0で無効)")
    ap.add_argument("--max-frames", type=int, default=0, help="テスト用の上限")
    ap.add_argument("--pack-format", type=int, default=121, help="リソースパックの形式番号")
    args = ap.parse_args()

    iw, ih, fps = probe(args.input)
    w = args.width
    h = args.height or max(1, round(w * ih / iw))
    if w > 65535 or h > 65535 or args.hold > 255:
        sys.exit("幅・高さ・holdが大きすぎます")
    rate = 20.0 / (fps * args.hold)
    print(f"{iw}x{ih} {fps:.3f}fps -> {w}x{h} ランプ, 再生速度 x{rate:.4f}")

    BANDS = 10
    band_step = max(1, args.keyframe // BANDS)
    prev, n, worst = None, 0, 0
    chunks = []
    for cur in read_frames(args.input, w, h, fps, args.threshold, False, args.hysteresis):
        if n == 0:
            prev = None
        elif args.keyframe and n % band_step == 0:
            # 全面を帯状に分けて少しずつ描き直す。書き込み失敗があっても自己修復する
            b = (n // band_step) % BANDS
            prev = prev.copy()
            prev[b * h // BANDS:(b + 1) * h // BANDS] = ~cur[b * h // BANDS:(b + 1) * h // BANDS]
        diff = np.ones_like(cur, dtype=bool) if prev is None else (prev != cur)
        on = best_rects(diff & ~cur)   # 暗い画素 -> 点灯
        off = best_rects(diff & cur)   # 明るい画素 -> 消灯
        worst = max(worst, sum((x2 - x1 + 1) * (y1 - y0 + 1) for x1, x2, y0, y1 in on + off))
        chunks.append(pack_rects(on) + pack_rects(off))
        prev, n = cur, n + 1
        if args.max_frames and n >= args.max_frames:
            break
    if n == 0:
        sys.exit("フレームを読み込めませんでした")

    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, f"{args.name}.lvf")
    with gzip.GzipFile(path, "wb", compresslevel=9, mtime=0) as f:
        f.write(struct.pack(">4sHHBif", b"LVF2", w, h, args.hold, n, fps))
        for c in chunks:
            f.write(c)
    print(f"{n} フレーム ({n * args.hold / 20:.1f}秒), 1フレーム最大 {worst} ランプ, {os.path.getsize(path) / 1e6:.1f}MB: {path}")

    # --- リソースパック(音声とランプの単色テクスチャ) ---
    rp = os.path.join(args.out, "lampvideo_rp")
    shutil.rmtree(rp, ignore_errors=True)
    if has_audio(args.input):
        os.makedirs(f"{rp}/assets/lampvideo/sounds", exist_ok=True)
        # 映像が rate 倍速なので、音声も同じ倍率に伸縮する
        flt, r = [], rate
        while r < 0.5:
            flt.append("atempo=0.5"); r /= 0.5
        while r > 2.0:
            flt.append("atempo=2.0"); r /= 2.0
        flt.append(f"atempo={r:.6f}")
        cmd = ["ffmpeg", "-v", "error", "-y", "-i", args.input]
        if args.max_frames:
            cmd += ["-t", f"{args.max_frames / fps:.4f}"]
        subprocess.run(cmd + ["-vn", "-ac", "1", "-af", ",".join(flt), "-c:a", "libvorbis",
                              f"{rp}/assets/lampvideo/sounds/movie.ogg"], check=True)
        write(f"{rp}/assets/lampvideo/sounds.json", json.dumps(
            {"movie": {"sounds": [{"name": "lampvideo:movie", "stream": True}]}}, indent=2))
    # ランプの格子模様を単色にして、離れて見たときのモアレを防ぐ
    os.makedirs(f"{rp}/assets/minecraft/textures/block", exist_ok=True)
    for name, color in (("redstone_lamp", (40, 18, 12)), ("redstone_lamp_on", (255, 232, 150))):
        Image.new("RGB", (16, 16), color).save(f"{rp}/assets/minecraft/textures/block/{name}.png")
    write(f"{rp}/pack.mcmeta", json.dumps({"pack": {
        "description": "ランプ動画の音声とランプの単色テクスチャ",
        "min_format": args.pack_format, "max_format": args.pack_format}}, ensure_ascii=False, indent=2))
    print(f"リソースパック: {rp}")


if __name__ == "__main__":
    main()
