#!/usr/bin/env python3
"""モンキー・D・ルフィの像(参考画像: 帽子をかぶり、胸の前で両手を合わせて立つポーズ)。
スケール: 150 ブロック = 1 m(1 ブロック ≒ 0.667 cm)。身長 174 cm → 約 261 ブロック(帽子を入れて約 285)。
参考画像のピクセル座標(px)から、全身の比率をそのまま写している。
実行: python3 tools/make_luffy.py → src/main/resources/statuemod/defaults/luffy.json
"""
import os, sys, math, random
sys.path.insert(0, os.path.dirname(__file__))
from voxlib import Vox

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "statuemod", "defaults", "luffy.json")

PAL = {
    "s": "minecraft:white_terracotta",       # 肌
    "a": "minecraft:terracotta",             # 肌の影(腹筋・鎖骨)
    "p": "minecraft:pink_terracotta",        # 傷
    "h": "minecraft:black_concrete",         # 髪・眉・瞳
    "w": "minecraft:white_concrete",         # 白目・歯
    "l": "minecraft:red_terracotta",         # 唇・口の中
    "V": "minecraft:red_concrete",           # 赤いジャケット
    "v": "minecraft:red_wool",               # ジャケット(明るい部分)
    "g": "minecraft:yellow_terracotta",      # ジャケットのボタン
    "Y": "minecraft:yellow_concrete",        # 黄色いサッシュ
    "y": "minecraft:orange_concrete",        # サッシュの影
    "B": "minecraft:light_blue_concrete",    # 青い短パン
    "b": "minecraft:blue_concrete",          # 短パンの影
    "F": "minecraft:white_wool",             # すその白いファー
    "f": "minecraft:light_gray_wool",        # ファーの影
    "o": "minecraft:birch_planks",           # ぞうりの底
    "t": "minecraft:black_concrete",         # 鼻緒
    "H": "minecraft:birch_planks",           # 麦わら帽子
    "J": "minecraft:yellow_terracotta",      # 麦わら帽子(影・編み目)
    "M": "minecraft:sand",                   # 麦わら帽子(明るい編み目)
    "R": "minecraft:red_concrete",           # 帽子のリボン
}

S = 0.629                                    # 参考画像 1px = 0.629 ブロック(身長 174cm=261 ブロック)
def X(px): return (px - 292) * S
def Y(py): return (500 - py) * S
def R(px): return px * S
def P(px, py, z): return (X(px), Y(py), z)

v = Vox()
rnd = random.Random(7)
SK = "s"

# ---------------- 足・ぞうり ----------------
def foot(heel, toe, ground_r=6.4):
    # 底
    v.capsule((heel[0], 1.2, heel[2]), (toe[0], 1.2, toe[2]), ground_r, ground_r - 0.8, "o", cond=lambda x, y, z: y < 2.6)
    # 足の甲・かかと・つま先
    v.capsule((heel[0], 8.5, heel[2]), (toe[0], 4.6, toe[2]), 5.3, 3.6, SK, cond=lambda x, y, z: y >= 2.4)
    v.capsule((heel[0], 4.6, heel[2]), (heel[0], 9.0, heel[2] - 0.5), 4.6, 4.2, SK, cond=lambda x, y, z: y >= 2.4)
    # 鼻緒(親指と人差し指の間から、足の両側へ)
    dx, dz = toe[0] - heel[0], toe[2] - heel[2]
    L = math.hypot(dx, dz); ux, uz = dx / L, dz / L; nx, nz = -uz, ux
    tx, tz = toe[0] - ux * 7, toe[2] - uz * 7
    for sg in (-1, 1):
        mx, mz = heel[0] + ux * L * 0.40 + nx * sg * 5.2, heel[2] + uz * L * 0.40 + nz * sg * 5.2
        v.capsule((tx, 5.2, tz), (mx, 3.2, mz), 0.95, 0.95, "t", cond=lambda x, y, z: y >= 2.4)
    v.ellipsoid(tx, 5.2, tz, 1.3, 1.3, 1.3, "t")

foot(P(232, 478, 8), P(188, 480, -14))       # 向かって左の足(奥・左向き)
foot(P(334, 480, 14), P(350, 490, -22))      # 向かって右の足(手前・やや右向き)

