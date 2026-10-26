#!/usr/bin/env python3
"""モンキー・D・ルフィの像。1 ブロック = 1 cm(100 ブロック = 1 m)、身長 174 cm(麦わら帽子を含めると約 188 cm)。
ポーズ: 帽子のつばをつかんでニッと笑う、ルフィの代表的なポーズ(右手=向かって左で帽子を押さえる)。
実行: python3 tools/make_luffy.py  → src/main/resources/statuemod/defaults/luffy.json
"""
import os, sys, math
sys.path.insert(0, os.path.dirname(__file__))
from voxlib import Vox

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "statuemod", "defaults", "luffy.json")

PAL = {
    "s": "minecraft:white_terracotta",       # 肌
    "r": "minecraft:red_terracotta",         # 傷
    "h": "minecraft:black_concrete",         # 髪
    "k": "minecraft:black_concrete",         # 目・口の中
    "w": "minecraft:white_concrete",         # 白目・歯
    "V": "minecraft:red_concrete",           # ベスト
    "B": "minecraft:blue_concrete",          # 短パン
    "Y": "minecraft:yellow_concrete",        # 腰の黄色いサッシュ
    "o": "minecraft:dark_oak_planks",        # ぞうりの底
    "t": "minecraft:black_concrete",         # ぞうりの鼻緒
    "H": "minecraft:yellow_wool",            # 麦わら帽子
    "J": "minecraft:honeycomb_block",        # 帽子の影(つばの裏)
    "R": "minecraft:red_concrete",           # 帽子の赤いリボン
    "m": "minecraft:pink_terracotta",        # 口の中の舌・影
}

v = Vox()
SK = "s"

# 向かって左 = ルフィの右側(x が小さい側)。体の中心は x=0, z=0。
def side(sign, f):  # sign=-1: 向かって左(ルフィの右), +1: 向かって右(ルフィの左)
    return f(sign)

# ---------------- 脚・短パン・足 ----------------
for sg in (-1, 1):
    hip, knee, ank = (sg * 7.5, 88, 0), (sg * 9.5, 48, -1), (sg * 10.5, 9, 2)
    def shorts(x, y, z):
        return "B" if y >= 58 else SK
    v.capsule(hip, knee, 8.2, 5.8, shorts)
    v.capsule(knee, ank, 5.6, 3.7, SK)
    # 足(ぞうり)
    xc = sg * 10.5
    v.ellipsoid(xc, 1.5, -5, 5.6, 1.6, 14, "o", cond=lambda x, y, z: y < 3)     # 底
    v.capsule((xc, 8, 1.5), (xc, 4.8, -13), 4.2, 3.0, SK)                         # 足の甲
    v.ellipsoid(xc, 5, 3.5, 3.8, 3.0, 3.8, SK)                                    # かかと
    # 鼻緒(甲の上を横切る)
    for z in (-7, -6):
        for ix in range(int(xc - 4.5), int(xc + 4.5) + 1):
            for iy in range(3, 9):
                if (ix, iy, z) in v.c and v.c[(ix, iy, z)] == SK and iy >= 4:
                    v.c[(ix, iy, z)] = "t"

# ---------------- 骨盤(短パン)・胴・サッシュ ----------------
v.loft([(82, 0, 0, 14.5, 9.8), (92, 0, 0, 14.2, 9.4), (100, 0, 0, 12.8, 8.4)], "B")

OPEN = 6.5
def torso_ch(x, y, z):
    if y < 105:
        return SK
    if z < 0.4 and abs(x) <= OPEN:
        return SK                      # ベストの前開き
    return "V"
v.loft([(100, 0, 0, 12.6, 8.2), (108, 0, 0.3, 13.0, 8.6), (118, 0, 0.5, 16.2, 9.8),
        (128, 0, 0.5, 18.2, 10.4), (137, 0, 0.3, 19.2, 9.8), (142, 0, 0.5, 15.0, 8.0), (144.5, 0, 1.5, 7.5, 6.5)],
       torso_ch)
