#!/usr/bin/env python3
"""鍵盤の動画 + 元の動画(や音声)の 2 つから、音符 JSON を細かく作る。

    python3 pair_to_notes.py 鍵盤の動画.mp4 元の動画.mp4

- 鍵盤の動画(ピアノロール)から、弾かれた音(音程・長さ)を読み取る。ここが土台(いちばん正確)。
- 元の動画の音を聞いて、次のことをする:
    1. 時間のずれ・テンポの違いを求めて、音符の時刻を元の動画に合わせる(音符の和音の並びと、元の音声の和音を照らして合わせる)。
    2. 元の音声から、同じ音程の「出だし」を探して、音符のタイミングを細かく合わせる。
    3. 元の音声から、ドラム(バスドラム・ハイハット)を探して足す(--snare でスネアも。誤検出が多いので標準は入れない)。
- 結果は、元の動画のほうの名前で、元の動画と同じフォルダに JSON を作る。

必要なもの: ffmpeg、pip install numpy scipy soundfile onnxruntime pretty_midi resampy librosa mir_eval(+ basic-pitch)
"""
import argparse
import os
import subprocess
import sys
import tempfile

import numpy as np

import audio_to_notes
import songtools
import video_to_notes

SR = 22050
HOP = 2205            # 0.1 秒(リピーター 1 目盛り)ごとに特徴を作る
FRAME = HOP / SR


def to_wav(src, dst):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-vn", "-ac", "1", "-ar", str(SR), dst], check=True)


def load_wav(path):
    import soundfile as sf
    y, sr = sf.read(path, dtype="float32", always_2d=True)
    return y.mean(1), sr


# ---------------------------------------------------------------- 時間の合わせ方

def symbolic_chroma(notes, t0, n_frames):
    """音符(開始秒, 終了秒, MIDI)から、0.1 秒ごとの 12 音(ドレミ…)の強さを作る。音は、時間とともに弱まるとする。"""
    c = np.zeros((12, n_frames), np.float32)
    for ts, te, m in notes:
        i0 = max(0, int((ts - t0) / FRAME))
        i1 = min(n_frames, max(i0 + 1, int(np.ceil((te - t0) / FRAME))))
        decay = np.exp(-np.arange(i1 - i0) * FRAME / 1.5)
        c[m % 12, i0:i1] += decay
    return prepare(c)


def prepare(c, smooth=3):
    """時間で少しならし、各フレームの平均を引いて(= 相関で比べる)長さをそろえる。
    平均を引かないと、どの 2 つのフレームも似て見えて、正しい位置と区別できない。"""
    from scipy.ndimage import uniform_filter1d
    c = uniform_filter1d(c, smooth, axis=1, mode="nearest")
    c = c - c.mean(0, keepdims=True)
    return (c / (np.linalg.norm(c, axis=0, keepdims=True) + 1e-6)).astype(np.float32)


def audio_chroma(y):
    import librosa
    return prepare(librosa.feature.chroma_stft(y=y, sr=SR, hop_length=HOP, n_fft=4096))


def robust_line(x, y, rounds=4):
    """外れ値を捨てながら、y = a x + b を当てはめる"""
    keep = np.ones(len(x), bool)
    a, b = 1.0, 0.0
    for _ in range(rounds):
        if keep.sum() < 10:
            break
        a, b = np.polyfit(x[keep], y[keep], 1)
        r = np.abs(y - (a * x + b))
        keep = r <= np.percentile(r[keep], 80)
    return a, b


