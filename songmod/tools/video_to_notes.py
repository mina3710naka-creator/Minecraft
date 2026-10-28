#!/usr/bin/env python3
"""ピアノロール動画(Synthesia 風。下に鍵盤があり、弾いている鍵が色づく)を、songmod 用の音符 JSON に変換する。

鍵盤の光り方を 1 フレームずつ読むので、音声から推定するより音程・タイミングが正確。
鍵盤の位置・大きさは動画から自動で調べる(88 鍵の全体でも、拡大された一部分でもよい)。

    python3 video_to_notes.py 曲.mp4

動画と同じフォルダに、動画と同じ名前の 曲.json ができる。--one-row で折り返さず 1 列、--row-length 61 で 61 マスで折り返し。

必要なもの: ffmpeg、pip install numpy(音声でオクターブを確かめるなら basic-pitch も。無くても動く)
鍵盤の自動検知が合わないときは --first-note / --x0 / --white-w / --white-y / --black-y で指定できる(座標は 640x360 に縮めたときの値)。
"""
import argparse
import os
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
    while x0 + i * white_w < W - 0.3 * white_w:
        pc = WHITE_PCS[wi % 7]
        keys.append((midi, min(max(x0 + (i + 0.5) * white_w, 2), W - 3), "w"))
        step = 2 if pc not in (4, 11) else 1
        if step == 2:
            bx = x0 + (i + 1) * white_w + BLACK_OFFSET[(pc + 1) % 12] * white_w
            if bx < W - 0.3 * white_w:
                keys.append((midi + 1, bx, "b"))
        midi += step
        wi += 1
        i += 1
    return sorted(keys)


def read_frames(path, vf, size_per_frame):
    cmd = ["ffmpeg", "-loglevel", "error", "-i", path, "-an", "-vf", vf, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE)
    while True:
        buf = proc.stdout.read(size_per_frame * 300)
        if not buf:
            break
        yield buf[: len(buf) // size_per_frame * size_per_frame]
    proc.wait()


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                         capture_output=True, text=True).stdout.strip()
    try:
        return float(out)
    except ValueError:
        return 0.0


