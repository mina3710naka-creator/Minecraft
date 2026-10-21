#!/usr/bin/env python3
"""動画(mp4等) -> レッドストーンランプ(点灯/消灯)のスクリーンを描くデータパックを生成する。

使い方:
    python3 make_lampvideo.py input.mp4 --out ./out --width 64

出力:
    out/lampvideo_dp/   データパック (datapacks フォルダへ)
    out/lampvideo_rp/   音声用リソースパック (音声がある場合。resourcepacks フォルダへ)
"""
import argparse
import json
import os
import shutil
import subprocess
import sys

import numpy as np
from PIL import Image

NS = "lampvideo"
LIT = "redstone_lamp[lit=true]"
UNLIT = "redstone_lamp[lit=false]"


def probe(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0",
         "-show_entries", "stream=width,height,r_frame_rate", "-of", "json", path],
        check=True, capture_output=True, text=True).stdout
    s = json.loads(out)["streams"][0]
    n, d = s["r_frame_rate"].split("/")
    return int(s["width"]), int(s["height"]), float(n) / float(d)


def has_audio(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries",
         "stream=codec_type", "-of", "csv=p=0", path],
        capture_output=True, text=True).stdout
    return "audio" in out


def read_frames(path, w, h, fps, threshold, dither):
    vf = f"fps={fps},scale={w}:{h}:flags=area,format=gray"
    p = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-i", path, "-vf", vf, "-f", "rawvideo", "-"],
        stdout=subprocess.PIPE)
    size = w * h
    while True:
        buf = p.stdout.read(size)
        if len(buf) < size:
            break
        a = np.frombuffer(buf, dtype=np.uint8).reshape(h, w)
        if dither:
            bits = np.array(Image.fromarray(a, "L").convert("1")) # Floyd-Steinberg
        else:
            bits = a >= threshold
        yield bits
    p.wait()


def runs(mask_row):
    """True が連続する区間 (x1, x2) を列挙"""
    x, w = 0, len(mask_row)
    while x < w:
        if mask_row[x]:
            s = x
            while x + 1 < w and mask_row[x + 1]:
                x += 1
            yield s, x
        x += 1