def align(notes, y):
    """鍵盤の動画の時刻(秒)→ 元の音声の時刻(秒)の対応を求める。返り値: (関数, 説明の辞書)"""
    import librosa
    t0 = min(n[0] for n in notes)
    t1 = max(n[1] for n in notes)
    n = int((t1 - t0) / FRAME) + 2
    cx = symbolic_chroma(notes, t0, n)
    cy = audio_chroma(y)
    cost = (1.0 - cx.T @ cy).astype(np.float32)  # (音符側の時間, 音声側の時間)
    # 進み方の傾きを 1/2〜2 に制限する(同じ曲なら、テンポがそれ以上は違わない)。制限しないと、音の薄い所で道が暴れる
    steps = np.array([[1, 1], [1, 2], [2, 1]])
    weights = np.array([1.0, 1.0, 1.0])
    try:
        _, wp = librosa.sequence.dtw(C=cost, subseq=True, step_sizes_sigma=steps, weights_mul=weights)
    except Exception:  # noqa: BLE001  (音声のほうが短いときなど)
        _, wp = librosa.sequence.dtw(C=cost, step_sizes_sigma=steps, weights_mul=weights)
    wp = wp[::-1]
    # 音符側の各フレームに、音声側のフレームの中央値を対応づける
    xs, ys = [], []
    for i in np.unique(wp[:, 0]):
        xs.append(t0 + i * FRAME)
        ys.append(np.median(wp[wp[:, 0] == i, 1]) * FRAME)
    xs, ys = np.array(xs), np.array(ys)
    a, b = robust_line(xs, ys)
    resid = np.abs(ys - (a * xs + b))
    info = {"scale": a, "offset": b, "median_resid": float(np.median(resid)), "frac_close": float((resid < 0.3).mean())}

    def linear(t):
        return a * np.asarray(t, float) + b

    # 場所によってテンポが違うとき用: DTW の点を、前後 12 秒ずつに区切って、それぞれ直線に当てはめる
    # (DTW の点は 0.1〜0.4 秒ほど揺れるが、区間ごとの直線にすると、その揺れが平均されて小さくなる)
    win, step = 12.0, 3.0
    cs, vs = [], []
    for c in np.arange(xs.min(), xs.max() + step, step):
        sel = np.abs(xs - c) <= win / 2
        if sel.sum() < 20:
            continue
        a_, b_ = robust_line(xs[sel], ys[sel])
        cs.append(c)
        vs.append(a_ * c + b_)
    cs, vs = np.array(cs), np.maximum.accumulate(np.array(vs))

    def warp(t):
        t = np.asarray(t, float)
        out = np.interp(t, cs, vs)
        return np.where(t < cs[0], vs[0] + a * (t - cs[0]), np.where(t > cs[-1], vs[-1] + a * (t - cs[-1]), out))
    return {"直線(ずれとテンポの違いだけ)": linear, "場所ごとの対応(テンポが変わる)": warp}, info


# ---------------------------------------------------------------- タイミングを細かく合わせる

def snap(notes, tpred, events, window):
    """音符ごとに、同じ音程の出だしを、予想の時刻の前後 window 秒で探す(1 つの出だしは 1 音符まで)。
    返り値: 各音符の (時刻, 見つかったか)"""
    by_pitch = {}
    for i, (s, e, p, a) in enumerate(events):
        by_pitch.setdefault(p, []).append((s, i))
    for p in by_pitch:
        by_pitch[p].sort()
    used = set()
    out = [None] * len(notes)
    order = sorted(range(len(notes)), key=lambda k: tpred[k])
    for k in order:
        m = notes[k][2]
        best = None
        for s, i in by_pitch.get(m, []):
            if s < tpred[k] - window:
                continue
            if s > tpred[k] + window:
                break
            if i in used:
                continue
            if best is None or abs(s - tpred[k]) < abs(best[0] - tpred[k]):
                best = (s, i)
        if best:
            used.add(best[1])
            out[k] = (best[0], True)
        else:
            out[k] = (tpred[k], False)
    return out


