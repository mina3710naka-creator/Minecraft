#!/usr/bin/env python3
"""魔法MOD の効果音を合成して ogg にする(numpy + ffmpeg)。属性ごとの詠唱・着弾、最大魔法、予兆、回避、反応など。

使い方: python3 -I tools/gen_sounds.py   (ffmpeg が必要)
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "magicmod")
SR = 32000
RNG = np.random.default_rng(7)


def t_(d):
    return np.linspace(0, d, int(SR * d), endpoint=False)


def noise(d):
    return RNG.standard_normal(int(SR * d))


def env(n, a=0.01, d=0.2, curve=2.0):
    """立ち上がり a 秒、そのあと指数的に減衰。"""
    x = np.arange(n) / SR
    up = np.minimum(1, x / max(a, 1e-4))
    down = np.exp(-x * curve / max(d, 1e-3)) if curve else np.ones(n)
    return up * down


def fade(x, a=0.005, b=0.03):
    n = len(x)
    na, nb = int(SR * a), int(SR * b)
    x = x.copy()
    x[:na] *= np.linspace(0, 1, na)
    x[-nb:] *= np.linspace(1, 0, nb)
    return x


def band(x, lo, hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    m = np.clip((f - lo) / max(lo * 0.2, 1), 0, 1) * np.clip((hi - f) / max(hi * 0.2, 1), 0, 1)
    return np.fft.irfft(X * m, len(x))


def lowpass(x, fc):
    return band(x, 1, fc)


def sweep_noise(d, f0, f1, width=0.5, shape=2.0):
    """周波数が f0 → f1 へ動くフィルタのかかったノイズ(風・ひゅっという音)。"""
    n = int(SR * d)
    x = noise(d)
    out = np.zeros(n)
    K = 14
    for k in range(K):
        u = k / (K - 1)
        fc = f0 * (f1 / f0) ** u
        b = band(x, fc * (1 - width * 0.5), fc * (1 + width * 0.5))
        c = u * 0.9 + 0.05
        e = np.exp(-((np.arange(n) / n - c) ** 2) / (2 * 0.07 ** 2))
        out += b * e
    return out


def tone(freq, d, harm=(1.0,), phase=0.0):
    """freq は定数か、長さ n の配列。"""
    n = int(SR * d)
    f = np.full(n, freq, dtype=float) if np.isscalar(freq) else np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(freq)), freq)
    ph = 2 * np.pi * np.cumsum(f) / SR + phase
    y = np.zeros(n)
    for i, h in enumerate(harm):
        y += h * np.sin(ph * (i + 1))
    return y


def saw(freq, d):
    n = int(SR * d)
    f = np.full(n, freq, dtype=float) if np.isscalar(freq) else np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(freq)), freq)
    ph = np.cumsum(f) / SR
    return 2 * (ph - np.floor(ph + 0.5))


def echo(x, delays=((0.11, 0.35), (0.19, 0.22), (0.31, 0.12))):
    out = np.zeros(len(x) + int(SR * 0.4))
    out[:len(x)] += x
    for dl, g in delays:
        o = int(SR * dl)
        out[o:o + len(x)] += x * g
    return out


def bell(f, d, amp=1.0):
    n = int(SR * d)
    x = np.arange(n) / SR
    y = np.zeros(n)
    for ratio, g, dec in ((1, 1.0, 1.0), (2.76, 0.4, 0.6), (5.4, 0.25, 0.4), (8.9, 0.12, 0.3)):
        y += g * np.sin(2 * np.pi * f * ratio * x) * np.exp(-x * 5 / (d * dec))
    return y * amp


def pops(d, rate, fc=3000, decay=0.02):
    n = int(SR * d)
    y = np.zeros(n)
    for _ in range(int(rate * d)):
        i = RNG.integers(0, n - 1)
        L = min(n - i, int(SR * decay * (0.5 + RNG.random() * 2)))
        y[i:i + L] += noise(L / SR) * np.exp(-np.arange(L) / (L / 4)) * (0.3 + RNG.random())
    return lowpass(y, fc)


def norm(x, peak=0.8):
    m = np.max(np.abs(x)) or 1
    return x / m * peak


# ---------------------------------------------------------------------------------------------

def cast(el, d):
    if el == "wind":
        x = sweep_noise(d, 500, 4200, 0.7) * 1.0 + 0.2 * lowpass(noise(d), 800) * env(int(SR * d), 0.1, d, 3)
        x *= env(len(x), 0.05, d, 1.2)
    elif el == "thunder":
        zap = saw(np.linspace(2600, 180, int(SR * 0.18)), 0.18) * env(int(SR * 0.18), 0.002, 0.08)
        crack = pops(d, 60, 6000, 0.01) * 2
        x = np.concatenate([zap, np.zeros(int(SR * d) - len(zap))]) + crack * env(int(SR * d), 0.005, d * 0.6)
        x += 0.5 * lowpass(noise(d), 300) * env(int(SR * d), 0.01, d * 0.5)
    elif el == "fire":
        sw = lowpass(noise(d), 1400) * env(int(SR * d), d * 0.25, d, 1.5)
        x = sw * 1.2 + pops(d, 45, 5000) * 1.5 * env(int(SR * d), 0.05, d, 1.0)
        x += 0.6 * sweep_noise(d, 200, 1800, 0.8) * env(int(SR * d), 0.1, d, 1.6)
    elif el == "water":
        n = int(SR * d)
        x = np.zeros(n)
        for k in range(int(d * 14)):
            st = RNG.random() * (d - 0.12)
            f0 = 300 + RNG.random() * 500
            b = tone(np.linspace(f0, f0 * 2.4, int(SR * 0.09)), 0.09) * env(int(SR * 0.09), 0.003, 0.05)
            i = int(st * SR)
            x[i:i + len(b)] += b * (0.3 + RNG.random() * 0.4)
        x += 0.5 * lowpass(noise(d), 2500) * env(n, 0.03, d, 2.5)
    elif el == "gravity":
        n = int(SR * d)
        f = np.linspace(180, 38, n)
        x = tone(f, d, (1, 0.4)) * (1 + 0.3 * np.sin(2 * np.pi * 9 * np.arange(n) / SR)) * env(n, 0.02, d, 1.0)
        x += 0.5 * sweep_noise(d, 3000, 200, 0.5)
    elif el == "dark":
        n = int(SR * d)
        x = lowpass(saw(55, d) + saw(58.5, d), 400) * (0.6 + 0.4 * np.sin(2 * np.pi * 6 * np.arange(n) / SR)) * env(n, d * 0.3, d, 1.5)
        x += 0.35 * band(noise(d), 1500, 5000) * env(n, d * 0.4, d, 2.0)
    elif el == "light":
        notes = [880, 1108, 1318]
        n = int(SR * d)
        x = np.zeros(n)
        for i, f in enumerate(notes):
            b = bell(f, d - i * 0.1, 0.8)
            o = int(SR * i * 0.08)
            x[o:o + len(b)] += b[:n - o]
    else:  # curse
        n = int(SR * d)
        f = 300 + 40 * np.sin(2 * np.pi * 5 * np.arange(n) / SR) - np.linspace(0, 120, n)
        x = tone(f, d, (1, 0.5, 0.3)) * env(n, 0.05, d, 1.4)
        x += 0.4 * band(noise(d), 900, 3500) * env(n, 0.15, d, 1.8) * (0.6 + 0.4 * np.sin(2 * np.pi * 11 * np.arange(n) / SR))
    return fade(norm(x))


def impact(el, d=0.3):
    n = int(SR * d)
    x = lowpass(noise(d), 900) * env(n, 0.002, d * 0.5)
    if el == "thunder":
        x += pops(d, 90, 7000, 0.006) * 1.5
    elif el == "fire":
        x += lowpass(noise(d), 2500) * env(n, 0.003, d * 0.6) + pops(d, 40, 4000)
    elif el == "water":
        x = lowpass(noise(d), 3500) * env(n, 0.004, d * 0.6) + tone(np.linspace(900, 300, n), d) * env(n, 0.002, d * 0.4) * 0.5
    elif el == "wind":
        x = sweep_noise(d, 3000, 800, 0.6) + 0.3 * x
    elif el == "gravity":
        x = tone(np.linspace(120, 35, n), d, (1, 0.5)) * env(n, 0.002, d * 0.7, 2) + 0.3 * x
    elif el == "dark":
        x = lowpass(noise(d), 500) * env(n, 0.01, d * 0.8) + tone(np.linspace(90, 50, n), d) * env(n, 0.01, d * 0.8)
    elif el == "light":
        x = bell(1760, d, 0.7) + bell(2349, d, 0.4) + 0.2 * x
    else:
        x = tone(np.linspace(260, 160, n), d, (1, 0.6)) * env(n, 0.003, d * 0.7) + 0.4 * band(noise(d), 1500, 4000) * env(n, 0.003, d * 0.5)
    return fade(norm(x))


def max_charge(d=1.6):
    n = int(SR * d)
    f = np.linspace(110, 900, n) ** 1.0
    x = tone(f, d, (1, 0.5, 0.3)) * np.linspace(0.1, 1, n) * (0.7 + 0.3 * np.sin(2 * np.pi * np.linspace(4, 18, n) * np.arange(n) / SR))
    x += 0.5 * sweep_noise(d, 400, 6000, 0.7) * np.linspace(0.2, 1, n)
    x += 0.4 * bell(1760, d, 0.6) * np.linspace(0, 1, n)
    return fade(norm(x), 0.02, 0.06)


def max_release(d=1.4):
    n = int(SR * d)
    boom = tone(np.linspace(110, 28, n), d, (1, 0.6)) * env(n, 0.003, d, 3.0)
    crash = lowpass(noise(d), 4000) * env(n, 0.002, d * 0.5, 3.5)
    x = boom * 1.2 + crash * 0.9 + 0.4 * sweep_noise(d, 5000, 300, 0.6)
    x = echo(x, ((0.09, 0.4), (0.17, 0.25), (0.29, 0.15)))
    return fade(norm(x), 0.002, 0.08)


def telegraph(d=0.55):
    a = tone(740, 0.2, (1, 0.3)) * env(int(SR * 0.2), 0.005, 0.2, 3)
    b = tone(554, 0.3, (1, 0.3)) * env(int(SR * 0.3), 0.005, 0.3, 3)
    x = np.concatenate([a, b])
    return fade(norm(echo(x, ((0.12, 0.3),))[:int(SR * d)], 0.7))


def dodge(d=0.28):
    x = sweep_noise(d, 1800, 6000, 0.8) * env(int(SR * d), 0.01, d, 2.0)
    return fade(norm(x, 0.6))


def just_dodge(d=0.7):
    x = bell(1568, d, 1.0) + bell(2093, d, 0.7)
    x += 0.4 * sweep_noise(d, 800, 7000, 0.6) * env(int(SR * d), 0.01, d * 0.4)
    return fade(norm(x))


def reaction(d=0.7):
    n = int(SR * d)
    crack = pops(0.25, 120, 9000, 0.004)
    x = np.zeros(n)
    x[:len(crack)] += crack * 1.3
    x += tone(np.linspace(150, 45, n), d, (1, 0.5)) * env(n, 0.004, d * 0.6, 3)
    x += bell(2093, d, 0.35)
    return fade(norm(x))


def combo_up(d=0.55):
    notes = [784, 988, 1175, 1568]
    n = int(SR * d)
    x = np.zeros(n)
    for i, f in enumerate(notes):
        b = bell(f, 0.35, 0.8)
        o = int(SR * i * 0.07)
        x[o:o + len(b)] += b[:n - o]
    return fade(norm(x))


def boss_roar(d=1.7):
    n = int(SR * d)
    f = 75 + 20 * np.sin(np.linspace(0, 3.1, n)) - np.linspace(0, 25, n)
    x = saw(f, d) * 0.7
    x += 0.8 * band(noise(d), 200, 900) * (0.5 + 0.5 * np.sin(2 * np.pi * 14 * np.arange(n) / SR))
    x = lowpass(x, 1800) * (np.minimum(1, np.arange(n) / (SR * 0.15))) * np.exp(-np.arange(n) / SR * 0.9)
    x = echo(x, ((0.12, 0.3), (0.24, 0.18)))
    return fade(norm(x), 0.02, 0.1)


# ---------------------------------------------------------------------------------------------
# 武器の効果音と、敵・使い魔の鳴き声

def formants(x, fs, widths=0.18):
    out = np.zeros(len(x))
    for f, g in fs:
        out += g * band(x, f * (1 - widths), f * (1 + widths))
    return out


def swing_sound(kind):
    if kind == "blade":
        x = sweep_noise(0.26, 1400, 4800, 0.7) * 1.0
        return fade(norm(x, 0.7), 0.01, 0.06)
    if kind == "spear":
        x = sweep_noise(0.18, 3200, 1500, 0.4) + 0.4 * sweep_noise(0.18, 5000, 3000, 0.3)
        return fade(norm(x, 0.65), 0.005, 0.04)
    if kind == "dual":
        a = sweep_noise(0.12, 2000, 5200, 0.6)
        y = np.zeros(int(SR * 0.3))
        y[:len(a)] += a
        y[int(SR * 0.13):int(SR * 0.13) + len(a)] += a * 0.8
        return fade(norm(y, 0.65), 0.005, 0.05)
    if kind == "whirl":
        x = sweep_noise(0.5, 500, 2400, 0.6) * np.sin(np.linspace(0, np.pi, int(SR * 0.5))) ** 0.7
        return fade(norm(x, 0.7), 0.04, 0.1)
    x = sweep_noise(0.55, 250, 1000, 0.8)
    n = len(x)
    thump = tone(np.linspace(110, 40, int(SR * 0.25)), 0.25) * np.exp(-np.arange(int(SR * 0.25)) / SR * 14)
    x[int(n * 0.55):int(n * 0.55) + len(thump)] += thump[:n - int(n * 0.55)] * 1.2 if n - int(n * 0.55) < len(thump) else thump * 1.2
    return fade(norm(x, 0.8), 0.03, 0.1)


def hit_sound(kind):
    d = 0.3
    n = int(SR * d)
    if kind == "slash":
        x = band(noise(d), 1800, 9000) * env(n, 0.002, 0.07, 3)
        x += 0.5 * bell(3400, d) * env(n, 0.001, 0.2, 2)
        return fade(norm(x, 0.8), 0.001, 0.05)
    if kind == "blunt":
        x = lowpass(noise(d), 900) * env(n, 0.002, 0.08, 3) * 0.8
        x += tone(np.linspace(150, 50, n), d) * env(n, 0.002, 0.18, 3)
        return fade(norm(x, 0.85), 0.001, 0.06)
    d = 0.9
    n = int(SR * d)
    x = band(noise(d), 1200, 8000) * env(n, 0.002, 0.12, 3)
    x += lowpass(noise(d), 600) * env(n, 0.002, 0.25, 3) * 0.9
    x += tone(np.linspace(90, 30, n), d) * env(n, 0.002, 0.5, 3) * 1.1
    x += 0.4 * bell(1800, d) * env(n, 0.001, 0.4, 2)
    x = echo(x, ((0.09, 0.3), (0.19, 0.16)))
    return fade(norm(x, 0.9), 0.001, 0.15)


KINDS = ["beast", "bat", "slime", "spirit", "humanoid", "golem", "dragon", "deep"]


def voice(kind, cat):
    """鳴き声。cat は idle / hurt / death。"""
    D = {"idle": 0.7, "hurt": 0.35, "death": 1.0}[cat]
    n = int(SR * D)
    ev = env(n, 0.01, D * 0.6, 2.5)
    if kind == "beast":
        f = {"idle": [95, 70], "hurt": [520, 260], "death": [340, 70]}[cat]
        x = saw(np.array(f, dtype=float), D) * 0.6 + 0.7 * band(noise(D), 200, 900)
        x = formants(x, [(500, 1.0), (1100, 0.5)]) * (0.6 + 0.4 * np.sin(2 * np.pi * (22 if cat == "idle" else 9) * np.arange(n) / SR))
    elif kind == "bat":
        f = {"idle": [3200, 4600, 3300, 4800], "hurt": [5200, 2600], "death": [4800, 900]}[cat]
        x = tone(np.array(f, dtype=float), D, (1, 0.3)) * (0.5 + 0.5 * np.sin(2 * np.pi * (18 if cat == "idle" else 30) * np.arange(n) / SR) ** 2)
    elif kind == "slime":
        f = {"idle": [180, 260, 160], "hurt": [420, 120], "death": [300, 60]}[cat]
        base = np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(f)), f)
        x = tone(base * (1 + 0.25 * np.sin(2 * np.pi * 11 * np.arange(n) / SR)), D, (1, 0.4))
        x += pops(D, 30, 1800, 0.03) * 1.2
    elif kind == "spirit":
        f = {"idle": [820, 1250, 900], "hurt": [1500, 700], "death": [500, 1800]}[cat]
        x = tone(np.array(f, dtype=float), D, (1, 0.25)) * (0.7 + 0.3 * np.sin(2 * np.pi * 7 * np.arange(n) / SR))
        x += 0.5 * bell(f[0], D) * env(n, 0.001, D, 2)
        x = echo(x, ((0.1, 0.4), (0.2, 0.25)))[:n]
    elif kind == "humanoid":
        f = {"idle": [135, 120, 130], "hurt": [210, 120], "death": [190, 55]}[cat]
        x = saw(np.array(f, dtype=float), D) * 0.8 + 0.25 * noise(D)
        x = formants(x, [(700, 1.0), (1150, 0.7), (2600, 0.25)])
    elif kind == "golem":
        x = lowpass(noise(D), 400) * 0.8
        if cat == "idle":
            x = x * (0.5 + 0.5 * np.sin(2 * np.pi * 5 * np.arange(n) / SR)) + band(noise(D), 150, 500) * 0.4
        else:
            for k in range(1 if cat == "hurt" else 5):
                i = int(SR * (0.02 + k * 0.13 * RNG.random() + k * 0.08))
                L = min(n - i, int(SR * 0.2))
                if L > 0:
                    x[i:i + L] += band(noise(L / SR), 300, 2400) * np.exp(-np.arange(L) / SR * 18) * 1.6
                    x[i:i + L] += tone(np.linspace(120, 45, L), L / SR) * np.exp(-np.arange(L) / SR * 10)
    elif kind == "dragon":
        f = {"idle": [75, 60], "hurt": [260, 110], "death": [230, 38]}[cat]
        x = saw(np.array(f, dtype=float), D) * 0.7 + 0.9 * band(noise(D), 150, 1200)
        x = formants(x, [(380, 1.0), (900, 0.6), (1700, 0.3)], 0.25) * (0.7 + 0.3 * np.sin(2 * np.pi * 17 * np.arange(n) / SR))
        x = echo(x, ((0.13, 0.3), (0.27, 0.18)))[:n]
    else:  # deep
        f = {"idle": [120, 210, 140], "hurt": [90, 55], "death": [160, 30]}[cat]
        x = tone(np.array(f, dtype=float), D, (1, 0.5, 0.2))
        x += 0.3 * pops(D, 12, 900, 0.05)
        x = echo(x, ((0.15, 0.4), (0.3, 0.25)))[:n]
    x = x[:n] * ev
    return fade(norm(x, 0.8), 0.01, 0.08)


def write_ogg(name, x):
    path = os.path.join(ASSETS, "sounds", name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32000).astype(np.int16)
    with tempfile.TemporaryDirectory() as td:
        wav = os.path.join(td, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", path], check=True)


def main():
    elements = ["wind", "thunder", "fire", "water", "gravity", "dark", "light", "curse"]
    sounds = {}
    for el in elements:
        write_ogg("cast_" + el, cast(el, 0.8))
        write_ogg("impact_" + el, impact(el))
        sounds["cast_" + el] = "魔法の詠唱(%s)" % el
        sounds["impact_" + el] = "魔法の着弾(%s)" % el
    for name, fn in (("max_charge", max_charge), ("max_release", max_release), ("telegraph", telegraph), ("dodge", dodge),
                     ("just_dodge", just_dodge), ("reaction", reaction), ("combo_up", combo_up), ("boss_roar", boss_roar)):
        write_ogg(name, fn())
        sounds[name] = name
    for kind in ("blade", "spear", "dual", "whirl", "heavy"):
        write_ogg("swing_" + kind, swing_sound(kind))
        sounds["swing_" + kind] = "武器の振り(%s)" % kind
    for kind in ("slash", "blunt", "finisher"):
        write_ogg("hit_" + kind, hit_sound(kind))
        sounds["hit_" + kind] = "武器の命中(%s)" % kind
    for kind in KINDS:
        for cat in ("idle", "hurt", "death"):
            write_ogg("voice_%s_%s" % (kind, cat), voice(kind, cat))
            sounds["voice_%s_%s" % (kind, cat)] = "鳴き声(%s・%s)" % (kind, cat)
    js = {k: {"sounds": ["%s:%s" % ("magicmod", k)], "subtitle": "subtitles.magicmod.%s" % k} for k in sounds}
    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(js, f, ensure_ascii=False, indent=2)
    sub = {"subtitles.magicmod.%s" % k: v for k, v in sounds.items()}
    for lp in ("ja_jp", "en_us"):
        path = os.path.join(ASSETS, "lang", lp + ".json")
        cur = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
        cur.update(sub)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(cur, f, ensure_ascii=False, indent=2)
    print("sounds:", len(sounds))


if __name__ == "__main__":
    main()
