#!/usr/bin/env python3
"""同梱サンプル像の JSON を作る。実行: python3 tools/make_statues.py
(ルフィは tools/make_luffy.py)。出力は src/main/resources/statuemod/defaults/ 。
ゲーム内の見た目に近い比率で、1 ピクセル = S ブロック(S は像ごと)で作る。"""
import os, sys, math
sys.path.insert(0, os.path.dirname(__file__))
from voxlib import Vox

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "statuemod", "defaults")


def hsh(x, y, z, salt=0):
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (salt * 2654435761)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


class PV(Vox):
    """ピクセル座標(整数)で組み立て、1 ピクセルを s×s×s ブロックにする"""
    def __init__(self, s):
        super().__init__(); self.s = s

    def pbox(self, x0, x1, y0, y1, z0, z1, ch):
        s = self.s
        self.box(x0 * s, (x1 + 1) * s - 1, y0 * s, (y1 + 1) * s - 1, z0 * s, (z1 + 1) * s - 1, ch)

    def pfront(self, x0, ytop, z, rows, zdepth=1):
        """正面(z)の面に絵を貼る。rows[0] が ytop の行、'.' は貼らない"""
        s = self.s
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != ".":
                    self.pbox(x0 + dx, x0 + dx, ytop - dy, ytop - dy, z, z + zdepth - 1, ch)


def save(fname, v, name, pal):
    text, size, n = v.dump(name, pal)
    os.makedirs(OUT, exist_ok=True)
    open(os.path.join(OUT, fname), "w", encoding="utf-8").write(text)
    print(fname, "size(x,z,y)=", size, "blocks=", n)


def noise(chars, weights, salt):
    """ブロックごとのノイズで色を選ぶ(質感)"""
    tot = sum(weights)
    def f(x, y, z):
        r = hsh(int(x), int(y), int(z), salt) * tot
        for c, w in zip(chars, weights):
            if r < w:
                return c
            r -= w
        return chars[-1]
    return f


# ======== クリーパー: 1px=2 ブロック(16 x 52 x 16) ========
v = PV(2)
tex = noise("GMg", (60, 25, 15), 1)
for (x0, z0) in [(0, 0), (4, 0), (0, 4), (4, 4)]:
    v.pbox(x0, x0 + 3, 0, 5, z0, z0 + 3, tex)
v.pbox(0, 7, 6, 17, 2, 5, tex)
v.pbox(0, 7, 18, 25, 0, 7, tex)
v.pfront(0, 25, 0, [
    "........",
    "........",
    ".KK..KK.",
    ".KK..KK.",
    "...KK...",
    "..KKKK..",
    "..KKKK..",
    "..K..K..",
], zdepth=1)
save("creeper.json", v, "クリーパー", {"G": "minecraft:lime_concrete", "M": "minecraft:lime_terracotta",
                                      "g": "minecraft:green_concrete", "K": "minecraft:black_concrete"})