def refine_by_windows(pitches, tpred, events, rng, tol, span=1.5, step=1.0):
    """近くの音符たちを、まとめて音声の出だしに合わせる。
    1 秒ごとに、前後 span 秒の音符を、-rng〜+rng 秒の範囲で少しずつずらして、同じ音程の出だしが tol 秒以内にある音符が
    いちばん多くなるずらし量を探す。同じ音の連打があっても、並び全体で合わせるので、隣の音と取り違えにくい。
    返り値: 各音符の補正を足した時刻"""
    from scipy.ndimage import median_filter
    by_pitch = {}
    for s_, e_, p, a in events:
        by_pitch.setdefault(p, []).append(s_)
    by_pitch = {p: np.array(sorted(v)) for p, v in by_pitch.items()}
    t = np.asarray(tpred, float)
    deltas = np.arange(-rng, rng + 1e-9, 0.01)
    centers, picks = [], []
    for c in np.arange(t.min(), t.max() + step, step):
        idx = np.where(np.abs(t - c) <= span)[0]
        if len(idx) < 6:
            continue
        score = np.zeros(len(deltas))
        for k in idx:
            arr = by_pitch.get(pitches[k])
            if arr is None or len(arr) == 0:
                continue
            tt = t[k] + deltas
            pos = np.searchsorted(arr, tt)
            near = np.minimum(np.abs(tt - arr[np.clip(pos - 1, 0, len(arr) - 1)]), np.abs(tt - arr[np.clip(pos, 0, len(arr) - 1)]))
            score += near <= tol
        if score.max() < 3:
            continue
        best = deltas[score >= score.max() - 0.5]
        centers.append(c)
        picks.append(best[np.argmin(np.abs(best))])   # 同点なら、ずらしの小さいほう
    if len(centers) < 3:
        return list(t)
    picks = median_filter(np.array(picks), size=5, mode="nearest")
    return list(t + np.interp(t, centers, picks))


def local_correction(times, offsets, span=3.0, need=6):
    """見つかった出だしのずれ(offsets)の中央値を、前後 span 秒ごとに求めて、各時刻の補正にする"""
    times = np.asarray(times)
    offsets = np.asarray(offsets)
    order = np.argsort(times)
    times, offsets = times[order], offsets[order]

    def corr(t):
        out = np.zeros(len(t))
        for k, tt in enumerate(t):
            lo, hi = np.searchsorted(times, [tt - span, tt + span])
            if hi - lo >= need:
                out[k] = np.median(offsets[lo:hi])
        return out
    return corr


# ---------------------------------------------------------------- 本体

def pair_notes(kb_args, orig_path, args):
    """返り値: (元の動画の時刻での音符のリスト [(秒, MIDI または打楽器の番号)], 説明の辞書)"""
    # 1. 鍵盤の動画から、音を読む
    frames = video_to_notes.read_keyboard_notes(kb_args)
    fps = video_to_notes.FPS
    notes = sorted((f / fps, e / fps, m + args.transpose if not songtools.is_drum(m) else m) for f, m, e in frames)
    notes = [(s, e, songtools.fold(m)) for s, e, m in notes]
    info = {"keyboard_notes": len(notes)}

    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, "orig.wav")
        to_wav(orig_path, wav)
        y, _ = load_wav(wav)
        info["orig_seconds"] = len(y) / SR

        # 2. 時刻を合わせる
        print("  元の音声と、音符を照らして、時刻を合わせています…", file=sys.stderr)
        maps, ainfo = align(notes, y)
        info.update(ainfo)

        # 3. タイミングを細かく合わせる(元の音声の、同じ音程の出だしを探す)
        print("  元の音声から音を推定しています(少し時間がかかります)…", file=sys.stderr)
        events = audio_to_notes.predict_events(wav, args)
        pitches = [n[2] for n in notes]
        best = None
        for mode, f in maps.items():
            tpred = list(f([n[0] for n in notes]))
            aligned = list(tpred)
            # 近くの音符たちをまとめて、音声の出だしに合わせる(範囲を広いほうから狭いほうへ)
            for rng, tol in ((0.6, 0.06), (0.25, 0.04), (0.1, 0.03)):
                tpred = refine_by_windows(pitches, tpred, events, rng, tol)
            refined = list(tpred)
            final = snap(notes, tpred, events, 0.08)
            confirmed = sum(1 for _, ok in final if ok)
            print(f"    {mode}: 出だしを確認できた音符 {confirmed} / {len(notes)}", file=sys.stderr)
            if best is None or confirmed > best[0]:
                best = (confirmed, mode, final, aligned, refined)
        info["confirmed"], info["mode"], second = best[0], best[1], best[2]
        info["_aligned_only"], info["_refined"] = best[3], best[4]  # 診断用
        info["_flags"] = [ok for _, ok in second]
        out = [(second[k][0], notes[k][2]) for k in range(len(notes))]
        # 音の強さ(%): 元の音声の大きさから(元の曲で小さく弾いている音は、小さく鳴らす)
        levels = songtools.levels_from_audio(wav, out)
        out = [(t, m, v) for (t, m), v in zip(out, levels)]

        # 4. ドラムを足す
        if not args.no_drums:
            drums = songtools.detect_drums(wav)
            keep = {songtools.BASEDRUM, songtools.HAT} | ({songtools.SNARE} if args.snare else set())
            drums = [(t, code, songtools.drum_velocity(amp)) for t, code, amp in drums if code in keep]
            out += drums
            info["drums"] = {songtools.DRUM_NAMES[c]: sum(1 for _, k, _ in drums if k == c) for c in keep}
    return out, info


