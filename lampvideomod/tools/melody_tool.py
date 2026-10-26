#!/usr/bin/env python3
"""
lampvideomod 用: mp3 / mp4 などの曲から「主旋律(歌うときの音程)」を取り出して、
Mod が読み込む「<曲の名前>.melody.json」を作る。

使い方:
    python melody_tool.py 曲.mp3                 # 同じ場所に 曲.melody.json を作る
    python melody_tool.py 曲.mp4 -o ~/.minecraft/lampvideo
    python melody_tool.py 曲.mp3 --separate      # (demucs が入っていれば)先に歌声だけを取り出す

できた .melody.json を、.minecraft/lampvideo フォルダ(または lampvideo/media)に入れる。
ゲームで同じ名前の曲を再生すると、音符ブロック/効果音のスピーカーは、この旋律を鳴らす。

必要なもの: Python 3.8+, numpy, ffmpeg(PATH に通っていること)。
  --separate を使うなら、さらに demucs(pip install demucs)。
注意: 外部サイトには何も送らない(すべて、この PC の中で処理する)。伴奏が目立つ曲では精度が落ちるので、
      --separate を勧める。できた JSON は、テキストなので、手で直してもよい(t=開始ms, d=長さms, n=MIDIノート番号)。
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile

import numpy as np

RATE = 22050
FRAME = 2048
HOP = 441          # 20ms
FMIN, FMAX = 80.0, 1000.0
THRESH = 0.30      # YIN のしきい値(小さいほど厳しい)
MIN_NOTE_MS = 80

NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
MAJOR = [0, 2, 4, 5, 7, 9, 11]


def decode(path, wav_rate=RATE):
    cmd = ["ffmpeg", "-v", "error", "-i", path, "-vn", "-ac", "1", "-ar", str(wav_rate), "-f", "f32le", "-"]
    out = subprocess.run(cmd, stdout=subprocess.PIPE, check=True).stdout
    return np.frombuffer(out, dtype=np.float32)


def separate_vocals(path, workdir):
    if shutil.which("demucs") is None:
        sys.exit("--separate には demucs が必要です: pip install demucs")
    subprocess.run(["demucs", "--two-stems", "vocals", "-o", workdir, path], check=True)
    base = os.path.splitext(os.path.basename(path))[0]
    for root, _dirs, files in os.walk(workdir):
        if "vocals.wav" in files and base in root:
            return os.path.join(root, "vocals.wav")
    sys.exit("demucs の出力が見つかりません")


def yin_track(x):
    """各フレームの (周波数Hz, 信頼度 0..1, 音量) を返す。周波数 0 = 音程なし。"""
    tau_min = int(RATE / FMAX)
    tau_max = int(RATE / FMIN)
    n = len(x)
    count = max(0, (n - FRAME) // HOP)
    f0 = np.zeros(count)
    conf = np.zeros(count)
    rms = np.zeros(count)
    win = FRAME - tau_max
    for i in range(count):
        seg = x[i * HOP: i * HOP + FRAME].astype(np.float64)
        rms[i] = np.sqrt(np.mean(seg * seg))
        if rms[i] < 0.01:
            continue
        # 差分関数 d(tau) = sum (x[j]-x[j+tau])^2 を、自己相関(FFT)で求める
        a = seg[:win]
        size = 1 << (FRAME + win).bit_length()
        fa = np.fft.rfft(a, size)
        fb = np.fft.rfft(seg, size)
        cross = np.fft.irfft(np.conj(fa) * fb, size)[: tau_max + 1]
        e = np.cumsum(seg * seg)
        energy_a = np.sum(a * a)
        energy_b = np.array([e[win + t - 1] - (e[t - 1] if t > 0 else 0.0) for t in range(tau_max + 1)])
        d = energy_a + energy_b - 2.0 * cross
        d[0] = 0.0
        cmnd = np.ones_like(d)
        cs = np.cumsum(d[1:])
        cmnd[1:] = d[1:] * np.arange(1, len(d)) / np.maximum(cs, 1e-12)
        tau = -1
        t = tau_min
        while t < tau_max:
            if cmnd[t] < THRESH:
                while t + 1 < tau_max and cmnd[t + 1] < cmnd[t]:
                    t += 1
                tau = t
                break
            t += 1
        if tau < 0:
            continue
        # 放物線補間
        y0, y1, y2 = cmnd[tau - 1], cmnd[tau], cmnd[tau + 1]
        den = y0 - 2 * y1 + y2
        shift = 0.5 * (y0 - y2) / den if abs(den) > 1e-12 else 0.0
        f0[i] = RATE / (tau + shift)
        conf[i] = 1.0 - cmnd[tau]
    return f0, conf, rms


def to_notes(f0, conf, rms):
    midi = np.where(f0 > 0, 69 + 12 * np.log2(np.maximum(f0, 1e-6) / 440.0), 0.0)
    # 音程のあるフレームを、中央値で、なめらかにする(1フレームのとび・オクターブの誤りを減らす)
    smooth = midi.copy()
    for i in range(len(midi)):
        w = midi[max(0, i - 2): i + 3]
        w = w[w > 0]
        smooth[i] = np.median(w) if len(w) >= 3 and midi[i] > 0 else 0.0
    notes = []
    i = 0
    n = len(smooth)
    while i < n:
        if smooth[i] <= 0:
            i += 1
            continue
        j = i
        vals = [smooth[i]]
        while j + 1 < n and smooth[j + 1] > 0 and abs(smooth[j + 1] - np.median(vals)) < 0.8:
            j += 1
            vals.append(smooth[j])
        start_ms = int(i * HOP * 1000 / RATE)
        dur_ms = int((j - i + 1) * HOP * 1000 / RATE)
        if dur_ms >= MIN_NOTE_MS:
            notes.append({"t": start_ms, "d": dur_ms, "n": int(round(float(np.median(vals)))),
                          "v": int(min(127, 40 + float(np.mean(rms[i:j + 1])) * 600))})
        i = j + 1
    # 同じ高さが、短い途切れでつながっているものは、1つにまとめる
    merged = []
    for nt in notes:
        if merged and nt["n"] == merged[-1]["n"] and nt["t"] - (merged[-1]["t"] + merged[-1]["d"]) < 60:
            merged[-1]["d"] = nt["t"] + nt["d"] - merged[-1]["t"]
        else:
            merged.append(nt)
    return merged


def detect_key(notes):
    """音の長さで重みを付けた音名の分布から、いちばん合う7音の音階(長調/その平行短調)を選ぶ。"""
    hist = np.zeros(12)
    for nt in notes:
        hist[nt["n"] % 12] += nt["d"]
    best, best_score = 0, -1.0
    for tonic in range(12):
        score = sum(hist[(tonic + d) % 12] for d in MAJOR)
        if score > best_score:
            best, best_score = tonic, score
    share = best_score / max(1.0, hist.sum())
    return best, share


def snap_to_scale(notes, tonic):
    """音階の外の音(誤検出が多い)を、いちばん近い音階の音に寄せる。"""
    scale = {(tonic + d) % 12 for d in MAJOR}
    changed = 0
    for nt in notes:
        if nt["n"] % 12 not in scale:
            for off in (-1, 1, -2, 2):
                if (nt["n"] + off) % 12 in scale:
                    nt["n"] += off
                    changed += 1
                    break
    return changed


def main():
    ap = argparse.ArgumentParser(description="曲から主旋律を取り出して .melody.json を作る")
    ap.add_argument("input", help="mp3 / mp4 / wav などの曲")
    ap.add_argument("-o", "--out", help="出力先のフォルダ(省略すると、曲と同じ場所)")
    ap.add_argument("--no-snap", action="store_true", help="音階への補正をしない")
    ap.add_argument("--separate", action="store_true", help="demucs で、先に歌声だけを取り出す")
    args = ap.parse_args()

    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg が見つかりません(PATH に通してください)")
    src = args.input
    with tempfile.TemporaryDirectory() as tmp:
        if args.separate:
            src_for_analysis = separate_vocals(src, tmp)
        else:
            src_for_analysis = src
        x = decode(src_for_analysis)
    f0, conf, rms = yin_track(x)
    notes = to_notes(f0, conf, rms)
    if notes:
        tonic, share = detect_key(notes)
        minor = NAMES[(tonic + 9) % 12]
        print(f"調: {NAMES[tonic]} メジャー / {minor} マイナー(音階に入る音の割合 {share:.0%})")
        if not args.no_snap:
            print(f"音階の外の音を {snap_to_scale(notes, tonic)} 個、近い音に寄せました")
    name = os.path.splitext(os.path.basename(src))[0]
    outdir = args.out or os.path.dirname(os.path.abspath(src))
    os.makedirs(outdir, exist_ok=True)
    out = os.path.join(outdir, name + ".melody.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"version": 1, "source": os.path.basename(src), "notes": notes}, f, ensure_ascii=False)
    print(f"{len(notes)} 個の音を書きました: {out}")


if __name__ == "__main__":
    main()
