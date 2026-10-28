"""audio_to_notes.py / video_to_notes.py 共通の処理(音域の折り返し・同時発音の調整・JSON 書き出し)。"""
import json
import sys

TICK = 0.1  # レッドストーンリピーター 1 目盛りの秒数
LOW, HIGH = 30, 102  # 音符ブロックで出せる範囲(低音 F#1 〜 高音 F#7)


def fold(p):
    while p < LOW:
        p += 12
    while p > HIGH:
        p -= 12
    return p


def limit_polyphony(by_time, cap, spread=True):
    """by_time: {tick: [(強さ, MIDI), ...]}。1 時刻 cap 音までにする。
    あふれた音は、いちばん高い音と低い音を残して、真ん中の音を 1 tick(0.1 秒)前か後ろへずらす。
    ずらせなければ弱い順に省く。返り値: ({tick: [midi...]}, 省いた数)"""
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
    row = '"row_length": "line", ' if row_length < 0 else '"row_length": %d, ' % row_length if row_length else ""
    with open(path, "w", encoding="utf-8") as f:
        f.write('{"name": %s, "tick": %s, %s"notes": [\n' % (json.dumps(name, ensure_ascii=False), TICK, row))
        f.write(",\n".join(json.dumps([t - base, m]) for t, m in notes))
        f.write("\n]}\n")
    return len(notes), (notes[-1][0] - base) * TICK if notes else 0


def report(path, count, seconds, dropped, moved):
    print(f"{path}: {count} 音, 長さ {seconds:.1f} 秒, 隣の 0.1 秒へずらした音 {moved}, 同時発音の上限で省いた音 {dropped}", file=sys.stderr)