def frame_commands(prev, cur, h):
    """前フレームとの差分だけを fill で書き換える。座標は画面の左下が原点。"""
    lines = []
    diff = np.ones_like(cur, dtype=bool) if prev is None else (prev != cur)
    for state, block in ((True, LIT), (False, UNLIT)):
        m = diff & (cur == state)
        for y in range(h):
            for x1, x2 in runs(m[y]):
                yy = h - 1 - y  # 画像の上端 = 画面の上
                lines.append(f"fill ~{x1} ~{yy} ~ ~{x2} ~{yy} ~ {block}")
    return lines


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("input")
    ap.add_argument("--out", default="out")
    ap.add_argument("--width", type=int, default=64, help="ランプの横の数")
    ap.add_argument("--height", type=int, help="ランプの縦の数(省略時は縦横比から)")
    ap.add_argument("--speed", choices=["slow", "normal"], default="slow",
                    help="slow: 元fpsの全フレームを1tickに1枚で再生(30fpsなら2/3倍速)。"
                         " normal: 20fpsに間引いて等速")
    ap.add_argument("--threshold", type=int, default=110, help="白黒の閾値(0-255)")
    ap.add_argument("--dither", action="store_true", help="ディザリング(点滅は増える)")
    ap.add_argument("--max-frames", type=int, default=0, help="テスト用の上限")
    ap.add_argument("--pack-format", type=int, default=121)
    args = ap.parse_args()

    iw, ih, src_fps = probe(args.input)
    w = args.width
    h = args.height or max(1, round(w * ih / iw))
    fps = src_fps if args.speed == "slow" else 20.0
    # slow: 1フレーム=1tick。映像は 20/src_fps 倍速 (30fps → 2/3)
    rate = 20.0 / fps
    print(f"{iw}x{ih} {src_fps:.3f}fps -> {w}x{h} ランプ, {fps:.3f}fps抽出, 再生速度 x{rate:.4f}")

    dp = os.path.join(args.out, "lampvideo_dp")
    rp = os.path.join(args.out, "lampvideo_rp")
    shutil.rmtree(dp, ignore_errors=True)
    shutil.rmtree(rp, ignore_errors=True)

    prev, n, worst = None, 0, 0
    for cur in read_frames(args.input, w, h, fps, args.threshold, args.dither):
        cmds = frame_commands(prev, cur, h)
        worst = max(worst, len(cmds))
        write(f"{dp}/data/{NS}/function/f/{n}.mcfunction",
              "\n".join(cmds) + ("\n" if cmds else "# (変化なし)\n"))
        prev, n = cur, n + 1
        if args.max_frames and n >= args.max_frames:
            break
    if n == 0:
        sys.exit("フレームを読み込めませんでした")
    print(f"{n} フレーム生成 (1フレーム最大 {worst} コマンド, {n/20:.1f}秒)")
    if worst > 30000:
        print("警告: 1tickのコマンド数が多すぎます。--width を下げてください", file=sys.stderr)

    f = f"{dp}/data/{NS}/function"
    write(f"{dp}/pack.mcmeta", json.dumps({"pack": {
        "description": "§eレッドストーンランプ動画§r (Lamp Video)",
        "min_format": args.pack_format, "max_format": args.pack_format}}, ensure_ascii=False, indent=2))
    write(f"{dp}/data/minecraft/tags/function/load.json", json.dumps({"values": [f"{NS}:load"]}, indent=2))
    write(f"{dp}/data/minecraft/tags/function/tick.json", json.dumps({"values": [f"{NS}:tick"]}, indent=2))
    write(f"{f}/load.mcfunction",
          "scoreboard objectives add lv dummy\n"
          "scoreboard players set #run lv 0\n"
          f"tellraw @a [{{\"text\":\"[ランプ動画] 読み込み完了 ({w}x{h}, {n}フレーム)\",\"color\":\"green\"}}]\n")
    # 実行位置を画面の左下角にする。画面は +X(右)と +Y(上)に広がり、
    # 南側(+Z)に立って北を向くと正しい向きに見える。
    write(f"{f}/setup.mcfunction",
          f"kill @e[type=marker,tag={NS}]\n"
          f"summon marker ~ ~ ~ {{Tags:[\"{NS}\"]}}\n"
          f"fill ~ ~ ~-1 ~{w-1} ~{h-1} ~-1 black_concrete\n"
          f"fill ~ ~ ~ ~{w-1} ~{h-1} ~ {UNLIT}\n"
          f"tellraw @s [{{\"text\":\"[ランプ動画] 画面を設置しました。南側から北を向いて見てください。/function {NS}:play で再生\",\"color\":\"green\"}}]\n")
    audio = has_audio(args.input)
    write(f"{f}/play.mcfunction",
          f"execute unless entity @e[type=marker,tag={NS}] run tellraw @s \"先に /function {NS}:setup を実行してください\"\n"
          f"execute unless entity @e[type=marker,tag={NS}] run return 0\n"
          "scoreboard players set #f lv 0\n"
          "scoreboard players set #run lv 1\n"
          + (f"execute at @s run playsound {NS}:movie master @s ~ ~ ~ 1 1\n" if audio else ""))
    write(f"{f}/stop.mcfunction",
          "scoreboard players set #run lv 0\n"
          + (f"stopsound @a * {NS}:movie\n" if audio else ""))
    write(f"{f}/tick.mcfunction",
          "execute if score #run lv matches 1 run function " + f"{NS}:next\n")
    write(f"{f}/next.mcfunction",
          f"execute if score #f lv matches {n}.. run function {NS}:stop\n"
          f"execute if score #run lv matches 1 store result storage {NS}:s n int 1 run scoreboard players get #f lv\n"
          f"execute if score #run lv matches 1 run function {NS}:step with storage {NS}:s\n"
          "execute if score #run lv matches 1 run scoreboard players add #f lv 1\n")
    write(f"{f}/step.mcfunction",
          f"$execute as @e[type=marker,tag={NS},limit=1] at @s run function {NS}:f/$(n)\n")

    if audio:
        os.makedirs(f"{rp}/assets/{NS}/sounds", exist_ok=True)
        # 映像が 20/fps 倍速なので、音声も同じ倍率に伸縮する
        flt = []
        r = rate
        while r < 0.5:
            flt.append("atempo=0.5"); r /= 0.5
        while r > 2.0:
            flt.append("atempo=2.0"); r /= 2.0
        flt.append(f"atempo={r:.6f}")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", args.input, "-vn", "-ac", "1",
                        "-af", ",".join(flt), "-c:a", "libvorbis",
                        f"{rp}/assets/{NS}/sounds/movie.ogg"], check=True)
        write(f"{rp}/assets/{NS}/sounds.json", json.dumps(
            {"movie": {"sounds": [{"name": f"{NS}:movie", "stream": True}]}}, indent=2))
        write(f"{rp}/pack.mcmeta", json.dumps({"pack": {
            "description": "ランプ動画の音声",
            "min_format": args.pack_format, "max_format": args.pack_format}}, indent=2))
        print("音声リソースパックを生成しました")
    print(f"完了: {dp}")


if __name__ == "__main__":
    main()