# ======== スティーブ: 1px=2 ブロック(32 x 64 x 16) ========
v = PV(2)
skin = noise("AB", (92, 8), 2)
v.pbox(4, 7, 2, 11, 2, 5, "P"); v.pbox(8, 11, 2, 11, 2, 5, "P")       # 脚(ズボン)
v.pbox(4, 7, 0, 1, 2, 5, "S"); v.pbox(8, 11, 0, 1, 2, 5, "S")         # 靴
v.pbox(4, 11, 12, 23, 2, 5, "T")                                       # 胴
v.pbox(0, 3, 12, 23, 2, 5, skin); v.pbox(12, 15, 12, 23, 2, 5, skin)   # 腕(肌)
v.pbox(0, 3, 18, 23, 2, 5, "T"); v.pbox(12, 15, 18, 23, 2, 5, "T")     # 袖
v.pbox(4, 11, 24, 31, 0, 7, skin)                                      # 頭
v.pbox(4, 11, 31, 31, 0, 7, "H")                                       # 髪(上)
v.pbox(4, 11, 24, 31, 7, 7, "H")                                       # 髪(後ろ)
v.pbox(4, 4, 28, 31, 0, 7, "H"); v.pbox(11, 11, 28, 31, 0, 7, "H")     # 髪(横)
v.pfront(4, 31, 0, [
    "HHHHHHHH",
    "HHHHHHHH",
    "HAAAAAAH",
    "AAAAAAAA",
    "AWBAABWA",
    "AAAAAAAA",
    "AAMMMMAA",
    "AAAAAAAA",
], zdepth=1)
save("steve.json", v, "スティーブ", {
    "A": "minecraft:terracotta", "B": "minecraft:brown_terracotta", "H": "minecraft:brown_concrete",
    "T": "minecraft:cyan_concrete", "P": "minecraft:blue_concrete", "S": "minecraft:gray_concrete",
    "W": "minecraft:white_concrete", "M": "minecraft:brown_terracotta"})

# ======== ブタ: 1px=2 ブロック(20 x 28 x 52) ========
v = PV(2)
pig = noise("pq", (85, 15), 3)
for (x0, z0) in [(0, 10), (6, 10), (0, 20), (6, 20)]:
    v.pbox(x0, x0 + 3, 0, 5, z0, z0 + 3, "q")                       # 脚
v.pbox(0, 9, 6, 13, 9, 24, pig)                                    # 胴
v.pbox(1, 8, 5, 12, 1, 8, pig)                                     # 頭
v.pbox(3, 6, 5, 7, 0, 0, "N")                                      # 鼻
v.pbox(3, 3, 6, 6, 0, 0, "n"); v.pbox(6, 6, 6, 6, 0, 0, "n")
v.pfront(1, 11, 1, [                                               # 目
    "........",
    "........",
    ".WK..KW.",
    "........",
], zdepth=1)
v.pbox(2, 2, 13, 13, 5, 6, "q"); v.pbox(7, 7, 13, 13, 5, 6, "q")   # 耳
v.pbox(4, 5, 11, 12, 25, 25, "q")                                  # しっぽ
save("pig.json", v, "ブタ", {"p": "minecraft:pink_wool", "q": "minecraft:pink_concrete",
                             "N": "minecraft:pink_terracotta", "n": "minecraft:red_terracotta",
                             "W": "minecraft:white_concrete", "K": "minecraft:black_concrete"})

# ======== 雪だるま(雪の玉 3 段 + シルクハット + マフラー + 木の枝の腕 + にんじん鼻) ========
v = PV(1)
snow = noise("SF", (90, 10), 4)
v.ellipsoid(14, 9, 14, 9, 9, 9, snow)
v.ellipsoid(14, 22, 14, 7, 7, 7, snow)
v.ellipsoid(14, 33, 14, 5.2, 5.2, 5.2, snow)
# 前(z が小さい側)の位置
FZ = 14 - 5.2
# 顔: 石炭の目と笑った口、にんじんの鼻
def face_paint(x, y, ch):
    z = v.front_z(int(x), int(y), 0, 14)
    if z is not None:
        v.c[(int(x), int(y), z)] = ch
for (x, y) in [(11, 35), (17, 35), (11, 34), (17, 34)]:
    face_paint(x, y, "K")
for (x, y) in [(10, 32), (11, 31), (12, 30), (13, 30), (14, 30), (15, 30), (16, 30), (17, 31), (18, 32)]:
    face_paint(x, y, "K")
v.capsule((14.5, 33, 8.8), (14.5, 32.6, 3.2), 1.5, 0.6, "O")
# 胴の石炭ボタン
for y in (25, 22, 19):
    face_paint(14, y, "K")
