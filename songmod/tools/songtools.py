"""audio_to_notes.py / video_to_notes.py 共通の処理(音域の折り返し・同時発音の調整・JSON 書き出し)。"""
import json
import os
import sys

TICK = 0.1  # レッドストーンリピーター 1 目盛りの秒数
LOW, HIGH = 30, 102  # 音符ブロックで出せる範囲(低音 F#1 〜 高音 F#7)


# 打楽器は、音の高さの代わりに 200 以上の番号で表す(JSON には [時刻, 0, "snare"] の形で書く)
BASEDRUM, SNARE, HAT = 200, 201, 202
DRUM_NAMES = {BASEDRUM: "basedrum", SNARE: "snare", HAT: "hat"}

# General MIDI のドラムの音程 → 打楽器(キック・タム = バスドラム、スネア・クラップ = スネア、ハイハット・シンバル = ハイハット)
GM_DRUMS = {}
for _p in (35, 36, 41, 43, 45, 47, 48, 50):
    GM_DRUMS[_p] = BASEDRUM
for _p in (37, 38, 39, 40):
    GM_DRUMS[_p] = SNARE
for _p in (42, 44, 46, 49, 51, 52, 53, 55, 57, 59):
    GM_DRUMS[_p] = HAT


def is_drum(code):
    return code >= 200


def fold(p):
    if is_drum(p):
        return p
    while p < LOW:
        p += 12
    while p > HIGH:
        p -= 12
    return p


def suppress_harmonics(grid, ra=0.7, rb=0.8, tol=1):
    """音声から推定した音のうち、倍音の取り違えと見られるものを取り除く。
    grid: {(tick, MIDI): 強さ}。ある音について、ほぼ同時に、1 オクターブ下(または 1 オクターブ上)に
    もっと強い音があり、その強さが ra 倍(上は rb 倍)より小さければ、倍音の取り違えとして捨てる。
    (ra=0.7, rb=0.8 は、映像から読んだ正解のある 2 曲で、いちばん一致度が上がった値)"""
    out = {}
    for (t, p), a in grid.items():
        if is_drum(p):
            out[(t, p)] = a
            continue

        def near(pp):
            return max((grid.get((t + d, pp), 0.0) for d in range(-tol, tol + 1)), default=0.0)

        if ra and a < ra * near(p - 12):
            continue
        if rb and a < rb * near(p + 12):
            continue
        out[(t, p)] = a
    return out


def limit_polyphony(by_time, cap, spread=True):
    """by_time: {tick: [(強さ, MIDI), ...]}。1 時刻 cap 音までにする。
    あふれた音は、まず弱い音から省く(最高音・最低音は残す)。spread=True なら、省く代わりに、
    真ん中の音を 1 tick(0.1 秒)前か後ろへずらして残す。返り値: ({tick: [midi...]}, 省いた数, ずらした数)"""
    out = {}
    for t in sorted(by_time):
        best = {}  # 同じ時刻の同じ音は 1 つにまとめる(強いほう)
        for a, m in by_time[t]:
            if m not in best or a > best[m]:
                best[m] = a
        out[t] = sorted(((a, m) for m, a in best.items()), key=lambda x: x[1])  # 低い順
    dropped = 0
    moved = 0
    for t in sorted(out):
        lst = out[t]
        while len(lst) > cap:
            # 真ん中の音(最高音・最低音は動かさない)から選ぶ。なければ最も弱い音
            inner = lst[1:-1] if len(lst) > 2 else lst
            victim = min(inner, key=lambda x: x[0])
            lst.remove(victim)
            placed = False
            if spread:
                for nt in (t - 1, t + 1):
                    if nt >= 0 and len(out.get(nt, [])) < cap and all(m != victim[1] for _, m in out.get(nt, [])):
                        out.setdefault(nt, []).append(victim)
                        out[nt].sort(key=lambda x: x[1])
                        placed = True
                        moved += 1
                        break
            if not placed:
                dropped += 1
    return {t: [m for _, m in v] for t, v in out.items() if v}, dropped, moved


def write_json(path, name, by_time, row_length=0):
    notes = [[t, m] for t in sorted(by_time) for m in sorted(by_time[t])]
    base = notes[0][0] if notes else 0
    def entry(t, m):
        return [t - base, 0, DRUM_NAMES[m]] if is_drum(m) else [t - base, m]
    row = '"row_length": "line", ' if row_length < 0 else '"row_length": %d, ' % row_length if row_length else ""
    with open(path, "w", encoding="utf-8") as f:
        f.write('{"name": %s, "tick": %s, %s"notes": [\n' % (json.dumps(name, ensure_ascii=False), TICK, row))
        f.write(",\n".join(json.dumps(entry(t, m)) for t, m in notes))
        f.write("\n]}\n")
    return len(notes), (notes[-1][0] - base) * TICK if notes else 0


def report(path, count, seconds, dropped, moved):
    print(f"{path}: {count} 音, 長さ {seconds:.1f} 秒, 隣の 0.1 秒へずらした音 {moved}, 同時発音の上限で省いた音 {dropped}", file=sys.stderr)


# ---- 変換したファイルを、ゲームの songs フォルダへ入れる ----

