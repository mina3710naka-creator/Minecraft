#!/usr/bin/env python3
"""同梱サンプル像の JSON を作る。実行: python3 tools/make_statues.py
座標は x=正面から見て左→右, y=下→上, z=正面(0)→奥。出力は src/main/resources/statuemod/defaults/ 。"""
import json, os, math

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "statuemod", "defaults")


class Vox:
    def __init__(self):
        self.c = {}

    def box(self, x0, x1, y0, y1, z0, z1, ch):
        """両端を含む直方体"""
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.c[(x, y, z)] = ch

    def set(self, x, y, z, ch):
        self.c[(x, y, z)] = ch

    def face(self, x0, ytop, z, rows):
        """正面(z)の面に絵を貼る。rows[0] が ytop の行。'.' は貼らない"""
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != ".":
                    self.c[(x0 + dx, ytop - dy, z)] = ch

    def dump(self, name, palette, replace_air=False):
        xs = [k[0] for k in self.c]; ys = [k[1] for k in self.c]; zs = [k[2] for k in self.c]
        minx, miny, minz = min(xs), min(ys), min(zs)
        w = max(xs) - minx + 1; h = max(ys) - miny + 1; d = max(zs) - minz + 1
        layers = []
        for y in range(h):
            rows = []
            for z in range(d):
                rows.append("".join(self.c.get((minx + x, miny + y, minz + z), " ") for x in range(w)))
            layers.append(rows)
        used = {ch for k, ch in self.c.items()}
        pal = {k: v for k, v in palette.items() if k in used}
        text = "{\n"
        text += '  "name": %s,\n' % json.dumps(name, ensure_ascii=False)
        text += '  "comment": "layers の先頭が1層目(いちばん下)。各層の先頭の行が像の正面、空白は何も置かない。",\n'
        text += '  "palette": {\n' + ",\n".join("    %s: %s" % (json.dumps(k), json.dumps(v)) for k, v in pal.items()) + "\n  },\n"
        text += '  "layers": [\n'
        parts = []
        for i, rows in enumerate(layers):
            parts.append("    [ " + ", ".join(json.dumps(r) for r in rows) + " ]")
        text += ",\n".join(parts) + "\n  ]\n}\n"
        return text


def save(fname, text):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, fname), "w", encoding="utf-8") as f:
        f.write(text)
    print("wrote", fname)


# ---------- クリーパー(1ブロック=1px 相当、8x26x8) ----------
v = Vox()
G = lambda x, y, z: "g" if ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) % 5 == 0 else "G"
for (x0, z0) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
    for x in range(x0, x0 + 4):
        for y in range(0, 6):
            for z in range(z0, z0 + 4):
                v.set(x, y, z, G(x, y, z))
for x in range(0, 8):
    for y in range(6, 18):
        for z in range(2, 6):
            v.set(x, y, z, G(x, y, z))
for x in range(0, 8):
    for y in range(18, 26):
        for z in range(0, 8):
            v.set(x, y, z, G(x, y, z))
v.face(0, 25, 0, [
    "........",
    "........",
    ".KK..KK.",
    ".KK..KK.",
    "...KK...",
    "..KKKK..",
    "..KKKK..",
    "..K..K..",
])
save("creeper.json", v.dump("クリーパー", {"G": "minecraft:lime_concrete", "g": "minecraft:green_concrete", "K": "minecraft:black_concrete"}))

# ---------- スティーブ(8+12+... 全長32, 横16, 奥行8) ----------
v = Vox()
v.box(4, 7, 0, 11, 2, 5, "P"); v.box(8, 11, 0, 11, 2, 5, "P")           # 脚(ズボン)
v.box(4, 7, 0, 1, 2, 5, "S"); v.box(8, 11, 0, 1, 2, 5, "S")             # 靴
v.box(4, 11, 12, 23, 2, 5, "T")                                          # 胴(シャツ)
v.box(0, 3, 12, 23, 2, 5, "T"); v.box(12, 15, 12, 23, 2, 5, "T")         # 腕
v.box(0, 3, 12, 14, 2, 5, "A"); v.box(12, 15, 12, 14, 2, 5, "A")         # 手(肌)
v.box(4, 11, 24, 31, 0, 7, "A")                                          # 頭
v.box(4, 11, 30, 31, 0, 7, "H"); v.box(4, 11, 24, 31, 7, 7, "H")         # 髪(上と後ろ)
v.box(4, 4, 28, 29, 0, 7, "H"); v.box(11, 11, 28, 29, 0, 7, "H")
v.face(4, 29, 0, [
    "HHHHHHHH",
    "H......H",
    ".WB..BW.",
    "........",
    "..MMMM..",
    "........",
])
save("steve.json", v.dump("スティーブ", {
    "A": "minecraft:orange_terracotta", "H": "minecraft:dark_oak_planks", "T": "minecraft:cyan_concrete",
    "P": "minecraft:blue_concrete", "S": "minecraft:gray_concrete", "W": "minecraft:white_concrete",
    "B": "minecraft:blue_terracotta", "M": "minecraft:brown_terracotta"}))

