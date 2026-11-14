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


def amp_of(v):
    """grid の値は、強さ(float)か、(強さ, 音の強さ%) の組"""
    return v[0] if isinstance(v, tuple) else v


def suppress_harmonics(grid, ra=0.7, rb=0.8, tol=1):
    """音声から推定した音のうち、倍音の取り違えと見られるものを取り除く。
    grid: {(tick, MIDI): 強さ または (強さ, 音の強さ%)}。ある音について、ほぼ同時に、1 オクターブ下(または 1 オクターブ上)に
    もっと強い音があり、その強さが ra 倍(上は rb 倍)より小さければ、倍音の取り違えとして捨てる。
    (ra=0.7, rb=0.8 は、映像から読んだ正解のある 2 曲で、いちばん一致度が上がった値)"""
    out = {}
    for (t, p), v in grid.items():
        a = amp_of(v)
        if is_drum(p):
            out[(t, p)] = v
            continue

        def near(pp):
            return max((amp_of(grid.get((t + d, pp), 0.0)) for d in range(-tol, tol + 1)), default=0.0)

        if ra and a < ra * near(p - 12):
            continue
        if rb and a < rb * near(p + 12):
            continue
        out[(t, p)] = v
    return out


def limit_polyphony(by_time, cap, spread=True):
    """by_time: {tick: [(強さ, MIDI, 音の強さ%), ...]}(音の強さ% は省略可。省略すると 100)。1 時刻 cap 音までにする。
    あふれた音は、まず弱い音から省く(最高音・最低音は残す)。spread=True なら、省く代わりに、
    真ん中の音を 1 tick(0.1 秒)前か後ろへずらして残す。
    返り値: ({tick: [(MIDI, 音の強さ%), ...]}, 省いた数, ずらした数)"""
    out = {}
    for t in sorted(by_time):
        best = {}  # 同じ時刻の同じ音は 1 つにまとめる(強いほう)
        for item in by_time[t]:
            a, m = item[0], item[1]
            vel = item[2] if len(item) > 2 else 100
            if m not in best or a > best[m][0]:
                best[m] = (a, vel)
        out[t] = sorted(((a, m, v) for m, (a, v) in best.items()), key=lambda x: x[1])  # 低い順
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
                    if nt >= 0 and len(out.get(nt, [])) < cap and all(m != victim[1] for _, m, _ in out.get(nt, [])):
                        out.setdefault(nt, []).append(victim)
                        out[nt].sort(key=lambda x: x[1])
                        placed = True
                        moved += 1
                        break
            if not placed:
                dropped += 1
    return {t: [(m, v) for _, m, v in lst] for t, lst in out.items() if lst}, dropped, moved


def write_json(path, name, by_time, row_length=0):
    """by_time: {tick: [MIDI, または (MIDI, 音の強さ%)]}。音の強さ が 100 のときは、JSON に書かない"""
    def norm(x):
        return (x, 100) if isinstance(x, int) else x
    notes = [[t, m, v] for t in sorted(by_time) for m, v in sorted(norm(x) for x in by_time[t])]
    base = notes[0][0] if notes else 0

    def entry(t, m, v):
        e = [t - base, 0, DRUM_NAMES[m]] if is_drum(m) else [t - base, m]
        if v < 100:
            e.append(int(max(1, v)))
        return e
    row = '"row_length": "line", ' if row_length < 0 else '"row_length": %d, ' % row_length if row_length else ""
    with open(path, "w", encoding="utf-8") as f:
        f.write('{"name": %s, "tick": %s, %s"notes": [\n' % (json.dumps(name, ensure_ascii=False), TICK, row))
        f.write(",\n".join(json.dumps(entry(t, m, v)) for t, m, v in notes))
        f.write("\n]}\n")
    return len(notes), (notes[-1][0] - base) * TICK if notes else 0


def report(path, count, seconds, dropped, moved):
    print(f"{path}: {count} 音, 長さ {seconds:.1f} 秒, 隣の 0.1 秒へずらした音 {moved}, 同時発音の上限で省いた音 {dropped}", file=sys.stderr)


# ---- 音の強さ(音量) ----

def midi_velocity(v):
    """MIDI のベロシティ(1〜127)を、音の強さ%(10〜100)にする"""
    return int(max(10, min(100, round(100 * (v / 127.0) ** 1.3))))


def drum_velocity(amp):
    """打楽器の検出の強さ(0〜1)を、音の強さ%にする"""
    # 打楽器の音は鋭くて耳につくので、上限を低くする(誤検出が混ざっても「カッ」と目立たないように)
    return int(max(25, min(70, round(25 + 45 * amp))))


def levels_from_audio(wav_path, notes, sr=22050):
    """音声の大きさから、音符ごとの音の強さ(%)を求める。notes: [(開始秒, MIDI)] 。返り値: [音の強さ%]
    各音符の出だしの 0.19 秒を取り出して、その音程(と、2〜4 倍音)の周波数のエネルギーを測る。
    曲全体で、大きいほう(上位 5%)を 100% として、25 dB 小さくなるごとに 10 倍小さくなる割合で、1〜100% にする。
    (元の曲で、小さく弾いている音を、小さく鳴らすため。音声が読めなければ、すべて 100%)"""
    import numpy as np
    try:
        import soundfile as sf
        y, file_sr = sf.read(wav_path, dtype="float32", always_2d=True)
    except Exception:  # noqa: BLE001
        return [100] * len(notes)
    y = y.mean(1)
    if file_sr != sr:
        y = np.interp(np.arange(0, len(y), file_sr / sr), np.arange(len(y)), y).astype("float32")
    n_fft = 4096
    win = np.hanning(n_fft).astype("float32")
    freqs = np.fft.rfftfreq(n_fft, 1.0 / sr)
    weights = (1.0, 0.6, 0.4, 0.3)
    db = []
    for t, m in notes:
        s0 = max(0, int((t - 0.02) * sr))
        seg = y[s0:s0 + n_fft]
        if len(seg) < n_fft:
            seg = np.pad(seg, (0, n_fft - len(seg)))
        spec = np.abs(np.fft.rfft(seg * win)) ** 2
        f0 = 440.0 * 2 ** ((m - 69) / 12.0)
        e = 0.0
        for k, w in enumerate(weights, start=1):
            f = f0 * k
            if f >= sr / 2 * 0.95:
                break
            sel = (freqs >= f * 2 ** (-0.45 / 12)) & (freqs <= f * 2 ** (0.45 / 12))
            if not sel.any():
                sel = np.zeros(len(freqs), bool)
                sel[int(round(f / (sr / n_fft)))] = True
            e += w * float(spec[sel].sum())
        db.append(10.0 * np.log10(e + 1e-9))
    if not db:
        return []
    ref = float(np.percentile(db, 95))
    return [int(round(100 * min(1.0, max(0.1, 10 ** ((d - ref) / 25.0))))) for d in db]


def levels_for_file(path, notes):
    """動画・音声ファイルの音から、音符ごとの音の強さ(%)を求める。notes: [(開始秒, MIDI)]。音声が無ければ、すべて 100%"""
    import subprocess
    import tempfile
    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, "a.wav")
        r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-vn", "-ac", "1", "-ar", "22050", wav])
        if r.returncode != 0 or not os.path.isfile(wav):
            return [100] * len(notes)
        return levels_from_audio(wav, notes)


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