def find_songs_dir(override=None):
    """Minecraft の songs フォルダ。環境変数 SONGMOD_DIR か、--songs-dir で指定。なければ、よくある場所の .minecraft/songs。見つからなければ None"""
    import platform
    cands = []
    if override:
        cands.append(override)
    if os.environ.get("SONGMOD_DIR"):
        cands.append(os.environ["SONGMOD_DIR"])
    home = os.path.expanduser("~")
    system = platform.system()
    if system == "Windows":
        cands.append(os.path.join(os.environ.get("APPDATA", home), ".minecraft", "songs"))
    elif system == "Darwin":
        cands.append(os.path.join(home, "Library", "Application Support", "minecraft", "songs"))
    else:
        cands.append(os.path.join(home, ".minecraft", "songs"))
    for c in cands:
        if override and c == override:
            return c  # 指定されたものは、まだ無くても作る
        if os.path.isdir(c) or os.path.isdir(os.path.dirname(c)):
            return c
    return None


def install(path, songs_dir=None):
    """path の JSON を、songs フォルダへコピーする。コピー先を返す。フォルダが見つからなければ None"""
    import shutil
    dest = find_songs_dir(songs_dir)
    if dest is None:
        print("(Minecraft の songs フォルダが見つからないので、コピーしません。--songs-dir で指定できます)", file=sys.stderr)
        return None
    os.makedirs(dest, exist_ok=True)
    target = os.path.join(dest, os.path.basename(path))
    shutil.copyfile(path, target)
    print(f"  → {target} にコピーしました", file=sys.stderr)
    return target


# ---- 音声から打楽器を探す(実験的) ----

def detect_drums(wav_path, sr=22050, n_fft=1024, hop=256):
    """打楽器の音を、音声から探す。返り値: [(時刻(秒), 打楽器の番号, 強さ0〜1)]。
    音声を「持続する音(メロディ)」と「瞬間的な音(打楽器)」に分け、打楽器のほうだけを、低い帯域(バスドラム)・
    中の帯域(スネア)・高い帯域(ハイハット)ごとに、急に大きくなった所を拾う。目安であって、正確ではない。"""
    import numpy as np
    import soundfile as sf
    from scipy.ndimage import median_filter

    y, file_sr = sf.read(wav_path, dtype="float32", always_2d=True)
    y = y.mean(1)
    if file_sr != sr:
        import numpy as _np
        idx = _np.arange(0, len(y), file_sr / sr)
        y = _np.interp(idx, _np.arange(len(y)), y).astype("float32")
    if len(y) < n_fft * 2:
        return []
    win = np.hanning(n_fft).astype("float32")
    n_frames = 1 + (len(y) - n_fft) // hop
    idx = np.arange(n_fft)[None, :] + hop * np.arange(n_frames)[:, None]
    S = np.abs(np.fft.rfft(y[idx] * win, axis=1)).T  # (周波数, 時間)
    # 持続する成分(時間方向になだらか)と、瞬間的な成分(周波数方向になだらか)に分ける
    H = median_filter(S, size=(1, 17), mode="nearest")
    P = median_filter(S, size=(17, 1), mode="nearest")
    S = S * (P ** 2 / (H ** 2 + P ** 2 + 1e-9))
    freqs = np.fft.rfftfreq(n_fft, 1.0 / sr)

    def band(lo, hi):
        return (S[(freqs >= lo) & (freqs < hi)] ** 2).sum(0)

    def flux_of(e):
        e = np.log1p(e)
        f = np.maximum(0, np.diff(e, prepend=e[0]))
        return np.convolve(f, np.ones(2) / 2, mode="same")

    def peaks(flux, thr, gap=6):
        res, last = [], -100
        for t in range(2, len(flux) - 2):
            if flux[t] > thr and flux[t] == flux[t - 2:t + 3].max() and t - last >= gap:
                res.append(t)
                last = t
        return res

    def threshold(flux, k):
        return np.median(flux) + k * flux.std() + 1e-6

    f_kick = flux_of(band(40, 130))
    f_snare_low = flux_of(band(180, 400))
    f_snare_mid = flux_of(band(2000, 6000))
    f_hat = flux_of(band(7000, 11000))
    out = []
    for t in peaks(f_kick, threshold(f_kick, 2.0)):
        out.append((t * hop / sr, BASEDRUM, float(min(1.0, f_kick[t] / (f_kick.max() + 1e-9)))))
    # スネアは「低〜中の帯域(胴の音)」と「中〜高の帯域(ザーッという音)」が同時に立ち上がったとき
    thr_low, thr_mid = threshold(f_snare_low, 2.0), threshold(f_snare_mid, 2.0)
    for t in peaks(f_snare_low, thr_low):
        if f_snare_mid[max(0, t - 1):t + 2].max() > thr_mid:
            out.append((t * hop / sr, SNARE, float(min(1.0, f_snare_low[t] / (f_snare_low.max() + 1e-9)))))
    for t in peaks(f_hat, threshold(f_hat, 3.5)):
        out.append((t * hop / sr, HAT, float(min(1.0, f_hat[t] / (f_hat.max() + 1e-9)))))
    out.sort()
    # 同じ瞬間にバスドラムとスネアがあれば、強いほうだけ(周波数が重なるため)
    clean = []
    for t, code, a in out:
        if clean and abs(clean[-1][0] - t) < 0.03 and {clean[-1][1], code} == {BASEDRUM, SNARE}:
            if a > clean[-1][2]:
                clean[-1] = (t, code, a)
            continue
        clean.append((t, code, a))
    return clean