def detect_keyboard(path, verbose=True):
    """動画から鍵盤の位置を調べる。動画の画面を何枚か重ねて中央値をとり(動く音符や光った鍵を消す)、
    白鍵の境目の線・黒鍵の並びから、白鍵の幅・左端・左端の白鍵の音名の「ドレミ」の部分・縦位置を求める。
    返り値: dict(x0, white_w, white_y, black_y, first_pc_index, n_white) か、見つからなければ None"""
    dur = duration(path) or 60
    n = 40
    vf = f"fps={n / dur:.6f},scale={W}:{H}"
    frames = []
    for buf in read_frames(path, vf, W * H * 3):
        frames.append(np.frombuffer(buf, np.uint8).reshape(-1, H, W, 3))
    if not frames:
        return None
    med = np.median(np.concatenate(frames).astype(np.float32), axis=0)  # (H, W, 3)

    # 白鍵の領域: 画面の下半分で、ほぼ全幅がクリーム色〜白になる行(黒鍵より下の部分)
    bright = med.min(-1) > 170
    frac = bright.mean(1)
    ys = [y for y in range(H // 2, H - 8) if frac[y] > 0.85]
    if not ys:
        return None
    y_ws = ys[0]
    # 鍵盤の上端: そこから上へ、白鍵が見える(明るい)行が続くところまで
    y_top = y_ws
    while y_top > H // 3 and frac[y_top - 1] > 0.15:
        y_top -= 1
    white_y = min(y_ws + 3, H - 12)
    black_y = int(y_top + 0.35 * (y_ws - y_top))

    # 白鍵の境目(細い暗い縦線)
    prof = med[white_y:white_y + 7].mean((0, 2))
    thr = np.median(prof) - 18
    bounds = []
    x = 0
    while x < W:
        if prof[x] < thr:
            e = x
            while e + 1 < W and prof[e + 1] < thr:
                e += 1
            if e - x <= 4:
                bounds.append((x + e) / 2)
            x = e + 1
        else:
            x += 1
    if len(bounds) < 8:
        return None
    bounds = np.array(bounds)
    # 境目に順に番号をつけながら、幅を少しずつ精密にする(画素単位の誤差が溜まらないように)
    w = float(np.median(np.diff(bounds)))
    ks = [0]
    for i in range(1, len(bounds)):
        ks.append(ks[-1] + max(1, int(round((bounds[i] - bounds[i - 1]) / w))))
        if i >= 3:
            w = float(np.polyfit(ks, bounds[: i + 1], 1)[0])
    k = np.array(ks, float)
    w, a0 = np.polyfit(k, bounds, 1)
    if np.mean(np.abs(a0 + w * k - bounds) < 1.5) < 0.8 or not 6 < w < 60:
        return None
    r = a0 % w
    x0 = r if r < 2 else (r - w if r - w < -2 else 0.0)  # 左端のごく細い欠片は無いものとする
    n_white = int(np.ceil((W - x0) / w - 0.05))

    # 黒鍵: 黒鍵の高さの行で、白鍵幅の 3〜9 割の暗い塊
    row = med[black_y - 1:black_y + 2].max(-1).mean(0)
    dark = row < 90
    centers = []
    x = 0
    while x < W:
        if dark[x]:
            e = x
            while e + 1 < W and dark[e + 1]:
                e += 1
            if 0.3 * w <= e - x + 1 <= 0.9 * w:
                centers.append((x + e) / 2)
            x = e + 1
        else:
            x += 1
    has_black = {int(round((c - x0) / w)) for c in centers}  # 白鍵 j-1 と j の境目に黒鍵がある
    gaps = [j for j in range(1, n_white) if j not in has_black and x0 + j * w < W - 0.5 * w]
    if n_white == 52 and abs(x0) < 1.5:
        idx0 = 5  # A(WHITE_PCS の A の位置)。88 鍵
    else:
        idx0 = None
        for g, g2 in zip(gaps, gaps[1:]):
            if g2 - g == 4:      # E と F の間 → 右の白鍵は F
                idx0 = (WHITE_PCS.index(5) - g) % 7
                break
            if g2 - g == 3:      # B と C の間 → 右の白鍵は C
                idx0 = (WHITE_PCS.index(0) - g) % 7
                break
        if idx0 is None:
            return None
    if verbose:
        print(f"鍵盤を検知: 白鍵 {n_white} 本(幅 {w:.2f}px、左端 x={x0:.1f})、縦位置 白鍵 y={white_y} 黒鍵 y={black_y}", file=sys.stderr)
    return {"x0": float(x0), "white_w": float(w), "white_y": white_y, "black_y": black_y,
            "first_pc": WHITE_PCS[idx0], "n_white": n_white}


def lit_matrix(path, keys, args):
    crop_y = min(args.white_y, args.black_y) // 2 * 2 - 2
    crop_h = (H - crop_y) // 2 * 2 - 2
    wy = (args.white_y - crop_y, args.white_y - crop_y + 7)   # 白鍵の下のほう(黒鍵がない所)
    by = (args.black_y - crop_y, args.black_y - crop_y + 7)   # 黒鍵の真ん中あたり
    rows = []
    for buf in read_frames(path, f"fps={FPS},scale={W}:{H},crop={W}:{crop_h}:0:{crop_y}", W * crop_h * 3):
        blk = np.frombuffer(buf, np.uint8).reshape(-1, crop_h, W, 3).astype(np.int16)
        lit = np.zeros((blk.shape[0], len(keys)), bool)
        for j, (_, x, t) in enumerate(keys):
            x0 = int(round(x))
            hw = max(2, int(args.white_w * 0.12)) if t == "w" else max(1, int(args.white_w * 0.08))
            reg = blk[:, wy[0]:wy[1], x0 - hw:x0 + hw + 1] if t == "w" else blk[:, by[0]:by[1], x0 - hw:x0 + hw + 1]
            colored = ((reg.max(-1) - reg.min(-1)) > args.sat) & (reg.max(-1) > 90)
            lit[:, j] = colored.mean((1, 2)) > 0.5
        rows.append(lit)
    if not rows:
        sys.exit("動画からフレームを読めませんでした")
    return np.concatenate(rows)


def guess_first_note(first_pc, x0, white_w, n_white):
    """左端の白鍵の音名。88 鍵(A0 から)ならそれ。そうでなければ、鍵盤の中央が E4 の近くになるオクターブを仮に選ぶ"""
    letter = {v: k for k, v in NOTE_PC.items()}[first_pc]
    if n_white == 52 and first_pc == 9 and abs(x0) < 1.5:
        return "A0"
    best = None
    for octave in range(0, 7):
        keys = layout(f"{letter}{octave}", x0, white_w)
        center = (keys[0][0] + keys[-1][0]) / 2
        if best is None or abs(center - 64) < best[0]:
            best = (abs(center - 64), f"{letter}{octave}")
    return best[1]


def octave_shift_from_audio(path, rises, f0):
    """音声の音程推定(basic-pitch)と見比べて、映像から読んだ音が何オクターブずれているかを調べる。
    返り値: 半音数(0, ±12, ±24)。basic-pitch が無い・判断できないときは None"""
    try:
        import types
        import audio_to_notes
        args = types.SimpleNamespace(start=0, length=0, onset=0.5, frame=0.3, min_len=0.06)
        ev = audio_to_notes.events_from_audio(path, args)
    except Exception as e:  # noqa: BLE001  (未インストール・失敗はどれも「確認しない」でよい)
        print(f"(音声での確認はしません: {type(e).__name__})", file=sys.stderr)
        return None
    audio = {}
    for st, _, p, _ in ev:
        audio.setdefault(p, set()).add(int(round(st / songtools.TICK)))
    vid = [(int(round((f - f0) / FPS / songtools.TICK)), m) for f, m in rises]
    best = None
    for shift in (-24, -12, 0, 12, 24):
        for off in range(-600, 601):
            c = sum(1 for t, m in vid if any((t + off + d) in audio.get(m + shift, ()) for d in (-1, 0, 1)))
            if best is None or c > best[0]:
                best = (c, shift, off)
    base = max(sum(1 for t, m in vid if any((t + off + d) in audio.get(m, ()) for d in (-1, 0, 1)))
               for off in range(-600, 601))
    if best[0] < 0.25 * len(vid) or best[0] < 1.15 * max(base, 1):
        return 0 if base >= 0.25 * len(vid) else None
    return best[1]


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input")
    ap.add_argument("-o", "--output", help="出力 JSON(省略すると、動画と同じフォルダに、動画と同じ名前で作る)")
    ap.add_argument("--name", help="曲名(省略すると動画のファイル名)")
    ap.add_argument("--start", type=float, default=0, help="この秒より前の音は捨てる")
    ap.add_argument("--end", type=float, default=0, help="この秒より後の音は捨てる(0 なら最後まで)")
    ap.add_argument("--max-poly", type=int, default=3, help="同時に鳴らす音の上限(標準 3)")
    ap.add_argument("--max-simul", type=int, default=10, help="1 フレームにこれより多くの鍵が一斉に光ったら誤検出として無視する")
    ap.add_argument("--no-spread", action="store_true", help="あふれた音を隣の 0.1 秒へずらさず、省く")
    ap.add_argument("--row-length", type=int, default=0, help="回路を何マスで折り返すか(0 なら自動)")
    ap.add_argument("--one-row", action="store_true", help="折り返さず 1 列にする(row_length を \"line\" にする)")
    ap.add_argument("--speed", type=float, default=1.0)
    ap.add_argument("--transpose", type=int, default=0)
    ap.add_argument("--no-audio-check", action="store_true", help="音声でオクターブを確かめない")
    ap.add_argument("--first-note", help="(自動検知の上書き)画面の左端にある白鍵の音名(例: F2)")
    ap.add_argument("--x0", type=float, help="(上書き)その白鍵の左端の x(640 に縮めたとき。画面の外なら負)")
    ap.add_argument("--white-w", type=float, help="(上書き)白鍵 1 本の幅(640 に縮めたとき)")
    ap.add_argument("--white-y", type=int, help="(上書き)白鍵の色を見る縦位置(640x360 換算)")
    ap.add_argument("--black-y", type=int, help="(上書き)黒鍵の色を見る縦位置")
    ap.add_argument("--sat", type=int, default=60, help="光っていると見なす色の濃さ")
    args = ap.parse_args()

    manual = args.first_note and args.x0 is not None and args.white_w and args.white_y and args.black_y
    octave_known = bool(args.first_note)
    if not manual:
        det = detect_keyboard(args.input)
        if det is None:
            print("鍵盤を自動で見つけられなかったので、88 鍵が横幅いっぱいの標準配置として読みます", file=sys.stderr)
            det = {"x0": 0.0, "white_w": W / 52, "white_y": 340, "black_y": 300, "first_pc": 9, "n_white": 52}
        if args.x0 is None:
            args.x0 = det["x0"]
        args.white_w = args.white_w or det["white_w"]
        args.white_y = args.white_y or det["white_y"]
        args.black_y = args.black_y or det["black_y"]
        if not args.first_note:
            args.first_note = guess_first_note(det["first_pc"], args.x0, args.white_w, det["n_white"])
            octave_known = det["n_white"] == 52 and args.first_note == "A0"
    keys = layout(args.first_note, args.x0, args.white_w)
    print(f"鍵盤: {len(keys)} 鍵 (MIDI {keys[0][0]}〜{keys[-1][0]}、左端 {args.first_note})", file=sys.stderr)
    lit = lit_matrix(args.input, keys, args)
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
        sys.exit("光っている鍵盤が見つかりません。鍵盤の位置が合っていないかもしれません(--first-note / --x0 / --white-w / --white-y / --black-y で指定できます)")
    f0 = min(f for f, _ in rises)
    if not octave_known and not args.no_audio_check:
        shift = octave_shift_from_audio(args.input, rises, f0)
        if shift is None:
            print("オクターブは推測です。違っていたら --first-note で左端の白鍵の音名(例: F2)を指定してください", file=sys.stderr)
        elif shift:
            print(f"音声と照らして、オクターブを {shift:+d} 半音直しました", file=sys.stderr)
            rises = [(f, m + shift) for f, m in rises]
        else:
            print("音声と照らして、オクターブは合っていました", file=sys.stderr)
    by_time = {}
    for f, m in rises:
        t = int(round((f - f0) / FPS / args.speed / songtools.TICK))
        by_time.setdefault(t, []).append((1.0, songtools.fold(m + args.transpose)))
    by_time, dropped, moved = songtools.limit_polyphony(by_time, args.max_poly, not args.no_spread)
    stem = os.path.splitext(os.path.basename(args.input))[0]
    name = args.name or stem
    out = args.output or os.path.join(os.path.dirname(os.path.abspath(args.input)), stem + ".json")
    count, seconds = songtools.write_json(out, name, by_time, -1 if args.one_row else args.row_length)
    songtools.report(out, count, seconds, dropped, moved)


if __name__ == "__main__":
    main()