# 腕(木の枝)
for sg in (-1, 1):
    v.capsule((14 + sg * 6, 24, 14), (14 + sg * 17, 31, 14), 0.9, 0.7, "W")
    v.capsule((14 + sg * 12, 27.5, 14), (14 + sg * 15, 24.5, 14), 0.7, 0.6, "W")
    v.capsule((14 + sg * 14.5, 29.6, 14), (14 + sg * 18, 35, 14), 0.6, 0.6, "W")
# マフラー
def scarf(x, y, z):
    r = math.hypot(x - 14, z - 14)
    return 4.4 <= r <= 6.7 and 27.2 <= y <= 30.4
v.fill(((7, 21), (27, 31), (7, 21)), scarf, "R")
v.box(16, 17, 20, 29, 8, 8, "R")                 # 前に垂れる端
v.box(17, 18, 23, 29, 8, 9, "R") if False else None
# シルクハット
def brim(x, y, z):
    return math.hypot(x - 14, z - 14) <= 7.2 and 37.5 <= y < 38.6
v.fill(((6, 22), (37, 39), (6, 22)), brim, "H")
def top(x, y, z):
    return math.hypot(x - 14, z - 14) <= 5.0 and 38.6 <= y < 47
v.fill(((8, 20), (38, 48), (8, 20)), top, lambda x, y, z: "R" if y < 41 else "H")
save("snowman.json", v, "雪だるま", {"S": "minecraft:snow_block", "F": "minecraft:white_concrete",
                                    "K": "minecraft:coal_block", "O": "minecraft:orange_concrete",
                                    "W": "minecraft:dark_oak_planks", "R": "minecraft:red_concrete",
                                    "H": "minecraft:black_concrete"})

# ======== スライム(16 x 16 x 16 の外殻 + 内側の核 + 顔) ========
v = PV(1)
v.box(0, 15, 0, 15, 0, 15, "L")
v.box(1, 14, 1, 14, 1, 14, None)
for k in list(v.c):
    x, y, z = k
    if 1 <= x <= 14 and 1 <= y <= 14 and 1 <= z <= 14:
        del v.c[k]
core = noise("CDc", (70, 20, 10), 5)
v.box(4, 11, 3, 10, 5, 12, core)
v.pfront(4, 9, 4, [
    "..........",
], 1) if False else None
for (x, y) in [(5, 8), (6, 8), (5, 7), (6, 7), (9, 8), (10, 8), (9, 7), (10, 7)]:
    v.c[(x, y, 5)] = "K"
for (x, y) in [(7, 5), (8, 5)]:
    v.c[(x, y, 5)] = "K"
save("slime.json", v, "スライム", {"L": "minecraft:slime_block", "C": "minecraft:lime_concrete",
                                  "D": "minecraft:green_concrete", "c": "minecraft:lime_terracotta",
                                  "K": "minecraft:black_concrete"})

# ======== エンダーマン: 1px=1 ブロック(12 x 8 x 50) ========
v = PV(1)
ender = noise("BDb", (80, 12, 8), 6)
v.pbox(2, 3, 0, 29, 3, 4, ender); v.pbox(6, 7, 0, 29, 3, 4, ender)     # 脚(長い)
v.pbox(2, 9, 30, 41, 2, 5, ender)                                       # 胴
v.pbox(0, 1, 12, 41, 3, 4, ender); v.pbox(10, 11, 12, 41, 3, 4, ender) # 腕(長い)
v.pbox(2, 9, 42, 49, 0, 7, ender)                                       # 頭
v.pfront(2, 49, 0, [
    "........",
    "........",
    "........",
    "........",
    "EeE..EeE",
    "KKK..KKK",
    "........",
    "........",
], zdepth=1)
save("enderman.json", v, "エンダーマン", {"B": "minecraft:black_concrete", "D": "minecraft:black_terracotta",
                                         "b": "minecraft:blackstone", "E": "minecraft:magenta_concrete",
                                         "e": "minecraft:pink_concrete", "K": "minecraft:black_concrete"})