# ---------------- 脚・短パン ----------------
HEM_L, HEM_R = 358, 372
def shorts_leg(hip, knee, hem_y, r_hip, r_hem, flare):
    """短パン(ゆるい筒)。上から裾まで、裾に向かって少し広がる"""
    v.capsule(hip, knee, r_hip, r_hem, "B", cond=lambda x, y, z: y >= Y(hem_y))
for sg, hem in ((-1, HEM_L), (1, HEM_R)):
    pass
# 向かって左(奥、外側に開く)
hipL, kneeL, ankL = P(270, 268, 3), P(231, 352, 4), P(220, 476, 6)
hipR, kneeR, ankR = P(308, 270, -1), P(327, 356, -3), P(341, 478, 9)
# 脚(肌)
v.capsule(kneeL, ankL, R(10.5), R(7.4), SK, cond=lambda x, y, z: y < Y(HEM_L) + 4)
v.capsule(kneeR, ankR, R(10.5), R(7.4), SK, cond=lambda x, y, z: y < Y(HEM_R) + 4)
# 短パンの筒(裾は膝のすこし下)
def tube(hip, knee, hem_y, r_hip, r_hem):
    d = [knee[i] - hip[i] for i in range(3)]
    L2 = sum(c * c for c in d)
    def inside(x, y, z):
        if y > Y(268) + 1 or y < Y(hem_y):
            return False
        t = max(0.0, min(1.0, ((x - hip[0]) * d[0] + (y - hip[1]) * d[1] + (z - hip[2]) * d[2]) / L2))
        r = r_hip + (r_hem - r_hip) * t
        q = [hip[i] + d[i] * t for i in range(3)]
        return (x - q[0]) ** 2 + (z - q[2]) ** 2 <= r * r and abs(y - q[1]) < 60
    return inside
def colorB(x, y, z):
    return "b" if hsh(int(x), int(y), int(z)) < 0.015 else "B"
def hsh(x, y, z):
    n = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791)
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0
extL = (kneeL[0] + (kneeL[0] - hipL[0]) * 0.07, kneeL[1] + (kneeL[1] - hipL[1]) * 0.07, kneeL[2])
extR = (kneeR[0] + (kneeR[0] - hipR[0]) * 0.07, kneeR[1] + (kneeR[1] - hipR[1]) * 0.07, kneeR[2])
v.fill(((X(180), X(260)), (Y(HEM_L), Y(262)), (-30, 30)), tube(hipL, extL, HEM_L, R(21), R(21.5)), colorB)
v.fill(((X(280), X(360)), (Y(HEM_R), Y(262)), (-30, 30)), tube(hipR, extR, HEM_R, R(21), R(25)), colorB)
# 裾の白いファー(ふさふさ)
def fur(hem_y, cx, rr, tilt):
    for iy in range(int(Y(hem_y + 15)), int(Y(hem_y - 4)) + 1):
        pass
    def inside(x, y, z):
        return True
for (kn, hem, rr) in ((extL, HEM_L, R(24.5)), (extR, HEM_R, R(28.5))):
    cx = kn[0] + (0 if hem == HEM_L else 0)
    def infur(x, y, z, kn=kn, hem=hem, rr=rr):
        if not (Y(hem + 20) <= y < Y(hem - 4)):
            return False
        # 裾の中心は脚の軸に沿って少しずれる
        return (x - kn[0]) ** 2 + (z - kn[2]) ** 2 <= rr * rr
    v.fill(((kn[0] - rr, kn[0] + rr), (Y(hem + 20), Y(hem - 4)), (kn[2] - rr, kn[2] + rr)), infur,
           lambda x, y, z: "F" if hsh(int(x * 1.3), int(y * 1.3), int(z * 1.3)) > 0.22 else "f")
# 骨盤(短パンの上部)
v.loft([(Y(285), X(292), 2.0, R(30), 10.8), (Y(268), X(292), 1.0, R(31), 11.5), (Y(250), X(292), 0.5, R(29.5), 11.0)], colorB)

# ---------------- 胴・ジャケット ----------------
def torso_ch(x, y, z):
    py = 500 - y / S
    dx = x - X(293)
    # 前開き(胸〜腹)
    if z < 1.0 and py > 146:
        half = R(12 + (min(py, 215) - 146) * 0.07 + (4 if py > 214 else 0))
        if abs(dx) <= half and py < 252:
            return SK
    return "v" if hsh(int(x), int(y), int(z)) < 0.15 else "V"
