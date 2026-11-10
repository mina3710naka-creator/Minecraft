#!/usr/bin/env python3
"""Minecraft 本体の音符ブロックの音を、実際に取り寄せて測り、楽器ごと・音域ごとの音量の補正表(SongTrim.java)を作る。

    python3 measure_note_sounds.py [Minecraft のバージョン(標準 26.3)] [基準の大きさ dB(標準 -34)]

やること:
  1. Mojang の配布元から、そのバージョンの sounds.json と、音符ブロックの音(note/*.ogg)を取り寄せる(ネットが必要)。
  2. 音符値 0〜24 ごとに、実際の再生と同じ音の高さ(2^((音符値 − 12) / 12) 倍の速さ)で作り直して、
     A 特性で補正した大きさ(0.4 秒の窓の最大、dB)を測る。
  3. 基準の大きさより大きい音を、その大きさまで下げる係数にして、SongTrim.java に書く
     (小さい音は、音量 1 が上限なので持ち上げられない)。
必要なもの: ffmpeg、numpy、soundfile
"""
import json
import os
import subprocess
import sys
import tempfile
import urllib.request

import numpy as np
import soundfile as sf

SR = 44100
# 楽器の名前(NoteBlockInstrument の定数名) → sounds.json の block.note_block.* に対応する音
EVENTS = {"BASS": "bass", "DIDGERIDOO": "didgeridoo", "GUITAR": "guitar", "HARP": "harp", "BIT": "bit", "BANJO": "banjo",
          "PLING": "pling", "IRON_XYLOPHONE": "iron_xylophone", "COW_BELL": "cow_bell", "FLUTE": "flute", "BELL": "bell",
          "CHIME": "chime", "XYLOPHONE": "xylophone", "BASEDRUM": "basedrum", "SNARE": "snare", "HAT": "hat"}
PERCUSSION = {"BASEDRUM", "SNARE", "HAT"}  # 音の高さを変えない


def fetch(url, dest=None):
    with urllib.request.urlopen(url, timeout=60) as r:
        data = r.read()
    if dest:
        with open(dest, "wb") as f:
            f.write(data)
    return data


def a_weight(n, sr):
    f2 = np.fft.rfftfreq(n, 1 / sr) ** 2
    ra = (12194.0 ** 2 * f2 ** 2) / ((f2 + 20.6 ** 2) * np.sqrt((f2 + 107.7 ** 2) * (f2 + 737.9 ** 2)) * (f2 + 12194.0 ** 2) + 1e-30)
    return ra / 1.2589  # 1 kHz で約 1(0 dB)


def level_db(y):
    pad = np.pad(y, (0, max(0, SR - len(y))))  # 短い音は、1 秒まで 0 でのばす
    yw = np.fft.irfft(np.fft.rfft(pad) * a_weight(len(pad), SR), n=len(pad))
    w = min(int(0.4 * SR), len(yw))
    c = np.cumsum(np.concatenate([[0], yw ** 2]))
    return 20 * np.log10(np.sqrt((c[w:] - c[:-w]) / w).max() + 1e-12)


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else "26.3"
    target = float(sys.argv[2]) if len(sys.argv) > 2 else -34.0
    manifest = json.loads(fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
    vurl = next(v["url"] for v in manifest["versions"] if v["id"] == version)
    objects = json.loads(fetch(json.loads(fetch(vurl))["assetIndex"]["url"]))["objects"]

    def asset(key):
        h = objects[key]["hash"]
        return fetch(f"https://resources.download.minecraft.net/{h[:2]}/{h}")

    sounds = json.loads(asset("minecraft/sounds.json"))
    table = {}
    with tempfile.TemporaryDirectory() as td:
        for inst, ev in EVENTS.items():
            name = sounds[f"block.note_block.{ev}"]["sounds"][0]
            name = name if isinstance(name, str) else name["name"]
            ogg, wav = os.path.join(td, "a.ogg"), os.path.join(td, "a.wav")
            with open(ogg, "wb") as f:
                f.write(asset("minecraft/sounds/" + name.replace("minecraft:", "") + ".ogg"))
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", ogg, "-ac", "1", "-ar", str(SR), wav], check=True)
            y, _ = sf.read(wav, dtype="float64")
            levels = []
            for note in range(25):
                pitch = 1.0 if inst in PERCUSSION else 2 ** ((note - 12) / 12)
                z = np.interp(np.arange(0, len(y) - 1, pitch), np.arange(len(y)), y)
                levels.append(level_db(z))
            table[inst] = [round(min(1.0, 10 ** ((target - lv) / 20)), 3) for lv in levels]
            print(f"{inst:15s} {name:24s} 最小 {min(levels):6.1f} dB  最大 {max(levels):6.1f} dB", file=sys.stderr)
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "java", "com", "songmod", "SongTrim.java")
    entries = ",\n".join('\t\t\tMap.entry("%s", new float[] { %s })' % (i, ", ".join("%.3ff" % x for x in v)) for i, v in table.items())
    with open(out, "w", encoding="utf-8") as f:
        f.write(f'''package com.songmod;

import java.util.Map;

/**
 * 楽器ごと・音域ごとの、音量の補正(1.0 = 下げない)。Minecraft {version} の音符ブロックの音(note/*.ogg)を、実際の再生と同じように
 * 音の高さを変えて(2^((音符値 − 12) / 12) 倍)、A 特性で補正した大きさ(0.4 秒の窓の最大)を測り、
 * 基準の大きさ({target:.0f} dB)より大きい音を、その大きさまで下げる係数にしたもの。小さい音は、音量 1 が上限なので、持ち上げられない。
 * (tools/measure_note_sounds.py で作る)
 */
final class SongTrim {{

	private static final Map<String, float[]> TABLE = Map.ofEntries(
{entries}
	);

	private SongTrim() {{
	}}

	/** 楽器の名前(NoteBlockInstrument の定数名)と音符値(0〜24)の、補正の係数。知らない楽器は 1.0 */
	static float of(String instrument, int note) {{
		float[] t = TABLE.get(instrument);
		return t == null ? 1.0F : t[Math.max(0, Math.min(24, note))];
	}}
}}
''')
    print("書きました:", os.path.normpath(out), file=sys.stderr)


if __name__ == "__main__":
    main()
