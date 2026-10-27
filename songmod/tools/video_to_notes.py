#!/usr/bin/env python3
"""ピアノロール動画(Synthesia 風。下に 88 鍵の鍵盤、弾いている鍵が色づく)を、songmod 用の音符 JSON に変換する。

鍵盤の光り方を 1 フレームずつ読むので、音声から推定するより音程・タイミングが正確。
    python3 video_to_notes.py 曲.mp4 -o 曲名.json --row-length 61

必要なもの: ffmpeg、pip install numpy
動画は 16:9、鍵盤が画面の下端(縦 280〜360/360 あたり)に 88 鍵(A0〜C8)ぴったり横幅いっぱい、という配置を想定。
鍵盤が拡大されている動画は --first-note / --x0 / --white-w で配置を教える(座標は 640x360 に縮めたときの値)。
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


WHITE_PCS = [0, 2, 4, 5, 7, 9, 11]  # C D E F G A B
NOTE_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
# 黒鍵の中心が、隣り合う白鍵の境目からどれだけずれているか(白鍵の幅に対する割合。実際の鍵盤の描き方)
BLACK_OFFSET = {1: -0.08, 3: 0.10, 6: -0.15, 8: 0.0, 10: 0.15}


def parse_note(text):
    """'F3' -> MIDI 53(C4 = 60)。白鍵の名前だけ"""
    text = text.strip().upper()
    if len(text) < 2 or text[0] not in NOTE_PC:
        sys.exit(f"音名が読めません: {text}(例: A0, F3)")
    return (int(text[1:]) + 1) * 12 + NOTE_PC[text[0]]


def layout(first_note="A0", x0=0.0, white_w=None):
    """[(MIDI, x, 'w' or 'b')]。first_note は画面の左端にある白鍵、x0 はその白鍵の左端の x、
    white_w は白鍵 1 本の幅(640 に縮めたときの px)。省略すると 88 鍵(A0〜C8)が横幅いっぱい。"""
    if white_w is None:
        white_w = W / 52
    midi = parse_note(first_note)
    wi = WHITE_PCS.index(midi % 12)
    keys = []
    i = 0
    while x0 + i * white_w < W:
        pc = WHITE_PCS[wi % 7]
        keys.append((midi, min(max(x0 + (i + 0.5) * white_w, 2), W - 3), "w"))
        step = 2 if pc not in (4, 11) else 1
        if step == 2:
            bx = x0 + (i + 1) * white_w + BLACK_OFFSET[(pc + 1) % 12] * white_w
            if bx < W - 1:
                keys.append((midi + 1, bx, "b"))
        midi += step
        wi += 1
        i += 1
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
            hw = max(1, int(args.white_w * 0.1)) if t == "w" else max(1, int(args.white_w * 0.08))
            reg = blk[:, wy[0]:wy[1], x0 - hw:x0 + hw + 1] if t == "w" else blk[:, by[0]:by[1], x0 - hw:x0 + hw + 1]
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
    ap.add_argument("--max-simul", type=int, default=10, help="1 フレームにこれより多くの鍵が一斉に光ったら誤検出として無視する")
    ap.add_argument("--no-spread", action="store_true", help="あふれた音を隣の 0.1 秒へずらさず、省く")
    ap.add_argument("--row-length", type=int, default=0, help="回路を何マスで折り返すか(0 なら自動)")
    ap.add_argument("--one-row", action="store_true", help="折り返さず 1 列にする(row_length を \"line\" にする)")
    ap.add_argument("--speed", type=float, default=1.0)
    ap.add_argument("--transpose", type=int, default=0)
    ap.add_argument("--first-note", default="A0", help="画面の左端にある白鍵の音名(例: F3)。88 鍵なら A0")
    ap.add_argument("--x0", type=float, default=0.0, help="その白鍵の左端の x(640 に縮めたとき。画面の外なら負)")
    ap.add_argument("--white-w", type=float, default=None, help="白鍵 1 本の幅(640 に縮めたとき)。省略なら 88 鍵が横幅いっぱい")
    ap.add_argument("--white-y", type=int, default=340, help="白鍵の色を見る縦位置(640x360 換算)")
    ap.add_argument("--black-y", type=int, default=300, help="黒鍵の色を見る縦位置")
    ap.add_argument("--sat", type=int, default=60, help="光っていると見なす色の濃さ")
    args = ap.parse_args()

    if args.white_w is None:
        args.white_w = W / 52
    keys = layout(args.first_note, args.x0, args.white_w)
    print(f"鍵盤: {len(keys)} 鍵 ({keys[0][0]}〜{keys[-1][0]})", file=sys.stderr)
    lit = lit_matrix(args.input, keys, args)
    nframes = lit.shape[0]
    rises = []  # (frame, midi)
    for j, (m, _, _) in enumerate(keys):
        col = lit[:, j]
        for f in np.where(col[1:] & ~col[:-1])[0] + 1:
            rises.append((int(f), m))
    # 1 フレームに大量の鍵が一斉に光るのは、画面の切り替えなどの誤検出(人の指は 10 本まで)
    per_frame = {}
    for f, _ in rises:
        per_frame[f] = per_frame.get(f, 0) + 1
    glitch = {f for f, n in per_frame.items() if n > args.max_simul}
    if glitch:
        print(f"画面の切り替えと見られる {len(glitch)} フレーム({sum(per_frame[f] for f in glitch)} 個の光)を無視しました", file=sys.stderr)
    rises = [(f, m) for f, m in rises
             if f not in glitch and f / FPS >= args.start and (not args.end or f / FPS <= args.end)]
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
    count, seconds = songtools.write_json(out, name, by_time, -1 if args.one_row else args.row_length)
    songtools.report(out, count, seconds, dropped, moved)


if __name__ == "__main__":
    main()
