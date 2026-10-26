#!/usr/bin/env python3
"""
.melody.json の全体の時間を、ずらす・伸ばす。音が曲より早いときは --shift で遅らせる。

    python melody_shift.py "曲.melody.json" --shift 800        # 0.8秒 遅くする(開始が遅くなる)
    python melody_shift.py "曲.melody.json" --shift -500       # 0.5秒 早くする
    python melody_shift.py "曲.melody.json" --scale 1.01       # 時間を 1% 伸ばす(後ろほど遅くなる)
    python melody_shift.py "曲.melody.json" --shift 800 --scale 1.01

元のファイルは「曲.melody.json.bak」に残る(すでにあれば上書きしない)。
時刻は   新しい時刻 = 元の時刻 × scale + shift   になる。
"""
import argparse
import json
import os
import shutil

ap = argparse.ArgumentParser()
ap.add_argument("file")
ap.add_argument("--shift", type=int, default=0, help="ミリ秒。プラスで遅く、マイナスで早く")
ap.add_argument("--scale", type=float, default=1.0, help="時間の倍率。1.01 で 1% 遅くなっていく")
a = ap.parse_args()

with open(a.file, encoding="utf-8") as f:
    data = json.load(f)
bak = a.file + ".bak"
if not os.path.exists(bak):
    shutil.copy(a.file, bak)
out = []
for n in data["notes"]:
    t = int(round(n["t"] * a.scale + a.shift))
    if t < 0:
        continue
    n["t"] = t
    n["d"] = max(1, int(round(n["d"] * a.scale)))
    out.append(n)
data["notes"] = out
with open(a.file, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False)
print(f"{len(out)} 個の音を書きました(shift={a.shift}ms, scale={a.scale})")