# 肩
for sg in (-1, 1):
    v.ellipsoid(sg * 18.8, 136.5, 1, 6.2, 6.0, 6.2, "s")

# 胸の X 字の傷(正面の肌の面にだけ描く)
def x_scar():
    cx, cy = 0.0, 122.0
    n = 60
    for i in range(n + 1):
        t = i / n
        for (x0, y0, x1, y1) in ((-6.5, cy + 11, 6.5, cy - 11), (6.5, cy + 11, -6.5, cy - 11)):
            x = x0 + (x1 - x0) * t; y = y0 + (y1 - y0) * t
            for ox in (0, 1):  # 太さ 2cm
                ix, iy = int(math.floor(x)) + ox - 0, int(math.floor(y))
                z = v.front_z(ix, iy, -20, 3)
                if z is not None and v.get(ix, iy, z) == SK:
                    v.c[(ix, iy, z)] = "r"
                    if v.get(ix, iy, z + 1) == SK:
                        v.c[(ix, iy, z + 1)] = "r"
x_scar()

# サッシュ(黄色い腰帯)+ 結び目と垂れ
v.loft([(96.5, 0, 0, 14.4, 9.8), (100, 0, 0, 13.8, 9.2), (104.5, 0, 0, 13.4, 8.8)], "Y", grow=1.0)
v.ellipsoid(7, 100.5, -9.8, 4.2, 4.0, 3.0, "Y")
v.capsule((6, 98, -10.5), (9, 78, -10.5), 2.2, 1.6, "Y")
v.capsule((8, 98, -10.5), (2.5, 80, -10.8), 2.2, 1.6, "Y")

# ---------------- 首・頭 ----------------
v.capsule((0, 141, 1.5), (0, 149, 1.0), 5.2, 4.8, SK)
HEAD_BOTTOM = 148
v.ellipsoid(0, 163.5, -1, 9.6, 10.8, 10.2, SK)             # 頭の上側
v.ellipsoid(0, 156.5, -1.3, 8.6, 8.4, 9.2, SK)             # あご
# 耳
for sg in (-1, 1):
    v.ellipsoid(sg * 9.6, 162.5, 1.0, 1.6, 3.0, 2.2, SK)
# 鼻
v.ellipsoid(0, 160.2, -11.0, 1.8, 2.0, 1.8, SK)

# 髪: 前髪(つばの下)・もみあげ・後頭部
def head_hair(x, y, z):
    return "h"
for (ix, iy, iz), ch in list(v.c.items()):
    if ch != SK or iy < 148:
        continue
    x, y, z = ix + .5, iy + .5, iz + .5
    if not (-10.5 <= x <= 10.5 and -13 <= z <= 10):
        continue
    if y >= 166.5 or (abs(x) >= 8.3 and y >= 158 and z > -5) or (z > 1.5 and y >= 153):
        v.c[(ix, iy, iz)] = "h"
# 前髪のぎざぎざ
for x in range(-8, 9):
    drop = (0, 1, 2, 1, 0, 2, 1)[x % 7]
    for yy in range(int(166.5 - drop), 167):
        z = v.front_z(x, yy, -14, -4)
        if z is not None and v.get(x, yy, z) == SK:
            v.c[(x, yy, z)] = "h"

# 顔: 目・眉・口・ほおの傷(頭の正面の面にだけ描く。ボクセル単位)
ZR = (-14, -4)
def P(ix, iy, ch, depth=2):
    v.paint_front(ix + .5, iy + .5, ch, depth, *ZR)
for sg in (-1, 1):
    cx = sg * 4.7
    for ix in range(-12, 13):
        for iy in range(155, 172):
            x, y = ix + .5, iy + .5
            if ((x - cx) / 1.6) ** 2 + ((y - 162.6) / 2.6) ** 2 <= 1:
                P(ix, iy, "k")
    P(-5 if sg < 0 else 4, 164, "w")                                    # 目のハイライト