keys = []
for (py, cx, ax, az) in ((250, 292, 30, 11.0), (232, 292, 31.5, 11.5), (212, 292, 31.5, 11.5), (190, 293, 32.5, 12.0),
                         (165, 294, 34.5, 12.5), (151, 295, 36.5, 12.0), (145, 295, 29, 10.0)):
    keys.append((Y(py), X(cx), 1.0, R(ax), az))
keys.sort()
v.loft(keys, torso_ch)
# ジャケットのすそ(広がって、左右でゆれる)
v.loft([(Y(262), X(292), 1.0, R(33), 12.5), (Y(246), X(292), 1.0, R(37), 13.5), (Y(236), X(290), 1.5, R(45), 15.0)],
       lambda x, y, z: None if (z < 1.0 and abs(x - X(293)) <= R(21)) else "V")
v.capsule(P(246, 236, 2), P(236, 244, 4), R(7), R(3.5), "V")           # 向かって左のすその先
v.capsule(P(327, 232, 2), P(322, 240, 3), R(6), R(3), "V")             # 向かって右のすその先
# えり・ボタン
v.loft([(Y(150), X(294), 0.0, R(17), 12.0), (Y(143), X(294), 0.5, R(14), 11.0)],
       lambda x, y, z: None if (z < 1.0 and abs(x - X(294)) <= R(9)) else "V")
for (px, py) in ((274, 156), (273, 172), (272, 188)):
    z = v.front_z(int(X(px)), int(Y(py)), -30, 6)
    if z is not None:
        v.c[(int(X(px)), int(Y(py)), z)] = "g"

# 肩(ジャケット)
v.ellipsoid(X(262), Y(152), 0.5, R(13), R(13), 11.5, "V")
v.ellipsoid(X(330), Y(154), 0.5, R(13), R(13), 11.5, "V")

# 胸の傷(ギザギザの X)と、腹筋・鎖骨の影(正面の肌の面だけに描く)
def paint(px, py, ch, depth=1):
    ix, iy = int(math.floor(X(px))), int(math.floor(Y(py)))
    z = v.front_z(ix, iy, -30, 4)
    if z is not None and v.get(ix, iy, z) in (SK, "a"):
        for d in range(depth):
            if v.get(ix, iy, z + d) in (SK, "a"):
                v.c[(ix, iy, z + d)] = ch
def pline(p0, p1, ch, step=0.6, depth=1, jitter=0.0):
    n = int(max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1])) / step) + 1
    for i in range(n + 1):
        t = i / n
        paint(p0[0] + (p1[0] - p0[0]) * t + rnd.uniform(-jitter, jitter),
              p0[1] + (p1[1] - p0[1]) * t + rnd.uniform(-jitter, jitter), ch, depth)
pline((282, 154), (312, 172), "p", 0.5, 2, 1.0)
pline((312, 154), (282, 172), "p", 0.5, 2, 1.0)
pline((285, 162), (302, 162), "p", 0.5, 2, 1.2)
pline((279, 150), (292, 153), "a"); pline((308, 150), (297, 153), "a")          # 鎖骨
pline((296, 150), (296, 150), "a")
pline((293, 178), (293, 232), "a", 0.5)                                         # 腹の中心線
for (py, w) in ((199, 15), (212, 15), (225, 14)):
    pline((293 - w, py), (293 + w, py), "a", 0.5)                               # 腹筋の段
pline((278, 182), (293, 189), "a"); pline((308, 182), (293, 189), "a")          # 胸の下の影
paint(293, 236, "a", 2)                                                         # へそ

# ---------------- サッシュ(黄色い帯)・結び目・垂れ ----------------
def sash_y(px):
    # 帯は向かって右に傾く: 左で高く、右で低い
    return -(px - 292) * 0.16
for py0, py1, grow in ((230, 262, 1.4),):
    for iy in range(int(Y(py1)), int(Y(py0)) + 1):
        pass