# ---------- ブタ(ピンク, 幅10 長さ24 高さ14) ----------
v = Vox()
# 向きは「正面=頭」。頭が手前(z 0..7)、胴が奥(z 8..23)
for (x0, z0) in [(1, 9), (7, 9), (1, 20), (7, 20)]:
    v.box(x0, x0 + 1, 0, 5, z0, z0 + 1, "p")
v.box(0, 9, 4, 11, 8, 23, "p")                      # 胴
v.box(1, 8, 4, 11, 0, 7, "p")                       # 頭
v.box(3, 6, 5, 7, 0, 0, "N"); v.box(3, 6, 5, 7, 0, 0, "N")  # 鼻
v.set(4, 6, 0, "n"); v.set(5, 6, 0, "n")
v.set(2, 9, 0, "W"); v.set(7, 9, 0, "W")            # 目
v.set(3, 9, 0, "K"); v.set(6, 9, 0, "K")
save("pig.json", v.dump("ブタ", {"p": "minecraft:pink_concrete", "N": "minecraft:pink_terracotta",
                                  "n": "minecraft:red_terracotta", "W": "minecraft:white_concrete",
                                  "K": "minecraft:black_concrete"}))

# ---------- 雪だるま ----------
v = Vox()
def sphere(cx, cy, cz, r, ch):
    for x in range(cx - r, cx + r + 1):
        for y in range(cy - r, cy + r + 1):
            for z in range(cz - r, cz + r + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2 <= r * r + r * 0.6:
                    v.set(x, y, z, ch)
sphere(5, 4, 5, 4, "S")
sphere(5, 11, 5, 3, "S")
sphere(5, 17, 5, 2, "S")
# 顔(正面は z が小さい側 = 頭の正面の表面に貼る)
def head_front(x, y):
    zs = [z for (xx, yy, z) in v.c if xx == x and yy == y and v.c[(xx, yy, z)] == "S"]
    return min(zs)
for (x, y, ch) in [(4, 18, "K"), (6, 18, "K"), (5, 17, "O"), (4, 16, "K"), (6, 16, "K")]:
    v.set(x, y, head_front(x, y), ch)
for y in (12, 10, 8):
    v.set(5, y, head_front(5, y), "K")
v.box(4, 6, 19, 19, 4, 6, "S")
v.set(5, 20, 5, "C")
save("snowman.json", v.dump("雪だるま", {"S": "minecraft:snow_block", "K": "minecraft:black_concrete",
                                         "O": "minecraft:orange_concrete", "C": "minecraft:carved_pumpkin[facing=south]"}))

# ---------- スライム(9x9x9) ----------
v = Vox()
v.box(0, 8, 0, 8, 0, 8, "L")
v.box(1, 7, 1, 7, 1, 7, " ")
v.box(2, 6, 2, 6, 2, 6, "C")
v.face(0, 6, 0, [
    "..K...K..",
    "..K...K..",
    "....M....",
])
save("slime.json", v.dump("スライム", {"L": "minecraft:slime_block", "C": "minecraft:lime_concrete",
                                       "K": "minecraft:black_concrete", "M": "minecraft:green_concrete"}))

# ---------- エンダーマン(1ブロック=2px, 6x25x4) ----------
v = Vox()
v.box(2, 2, 0, 14, 1, 1, "B"); v.box(3, 3, 0, 14, 1, 1, "B")   # 脚
v.box(1, 4, 15, 20, 0, 2, "B")                                    # 胴
v.box(0, 0, 6, 20, 1, 1, "B"); v.box(5, 5, 6, 20, 1, 1, "B")      # 腕(長い)
v.box(1, 4, 21, 24, 0, 3, "B")                                    # 頭
v.set(1, 22, 0, "E"); v.set(4, 22, 0, "E"); v.set(2, 22, 0, "e"); v.set(3, 22, 0, "e")
save("enderman.json", v.dump("エンダーマン", {"B": "minecraft:black_concrete", "E": "minecraft:magenta_concrete",
                                              "e": "minecraft:purple_concrete"}))