def convert_pair(kb_path, orig_path, args):
    kb_args = argparse.Namespace(**{k: v for k, v in vars(args).items()})
    kb_args.input = kb_path
    kb_args.start = 0
    kb_args.end = 0
    kb_args.require_keyboard = False
    kb_args.output = None
    out_notes, info = pair_notes(kb_args, orig_path, args)

    by_time = {}
    for t, m, vel in out_notes:
        tick = int(round(t / args.speed / songtools.TICK))
        if tick >= 0:
            by_time.setdefault(tick, []).append((1.0, songtools.fold(m), vel))
    by_time, dropped, moved = songtools.limit_polyphony(by_time, args.max_poly, False)
    stem = os.path.splitext(os.path.basename(orig_path))[0]
    name = args.name or stem
    out = args.output or os.path.join(os.path.dirname(os.path.abspath(orig_path)), stem + ".json")
    count, seconds = songtools.write_json(out, name, by_time, 0)

    n = info["keyboard_notes"]
    print(f"  時刻の合わせ方: {info['mode']}  (テンポ比 {info['scale']:.3f}、ずれ {info['offset']:+.2f} 秒、"
          f"中央値の誤差 {info['median_resid']:.2f} 秒)", file=sys.stderr)
    print(f"  元の音声で、出だしを確認できた音符: {info['confirmed']} / {n}  ({100 * info['confirmed'] / max(1, n):.0f}%)", file=sys.stderr)
    if info["confirmed"] < 0.3 * n:
        print("  ※ 確認できた音符が少ないので、2 つの動画が同じ曲か、確かめてください(ずれが大きいと、時刻の合わせ方を間違えます)", file=sys.stderr)
    if "drums" in info:
        print("  足したドラム: " + "、".join(f"{k} {v}" for k, v in info["drums"].items()), file=sys.stderr)
    songtools.report(out, count, seconds, dropped, moved)
    if args.install:
        songtools.install(out, args.songs_dir)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("keyboard", help="鍵盤のある動画(ピアノロール)")
    ap.add_argument("original", help="元の動画(鍵盤がないほう。音声だけのファイルでもよい)")
    ap.add_argument("-o", "--output")
    ap.add_argument("--name", help="曲名(省略すると、元の動画のファイル名)")
    ap.add_argument("--install", action="store_true", help="できた JSON を、Minecraft の songs フォルダへコピーする")
    ap.add_argument("--songs-dir")
    ap.add_argument("--max-poly", type=int, default=12)
    ap.add_argument("--max-simul", type=int, default=10)
    ap.add_argument("--no-drums", action="store_true", help="元の音声から、ドラムを足さない")
    ap.add_argument("--snare", action="store_true", help="スネアも足す(誤検出が多い)")
    ap.add_argument("--speed", type=float, default=1.0)
    ap.add_argument("--transpose", type=int, default=0)
    ap.add_argument("--onset", type=float, default=0.35, help="元の音声から出だしを探すときのしきい値(ゆるくして、取りこぼしを減らす)")
    ap.add_argument("--frame", type=float, default=0.2)
    ap.add_argument("--min-len", type=float, default=0.05)
    ap.add_argument("--no-audio-check", action="store_true")
    ap.add_argument("--first-note")
    ap.add_argument("--x0", type=float)
    ap.add_argument("--white-w", type=float)
    ap.add_argument("--white-y", type=int)
    ap.add_argument("--black-y", type=int)
    ap.add_argument("--sat", type=int, default=60)
    args, ignored = ap.parse_known_args()
    if ignored:
        print(f"(この変換では使わない指定を、無視しました: {' '.join(ignored)})", file=sys.stderr)
    convert_pair(args.keyboard, args.original, args)


if __name__ == "__main__":
    main()