def sash_inside(x, y, z):
    cx, cz, ax, az = X(292), 1.0, R(33.5), 12.6
    if ((x - cx) / ax) ** 2 + ((z - cz) / az) ** 2 > 1:
        return False
    py = 500 - y / S
    c = 244 + (x - cx) / S * 0.14 + 4      # 帯の中心線の高さ(px)
    return abs(py - c) <= 15.5
v.fill(((X(256), X(330)), (Y(272), Y(224)), (-14, 15)), sash_inside,
       lambda x, y, z: "y" if hsh(int(x), int(y), int(z)) < 0.03 else "Y")
v.ellipsoid(X(308), Y(262), -12.5, R(11), R(10), 6.0, "Y")                       # 結び目
v.ellipsoid(X(314), Y(268), -13.5, R(7), R(8), 4.5, "y")
def ribbon(p0, p1, w0, w1, th, ch):
    n = int(math.dist(p0, p1) / 1.0) + 1
    for i in range(n + 1):
        t = i / n
        c = [p0[k] + (p1[k] - p0[k]) * t for k in range(3)]
        v.ellipsoid(c[0], c[1], c[2], w0 + (w1 - w0) * t, 1.6, th, ch)
ribbon(P(312, 266, -14.5), P(340, 346, -15.5), 7.5, 8.5, 1.9, "Y")                # 垂れ(右下へ)
ribbon(P(305, 268, -12.0), P(322, 322, -13.5), 3.5, 4.0, 1.5, "y")
# ---------------- 首・頭 ----------------
v.capsule(P(293, 128, 1.5), P(293, 150, 1.0), R(10.5), R(11.5), SK)
HC = (X(293), Y(108), 0.0)
v.ellipsoid(HC[0], HC[1], 0.5, R(25.5), R(26.5), 14.5, SK)              # 頭
v.ellipsoid(HC[0], Y(121), -0.2, R(22), R(12.5), 13.2, SK)               # あご
v.ellipsoid(HC[0] + R(1), Y(130), -1.0, R(10), R(3.5), 10.5, SK)         # あごさき
for sg in (-1, 1):
    v.ellipsoid(HC[0] + sg * R(25.8), Y(114), 3.5, 2.6, R(8), 4.0, SK)   # 耳

# 髪: 後ろ・横・前髪(つばの下)
def hair_cond(ix, iy, iz):
    x, y, z = ix + .5, iy + .5, iz + .5
    py = 500 - y / S
    dx = (x - HC[0]) / S
    if z > 3.5 and py < 132:
        return True                               # 後頭部(襟足まで)
    if py < 102.5 + 2.5 * (abs(dx) / 25):
        return True                               # 前髪(つばの下の額)
    if abs(dx) > 19 and py < 122 and z > -4:
        return True                               # もみあげ
    return False
for (ix, iy, iz), ch in list(v.c.items()):
    if ch == SK and iy > Y(150) and hair_cond(ix, iy, iz) and abs(ix + .5 - HC[0]) < R(30) and iz < 20:
        v.c[(ix, iy, iz)] = "h"
# 髪のボリューム(後頭部と横にふくらみ、毛先がはねる)
v.ellipsoid(HC[0], Y(104), 6.0, R(29), R(22), 16.0, "h", cond=lambda x, y, z: z > -2.0)
for sg in (-1, 1):
    v.ellipsoid(HC[0] + sg * R(26), Y(115), 4.0, R(5.5), R(11), 7.0, "h", cond=lambda x, y, z: z > -3.0)
    for k, (dx, dy) in enumerate(((31, 108), (30, 118), (27, 126))):
        v.capsule((HC[0] + sg * R(24), Y(106 + k * 8), 5.0), (HC[0] + sg * R(dx), Y(dy + 4), 6.0), R(5), R(1.2), "h")
# 前髪のギザギザ(額に垂れる毛先)
for px in range(268, 320, 3):
    drop = (rnd.choice((1, 2, 2, 1)) if (px < 288 or px > 298) else rnd.choice((4, 5, 6))) if 272 < px < 315 else 3
    for py in range(102, 102 + drop):
        ix, iy = int(X(px)), int(Y(py))
        z = v.front_z(ix, iy, -20, -4)
        if z is not None and v.get(ix, iy, z) == SK:
            v.c[(ix, iy, z)] = "h"
            if v.get(ix, iy, z + 1) == SK:
                v.c[(ix, iy, z + 1)] = "h"