# 大きな笑い口(歯を見せる)
for ix in range(-9, 10):
    x = ix + .5
    top = 155.8 + 0.075 * x * x
    depth = 5.0 * max(0.0, 1 - (x / 7.6) ** 2)
    for iy in range(148, 162):
        y = iy + .5
        if top - depth <= y <= top:
            if y >= top - 1.0 and abs(x) < 6.6:
                ch = "w"
            elif y <= top - depth + 1.0 and abs(x) < 3.5 and depth > 3:
                ch = "m"
            else:
                ch = "k"
            P(ix, iy, ch)
# 左目の下の傷(向かって右)
for ix in (4, 5, 6, 7):
    P(ix, 159, "r")
P(5, 158, "r"); P(5, 160, "r"); P(7, 158, "r"); P(7, 160, "r")

# ---------------- 腕 ----------------
# 向かって右(ルフィの左)は自然に下ろして握りこぶし
S = (18.8, 136.5, 1)
E = (25.5, 111.5, 2.5)
W = (26.8, 90.0, 5.0)
v.capsule(S, E, 5.6, 4.6, SK)
v.capsule(E, W, 4.6, 3.6, SK)
v.ellipsoid(27.2, 83.5, 6.0, 4.4, 5.4, 4.0, SK)
v.ellipsoid(29.5, 84.5, 3.5, 1.9, 4.0, 2.0, SK)            # 親指

# ---------------- 麦わら帽子 ----------------
HC = (0, -1)       # 帽子の中心(x,z)
BR_Y = 169.0
def brim(x, y, z):
    r2 = (x - HC[0]) ** 2 + (z - HC[1]) ** 2
    if r2 > 27.0 ** 2:
        return None
    lift = 0.0032 * r2
    if BR_Y + lift <= y < BR_Y + 2.0 + lift:
        return "J" if y < BR_Y + 1.0 + lift else "H"
    return None
v.fill(((-27, 27), (BR_Y, BR_Y + 6), (-28, 26)), lambda x, y, z: brim(x, y, z) is not None, brim)
def crown(x, y, z):
    if y < BR_Y + 1.5:
        return False
    return ((x - HC[0]) / 12.4) ** 2 + ((z - HC[1]) / 13.2) ** 2 + ((y - (BR_Y + 1.5)) / 17.0) ** 4 <= 1.0
def crown_ch(x, y, z):
    if BR_Y + 2.0 <= y < BR_Y + 8.0:
        return "R"
    return "H" if (int(x) + int(z) + int(y)) % 7 else "J"
v.fill(((-13, 13), (BR_Y + 1.5, BR_Y + 19), (-15, 14)), crown, crown_ch)

# ---------------- 帽子をつかむ右手(向かって左) ----------------
S = (-18.8, 136.5, 1)
E = (-44.0, 129.0, -1.0)
W = (-33.5, 154.5, -4.5)
v.capsule(S, E, 5.6, 4.6, SK)
v.capsule(E, W, 4.6, 3.6, SK)
v.ellipsoid(-27.5, 164.5, -6.0, 5.0, 5.6, 4.6, SK)          # 握りこぶし(つばをつかむ)
v.ellipsoid(-22.8, 169.3, -8.0, 2.4, 2.2, 3.4, SK)          # 親指をつばの上に
for k in range(4):                                           # 指の線
    v.paint_front(-30 + k * 2.0, 164.5, "s", 1, -20, -2)

text, size, n = v.dump("モンキー・D・ルフィ", PAL, comment=(
    "1ブロック=1cm(100ブロック=1m)。身長174cm(麦わら帽子込みで約188cm)。帽子をつかんでニッと笑うポーズ。"
    "layers の先頭が1層目(いちばん下)。各層の先頭の行が像の正面、各行は正面から見て左→右、空白は何も置かない。"))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w", encoding="utf-8").write(text)
print("luffy.json", "size(x,z,y)=", size, "blocks=", n, "bytes=", len(text))
