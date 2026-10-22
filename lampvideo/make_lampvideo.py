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
# ランプ自体は置きっぱなしにして、背面(~ ~ ~-1)のブロックを切り替えて点灯/消灯する。
# redstone_lamp[lit=true] を直接置くと、無電力のランプは隣の更新で4tick後に勝手に消えてしまうため。
LIT = "redstone_block"
UNLIT = "black_concrete"


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


def read_frames(path, w, h, fps, threshold, dither, hyst=0):
    vf = f"fps={fps},scale={w}:{h}:flags=area,format=gray"
    p = subprocess.Popen(
        ["ffmpeg", "-v", "error", "-i", path, "-vf", vf, "-f", "rawvideo", "-"],
        stdout=subprocess.PIPE)
    size = w * h
    state = None
    while True:
        buf = p.stdout.read(size)
        if len(buf) < size:
            break
        a = np.frombuffer(buf, dtype=np.uint8).reshape(h, w)
        if dither:
            bits = np.array(Image.fromarray(a, "L").convert("1")) # Floyd-Steinberg
        elif hyst and state is not None:
            # 閾値付近のちらつきでランプが切り替わらないよう、十分変化したときだけ反転する
            bits = np.where(a >= threshold + hyst, True, np.where(a < threshold - hyst, False, state))
        else:
            bits = a >= threshold
        state = bits
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


def rects(m):
    """True の画素を長方形に分割する。横の連続区間を作り、同じ区間が縦に続く行を1つにまとめる。
    戻り値は (x1, x2, y0, y1) のリスト。"""
    out = []
    open_ = {}  # (x1, x2) -> 縦に伸びている区間の開始行
    h = m.shape[0]
    for y in range(h + 1):
        now = set(runs(m[y])) if y < h else set()
        for key in list(open_):
            if key not in now:
                out.append((key[0], key[1], open_.pop(key), y - 1))
        for key in now:
            open_.setdefault(key, y)
    return out


def best_rects(m):
    """横方向優先と縦方向優先の分割を比べて、長方形が少ない方を使う(結果の画面は同じ)"""
    hr = rects(m)
    vr = [(y0, y1, x1, x2) for x1, x2, y0, y1 in rects(m.T)]
    return hr if len(hr) <= len(vr) else vr


def frame_commands(prev, cur, h):
    """前フレームとの差分だけを fill で書き換える。座標は画面の左下が原点。"""
    lines = []
    diff = np.ones_like(cur, dtype=bool) if prev is None else (prev != cur)
    # 暗い画素(False)を点灯、明るい画素(True)を消灯にする
    for state, block in ((True, UNLIT), (False, LIT)):
        for x1, x2, y0, y1 in best_rects(diff & (cur == state)):
            lines.extend(fmt_fill((x1, x2), y0, y1, h, block, off=block == UNLIT))
    return lines