# 後ろ髪のとがった毛先
for px in range(270, 316, 4):
    for k in range(rnd.choice((2, 3, 5, 3))):
        ix, iy = int(X(px)), int(Y(133 + k * 1.6))
        for iz in range(int(1), int(16)):
            if v.get(ix, iy, iz) in (SK, "h"):
                v.c[(ix, iy, iz)] = "h"

# 顔(正面の面だけに描く。1 ボクセル=約 1.6px)
ZR = (-24, -2)
def F(px, py, ch, depth=2):
    v.paint_front(X(px), Y(py), ch, depth, *ZR)
def face_ellipse(cx, cy, rx, ry, ch, depth=2):
    for ix in range(int(cx - rx - 2), int(cx + rx + 3)):
        for iy in range(int(cy - ry - 2), int(cy + ry + 3)):
            if ((ix + .5 - cx) / rx) ** 2 + ((iy + .5 - cy) / ry) ** 2 <= 1:
                F(ix + .5, iy + .5, ch, depth)
# 目: 白目 + 黒目 + ハイライト(やや上目づかい、にらむ形)
for (cx, cy) in ((283, 108.5), (304, 108.5)):
    face_ellipse(cx, cy, 6.4, 5.6, "w")
for (cx, cy) in ((284.5, 108.5), (302.5, 108.5)):
    face_ellipse(cx, cy, 3.5, 4.0, "h")
F(286.0, 106.2, "w"); F(286.0, 107.4, "w"); F(304.0, 106.2, "w"); F(304.0, 107.4, "w")
# まゆ(内側へ下がる、力のある眉)
pline_front = lambda p0, p1, ch: [F(p0[0] + (p1[0] - p0[0]) * i / 30, p0[1] + (p1[1] - p0[1]) * i / 30, ch)
                                for i in range(31)]
pline_front((276, 102.8), (291, 103.6), "h")
pline_front((312, 102.8), (297, 103.6), "h")
# 上まぶたのかげ

# 鼻: 小さな影
F(292, 117, "a"); F(293.5, 117, "a"); F(291, 115.5, "a")
# 口: 口角が上がったニヤッとした笑み(薄く開けて歯が見える)
for i in range(0, 41):
    t = i / 40
    px = 279 + 28 * t
    py = 124.3 - 4.0 * math.sin(math.pi * t) * 0 + 5.5 * (2 * t - 1) ** 2 * -1 + 5.5   # 中央がやや下がった弧
    F(px, py + 0.0, "h", 2)
for i in range(4, 37):
    t = i / 40
    px = 279 + 28 * t
    py = 124.3 + 5.5 - 5.5 * (2 * t - 1) ** 2 + 1.7
    F(px, py, "w", 2)
for (px, py) in ((283, 123), (304, 122.5)):
    F(px, py, "h", 2)
# 左目の下の傷(向かって右): 3 本のすじ
for dy in (0, 3.2):
    for dx in range(0, 7):
        F(304 + dx, 117.5 + dy + dx * 0.15, "p", 1)
# ほお・あごの輪郭の影
for i in range(0, 12):
    F(272 + i * 0.4, 118 + i * 1.3, "a", 1)
    F(312 - i * 0.4, 118 + i * 1.3, "a", 1)

# ---------------- 腕(ジャケットの袖)と、胸の前で合わせた手 ----------------
def sleeve(S0, E0, W0, r0, r1, r2):
    v.capsule(S0, E0, R(r0), R(r1), "V")
    v.capsule(E0, W0, R(r1), R(r2), "V")
sleeve(P(262, 152, 0.5), P(226, 192, 5.0), P(278, 192, -15.0), 13, 14.5, 10)     # 向かって左(ルフィの右)
sleeve(P(331, 154, 0.5), P(362, 208, 4.0), P(302, 194, -15.0), 13, 15, 10)       # 向かって右(ルフィの左)
# 袖口(カフス)と手
v.ellipsoid(X(280), Y(191), -16.5, R(10.5), R(10.5), 8.5, "V")
v.ellipsoid(X(301), Y(193), -16.5, R(10.0), R(10.0), 8.5, "V")
hand = [(X(283), Y(186), -21.5, R(10), R(8.5), 6.5), (X(295), Y(182), -22.5, R(11), R(8.5), 6.5),
        (X(290), Y(190), -25.0, R(9), R(6.5), 4.0)]
for (cx, cy, cz, ax, ay, az) in hand:
    v.ellipsoid(cx, cy, cz, ax, ay, az, SK)
for i in range(4):                                  # 指(にぎった指の節)
    v.ellipsoid(X(282 + i * 5.5), Y(194.5 - (1 if i % 2 else 0)), -27.0, 2.6, 2.4, 2.3, SK)
v.ellipsoid(X(300), Y(183), -26.0, 2.8, 3.8, 2.8, SK)         # 親指
for i in range(3):
    pline((280 + i * 6, 190), (281 + i * 6, 194), "a", 1) if False else None

# ---------------- 麦わら帽子(右下がりに傾いている) ----------------
HAT_C = (X(300), Y(93), 0.0)
ROLL = -math.atan2(30, 120)                 # 向かって右が下がる
PITCH = math.radians(3)
def to_local(x, y, z):
    px, py, pz = x - HAT_C[0], y - HAT_C[1], z - HAT_C[2]
    # roll(z 軸まわり)の逆回転
    c, s = math.cos(-ROLL), math.sin(-ROLL)
    u, vv = px * c - py * s, px * s + py * c
    # pitch(x 軸まわり)の逆回転
    c2, s2 = math.cos(-PITCH), math.sin(-PITCH)
    w = pz * c2 - vv * s2
    vv = pz * s2 + vv * c2
    return u, vv, w
BR = R(61)
def hat_inside(x, y, z):
    u, vv, w = to_local(x, y, z)
    r = math.hypot(u, w)
    if -1.5 <= vv < 1.5 and r <= BR:                      # つば
        return True
    if vv >= 1.5:                                            # クラウン(上にいくほどすぼまるドーム)
        h = vv - 1.5
        if h > 32.0:
            return False
        rr = R(33.5 - 5.0 * h / 32.0)
        if h > 17:
            rr *= math.sqrt(max(0.0, 1 - ((h - 17) / 15.0) ** 2))
        return r <= rr
    return False
def hat_ch(x, y, z):
    u, vv, w = to_local(x, y, z)
    r = math.hypot(u, w)
    if vv < 1.5 and r <= BR:
        return "J" if vv < -0.3 else ("H" if int(r / 2.2) % 2 == 0 else "M")   # つばは同心円の編み目
    h = vv - 1.5
    if 1.0 <= h < 15.5:
        return "R"                                          # 赤いリボン
    ang = math.atan2(w, u)
    k = int(h / 2.4)
    return "H" if (k + int(ang * 7)) % 3 else "M"
v.fill(((HAT_C[0] - BR - 4, HAT_C[0] + BR + 4), (HAT_C[1] - BR * 0.4 - 4, HAT_C[1] + 40),
        (-BR - 4, BR + 4)), hat_inside, hat_ch)

# ---------------- 背面側の細部(参考画像2: 後ろ姿) ----------------
# ジャケットの背中にしわ(影)と、すその左右のゆれ
for sg in (-1, 1):
    v.capsule((X(292) + sg * R(26), Y(250), 12.0), (X(292) + sg * R(40), Y(236), 15.0), R(5), R(2.5), "V")
# ふくらはぎ・すねの後ろをしっかり(筋肉の盛り)
v.ellipsoid(kneeL[0] - 1, Y(395), kneeL[2] + 2.0, R(8.8), R(26), 6.0, SK)
v.ellipsoid(kneeR[0] + 1, Y(400), kneeR[2] + 2.0, R(8.8), R(26), 6.0, SK)

text, size, n = v.dump("モンキー・D・ルフィ", PAL, comment=(
    "150ブロック=1m。身長174cm=約261ブロック(麦わら帽子込みで約285)。参考画像のポーズ(胸の前で両手を合わせる)。"
    "layers の先頭が1層目(いちばん下)。各層の先頭の行が像の正面、各行は正面から見て左→右、空白は何も置かない。"), version=3)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w", encoding="utf-8").write(text)
print("luffy.json", "size(x,z,y)=", size, "blocks=", n, "bytes=", len(text))