def fmt_fill(key, y0, y1, h, block, off):
    # 画像の上端 = 画面の上。y0..y1 は画像の行番号。
    # fill は1回32768ブロックまでなので、大きい長方形は行方向に分割する
    step = max(1, 32768 // (key[1] - key[0] + 1))
    out = []
    for a in range(y0, y1 + 1, step):
        b = min(y1, a + step - 1)
        lo, hi = h - 1 - b, h - 1 - a
        out.append(f"fill ~{key[0]} ~{lo} ~-1 ~{key[1]} ~{hi} ~-1 {block}")
        if off:
            # 背面だけ外すとランプが消えるまで4tickかかり、残像になる。ランプ自体も直接消灯して即座に反映する
            out.append(f"fill ~{key[0]} ~{lo} ~ ~{key[1]} ~{hi} ~ redstone_lamp[lit=false]")
    return out


def setup_fills(w, h):
    # fill は1回32768ブロックまで。行ごとにまとめて分割する
    step = max(1, 32768 // w // 2)
    out = ""
    for y in range(0, h, step):
        y2 = min(h - 1, y + step - 1)
        out += f"fill ~ ~{y} ~-1 ~{w-1} ~{y2} ~-1 {UNLIT}\n"
        out += f"fill ~ ~{y} ~ ~{w-1} ~{y2} ~ redstone_lamp[lit=false]\n"
    return out


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
    ap.add_argument("--hysteresis", type=int, default=24,
                    help="白黒の切り替えにヒステリシスを付けて、ちらつきによる書き換えを減らす(0で無効)")
    ap.add_argument("--hold", type=int, default=1,
                    help="全フレームをそれぞれNtick表示する(2なら1tickあたりの負荷が半分。再生速度と音声もN分の1になる)")
    ap.add_argument("--keyframe", type=int, default=100,
                    help="Nフレームで全面を1周描き直す(帯状に少しずつ。0で無効)")
    ap.add_argument("--zip", action="store_true", help="データパックをzipにする(容量が1/10以下)")
    ap.add_argument("--pack-format", type=int, default=121)
    args = ap.parse_args()

    iw, ih, src_fps = probe(args.input)
    w = args.width
    h = args.height or max(1, round(w * ih / iw))
    fps = src_fps if args.speed == "slow" else 20.0
    # slow: 1フレーム=1tick。映像は 20/src_fps 倍速 (30fps → 2/3)
    rate = 20.0 / (fps * args.hold)
    print(f"{iw}x{ih} {src_fps:.3f}fps -> {w}x{h} ランプ, {fps:.3f}fps抽出, 再生速度 x{rate:.4f}")

    dp = os.path.join(args.out, "lampvideo_dp")
    rp = os.path.join(args.out, "lampvideo_rp")
    shutil.rmtree(dp, ignore_errors=True)
    shutil.rmtree(rp, ignore_errors=True)

    BANDS = 10
    band_step = max(1, args.keyframe // BANDS)
    prev, n, worst = None, 0, 0
    for cur in read_frames(args.input, w, h, fps, args.threshold, args.dither, args.hysteresis):
        if n == 0:
            prev = None
        elif args.keyframe and n % band_step == 0:
            # 全面を帯状に分けて少しずつ描き直す(全面を一度にやると1tickの負荷が跳ね上がる)。
            # ロード外などで書き込みに失敗しても自己修復する
            b = (n // band_step) % BANDS
            prev = prev.copy()
            prev[b * h // BANDS:(b + 1) * h // BANDS] = ~cur[b * h // BANDS:(b + 1) * h // BANDS]
        cmds = frame_commands(prev, cur, h)
        worst = max(worst, len(cmds))
        write(f"{dp}/data/{NS}/function/f/{n}.mcfunction",
              "\n".join(cmds) + ("\n" if cmds else "# (変化なし)\n"))
        prev, n = cur, n + 1
        if args.max_frames and n >= args.max_frames:
            break
    if n == 0:
        sys.exit("フレームを読み込めませんでした")
    print(f"{n} フレーム生成 (1フレーム最大 {worst} コマンド, {n*args.hold/20:.1f}秒)")
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
          # 画面の上端がワールドの高さ上限(Y=319)を超えると、上の部分が描けず映像が欠ける
          "execute store result score #y lv run data get entity @s Pos[1]\n"
          f"execute if score #y lv matches {320 - h + 1}.. run tellraw @s [{{\"text\":\"[ランプ動画] 高さ不足です。画面の高さが{h}ブロックあるので、Y={320 - h}以下の場所で実行してください(スーパーフラットの地面など)\",\"color\":\"red\"}}]\n"
          f"execute if score #y lv matches {320 - h + 1}.. run return 0\n"
          f"kill @e[type=marker,tag={NS}]\n"
          f"summon marker ~ ~ ~ {{Tags:[\"{NS}\"]}}\n"
          # 画面が読み込み範囲外だと fill が失敗して映像が欠けるので、画面の範囲を常時読み込みにする
          f"forceload add ~ ~-1 ~{w-1} ~\n"
          f"schedule function {NS}:draw 40t\n"
          f"tellraw @s [{{\"text\":\"[ランプ動画] 画面を準備中... 2秒後に設置します\",\"color\":\"green\"}}]\n")
    write(f"{f}/draw.mcfunction",
          f"execute as @e[type=marker,tag={NS},limit=1] at @s run function {NS}:draw_do\n"
          f"tellraw @a [{{\"text\":\"[ランプ動画] 画面を設置しました。南側から北を向いて見てください。/function {NS}:play で再生\",\"color\":\"green\"}}]\n")
    write(f"{f}/draw_do.mcfunction", setup_fills(w, h))
    write(f"{f}/remove.mcfunction",
          "scoreboard players set #run lv 0\n"
          f"execute as @e[type=marker,tag={NS},limit=1] at @s run forceload remove ~ ~-1 ~{w-1} ~\n"
          f"kill @e[type=marker,tag={NS}]\n")
    audio = has_audio(args.input)
    write(f"{f}/play.mcfunction",
          f"execute unless entity @e[type=marker,tag={NS}] run tellraw @s \"先に /function {NS}:setup を実行してください\"\n"
          f"execute unless entity @e[type=marker,tag={NS}] run return 0\n"
          "scoreboard players set #d lv 0\n"
          "scoreboard players set #c lv 0\n"
          "scoreboard players set #run lv 1\n"
          + (f"execute at @s run playsound {NS}:movie master @s ~ ~ ~ 1 1\n" if audio else ""))
    write(f"{f}/stop.mcfunction",
          "scoreboard players set #run lv 0\n"
          + (f"stopsound @a * {NS}:movie\n" if audio else ""))
    write(f"{f}/tick.mcfunction",
          "execute if score #run lv matches 1 run function " + f"{NS}:next\n")
    r1 = "execute if score #run lv matches 1 "
    write(f"{f}/next.mcfunction",
          f"execute if score #d lv matches {n}.. run function {NS}:stop\n"
          f"{r1}if score #c lv matches 0 store result storage {NS}:s n int 1 run scoreboard players get #d lv\n"
          f"{r1}if score #c lv matches 0 run function {NS}:step with storage {NS}:s\n"
          f"{r1}if score #c lv matches 0 run scoreboard players add #d lv 1\n"
          f"{r1}run scoreboard players add #c lv 1\n"
          f"{r1}if score #c lv matches {args.hold}.. run scoreboard players set #c lv 0\n")
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
    if args.zip:
        z = shutil.make_archive(dp, "zip", dp)
        shutil.rmtree(dp)
        print(f"zip: {z}")
    print(f"完了: {dp}")


if __name__ == "__main__":
    main()
