#!/usr/bin/env python3
"""参考画像(正面)を先にブロックの解像度でピクセル化し(pixelize.py)、その絵に合わせて立体にする。

1. 正面の絵: ピクセル化した画像の色を、いちばん近いブロックの色に置きかえる(正面から見ると絵そのもの)
2. 部位分け: 色と位置で 帽子・つば・頭・首・胴(ジャケット/腹/帯/短パン)・袖・手・帯の垂れ・脚・足 に分ける
3. 奥行き: 部位ごとに、輪郭までの距離から丸みのある厚みをつける(服はゆるく、体に張りつけない)
4. 中身は空洞にして(殻の厚さ 2)、JSON に書き出す

使い方: python3 -I luffy_from_image.py <pixelize.py の出力フォルダ> <出力 json> [色プレビュー用 json]
"""
import sys, os, json, math
import numpy as np
from scipy import ndimage
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from voxlib import Vox

src, out_json = sys.argv[1], sys.argv[2]
pal_dump = sys.argv[3] if len(sys.argv) > 3 else None
col = np.load(os.path.join(src, "front.npy")).astype(float)
mask = np.load(os.path.join(src, "mask.npy"))
H0, W0 = mask.shape
PADX = 4
H, W = H0, W0 + 2 * PADX
mask = np.pad(mask, ((0, 0), (PADX, PADX)))
col = np.pad(col, ((0, 0), (PADX, PADX), (0, 0)), constant_values=255)
YY, XX = np.mgrid[0:H, 0:W]
R_, G_, B_ = col[..., 0], col[..., 1], col[..., 2]

# ------------------------------------------------------------------ 1. 色 → ブロック
CAND = {
    # 肌
    "minecraft:white_terracotta": (209, 178, 161), "minecraft:terracotta": (152, 94, 67),
    "minecraft:jungle_planks": (160, 115, 80), "minecraft:pink_terracotta": (161, 78, 78),
    "minecraft:orange_terracotta": (161, 83, 37), "minecraft:sandstone": (224, 214, 170),
    "minecraft:pink_wool": (237, 141, 172),
    # 髪・線・目
    "minecraft:black_concrete": (8, 10, 15), "minecraft:black_terracotta": (37, 22, 16),
    "minecraft:brown_terracotta": (77, 51, 36), "minecraft:white_concrete": (207, 213, 214),
    "minecraft:dark_oak_planks": (66, 43, 20),
    # ジャケット・帽子のリボン
    "minecraft:red_concrete": (142, 33, 33), "minecraft:red_wool": (160, 39, 34),
    "minecraft:redstone_block": (175, 24, 5), "minecraft:nether_wart_block": (114, 2, 2),
    # サッシュ
    "minecraft:yellow_concrete": (240, 175, 21), "minecraft:yellow_wool": (248, 197, 39),
    "minecraft:gold_block": (246, 208, 61), "minecraft:yellow_terracotta": (186, 133, 35),
    # 短パン
    "minecraft:light_blue_concrete": (36, 137, 199), "minecraft:blue_concrete": (45, 47, 143),
    "minecraft:lapis_block": (31, 67, 140), "minecraft:blue_ice": (116, 167, 253),
    "minecraft:blue_wool": (57, 70, 155), "minecraft:blue_terracotta": (74, 60, 91),
    # ファー
    "minecraft:white_wool": (233, 236, 236), "minecraft:light_gray_wool": (142, 142, 134),
    "minecraft:light_gray_concrete": (125, 125, 115), "minecraft:snow_block": (249, 254, 254),
    "minecraft:calcite": (223, 224, 220), "minecraft:gray_concrete": (55, 58, 62),
    # 麦わら・ぞうり
    "minecraft:bamboo_planks": (224, 203, 100), "minecraft:raw_gold_block": (221, 169, 46),
    "minecraft:oak_planks": (162, 130, 78), "minecraft:sand": (219, 207, 163),
    "minecraft:birch_planks": (192, 175, 121), "minecraft:honeycomb_block": (229, 148, 29),
}
names = list(CAND)
cand_rgb = np.array([CAND[n] for n in names], float)

def to_lab(rgb):
    c = rgb / 255.0
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    M = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = c @ M.T / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)

cand_lab = to_lab(cand_rgb)
lab_img = to_lab(col)

# ------------------------------------------------------------------ 2. 部位分け
def cap_mask(p0, p1, r):
    """2 点の線分から距離 r 以内"""
    p0 = np.array(p0, float); p1 = np.array(p1, float)
    v = p1 - p0
    t = np.clip(((XX - p0[0]) * v[0] + (YY - p0[1]) * v[1]) / (v @ v), 0, 1)
    return (XX - (p0[0] + v[0] * t)) ** 2 + (YY - (p0[1] + v[1] * t)) ** 2 <= r * r
def X_(x): return x + PADX                               # 画像(ピクセル化後)の x → 余白つきの x

red = mask & (R_ > 130) & (R_ - G_ > 70) & (R_ - B_ > 50) & (G_ < 110)
blue = mask & (B_ > R_ + 40) & (B_ > 110)
yellow = mask & (R_ > 170) & (G_ > 130) & (B_ < 110) & (R_ - B_ > 100)
skin_like = mask & (R_ > 170) & (G_ > 120) & (B_ > 95) & (R_ - B_ < 110) & ~yellow
straw = mask & (R_ > 150) & (G_ > 100) & (B_ < 140) & (R_ - B_ > 70) & ~red

part = np.zeros((H, W), int)
HAT_C, HEAD, NECK, BODY, LSLV, RSLV, HANDS, TAIL, LLEG, RLEG, LFOOT, RFOOT, BRIM = range(1, 14)

# --- 帽子・頭(y<56)
top = mask & (YY < 56)
hatpix = top & (straw | (red & (YY < 50))) & ~((XX >= X_(52)) & (XX <= X_(92)) & (YY >= 32))
facebox = top & ~hatpix
part[hatpix] = HAT_C
part[facebox & (YY <= 51)] = HEAD
part[facebox & (YY > 51)] = NECK
# つば: 帯(赤)より下にあるわら色、またはクラウンの左右(x<55, x>96)にあるわら色
band_bottom = np.full(W, -1)
for x in range(W):
    ys_ = np.where(red[:50, x] & hatpix[:50, x])[0]
    if len(ys_):
        band_bottom[x] = ys_.max()
for x in range(W):
    for y in np.where(hatpix[:, x])[0]:
        if band_bottom[x] >= 0:
            if y > band_bottom[x]:
                part[y, x] = BRIM
        else:
            if (x < X_(58) or x > X_(94)) and y > 14:
                part[y, x] = BRIM
            elif y > 14 and x >= X_(58) and x <= X_(94):
                pass
# クラウンの上側で帯がない列(ドーム)は HAT_C のまま

# --- 体(y>=56)
low = mask & (YY >= 56)
sleeve_l = cap_mask((X_(47), 64), (X_(26), 90), 15) | cap_mask((X_(26), 90), (X_(49), 97), 9.5)
sleeve_r = cap_mask((X_(95), 64), (X_(106), 100), 15) | cap_mask((X_(106), 102), (X_(82), 99), 10.5)
hands_zone = ((XX - X_(60)) / 24.0) ** 2 + ((YY - 94) / 12.5) ** 2 <= 1
body_zone = low & (YY < 168)
part[body_zone & (red | skin_like | yellow | blue | straw)] = BODY
part[low & red & sleeve_l & (YY < 112)] = LSLV
part[low & red & sleeve_r & (YY < 114)] = RSLV
part[low & skin_like & hands_zone & (YY >= 80) & (YY <= 108)] = HANDS
# 帯の垂れ(右腰から下へ)
tail = low & yellow & (XX >= X_(70)) & (YY >= 125)
part[tail & (YY > 138)] = TAIL
# 脚(y>=164): 短パン・ファー・すね
crotch = X_(55)
legzone = low & (YY >= 164) & ~(part == TAIL)
part[legzone & (XX < crotch)] = LLEG
part[legzone & (XX >= crotch)] = RLEG
part[low & (YY >= 255) & (XX < X_(42)) & mask & ~(part == TAIL)] = LFOOT
part[low & (YY >= 262) & (XX >= X_(80)) & mask & ~(part == TAIL)] = RFOOT
# 残りで未分類の前景は、近い部位に寄せる(穴をつぶす)
unl = mask & (part == 0)
if unl.any():
    idx = ndimage.distance_transform_edt(part == 0, return_distances=False, return_indices=True)
    part[unl] = part[idx[0][unl], idx[1][unl]]
part[~mask] = 0
# 帯(サッシュ)の細かい穴や、ベスト下の細部も BODY へ
np.save("/tmp/part.npy", part)


FAM = {
    "skin": ["white_terracotta", "terracotta", "jungle_planks", "pink_terracotta", "orange_terracotta", "pink_wool"],
    "red": ["red_concrete", "red_wool", "redstone_block", "nether_wart_block"],
    "yellow": ["yellow_concrete", "yellow_wool", "gold_block", "yellow_terracotta"],
    "straw": ["bamboo_planks", "raw_gold_block", "oak_planks", "birch_planks", "honeycomb_block", "yellow_terracotta"],
    "blue": ["light_blue_concrete", "blue_concrete", "lapis_block", "blue_ice", "blue_wool", "blue_terracotta"],
    "white": ["white_wool", "light_gray_wool", "light_gray_concrete", "snow_block", "calcite", "white_concrete", "gray_concrete"],
    "dark": ["black_concrete", "black_terracotta", "brown_terracotta", "dark_oak_planks"],
}
FAM = {k: [names.index("minecraft:" + n) for n in v] for k, v in FAM.items()}
ALLOW = {
    HAT_C: ["straw", "red", "dark"], BRIM: ["straw", "dark", "red"],
    HEAD: ["skin", "dark", "white", "red"], NECK: ["skin", "dark"],
    BODY: ["skin", "red", "yellow", "blue", "dark"], LSLV: ["red", "dark"], RSLV: ["red", "dark"],
    HANDS: ["skin", "dark"], TAIL: ["yellow", "dark"],
    LLEG: ["blue", "white", "skin", "dark"], RLEG: ["blue", "white", "skin", "dark"],
    LFOOT: ["skin", "dark", "straw"], RFOOT: ["skin", "dark", "straw"],
}
block = np.full((H, W), -1, int)
# 暗い線・髪は黒系にそろえる
darkpix = mask & (R_ + G_ + B_ < 150)
for p, fams in ALLOW.items():
    sel = np.where(mask & (part == p))
    if len(sel[0]) == 0:
        continue
    allowed = sorted({i for f in fams for i in FAM[f]})
    dd = ((lab_img[sel][:, None, :] - cand_lab[None, allowed, :]) ** 2).sum(-1)
    block[sel] = np.array(allowed)[dd.argmin(1)]
block[darkpix & (part != 0)] = names.index("minecraft:black_concrete")
# 絵が白(目・歯)の部分は、頭では白に固定

# ------------------------------------------------------------------ 3. 奥行き
zf = np.zeros((H, W)); zb = np.zeros((H, W))
valid = np.zeros((H, W), bool)

def tube(pmask, hmax, rcap, win, aspect=1.0, zc=0.0, front_off=0.0):
    """pmask の画素に、丸みのある厚み(中心 zc、半分の厚み h)をつける"""
    dt = ndimage.distance_transform_edt(pmask)
    rloc = ndimage.maximum_filter(dt, size=win)
    rloc = np.clip(rloc, 2.0, rcap)
    dd = np.minimum(dt, rloc)
    hcap = np.minimum(rloc * aspect, hmax)
    h = hcap * np.sqrt(np.clip(1 - (1 - dd / rloc) ** 2, 0, 1))
    h = np.maximum(h, 1.0)
    return h

def put(pmask, h, zc, front_off=0.0):
    zc_arr = zc if isinstance(zc, np.ndarray) else np.full((H, W), float(zc))
    sel = pmask
    zf[sel] = (zc_arr - h)[sel] + front_off
    zb[sel] = (zc_arr + h)[sel]
    valid[sel] = True

# 頭: 隠れている上の部分も含めた楕円を土台に丸くする
head_ext = (part == HEAD) | (part == NECK) | ((((XX - X_(73)) / 21.0) ** 2 + ((YY - 33) / 23.0) ** 2) <= 1) & (YY < 52)
head_h = tube(head_ext, 15.0, 16.0, 41)
hm = part == HEAD
put(hm, head_h, -1.0)
HEAD_BACK = np.where(head_ext, -1.0 + head_h, -99)       # 隠れた頭の後ろ側(帽子の下に使う)
nm = part == NECK
put(nm, np.full((H, W), 6.5), 2.0)

# 胴(+ 隠れた部分: 袖・手の後ろ)
body_ext = (part == BODY) | ((part == TAIL) & (YY < 164)) | (((part == LSLV) | (part == RSLV) | (part == HANDS)) & (XX >= X_(40)) & (XX <= X_(100)) & (YY < 135)) \
    | (((XX >= X_(44)) & (XX <= X_(96))) & (YY >= 56) & (YY < 136) & mask)
body_h = tube(body_ext, 13.0, 20.0, 45, aspect=0.75)
# 胸〜腹は上のほうが厚く、腰に向かって少し薄く
hy = np.interp(YY, [56, 100, 135, 168], [1.0, 1.0, 0.92, 0.86])
body_h = body_h * hy
bm = part == BODY
put(bm, body_h, 0.0)
# ジャケットは体から少し前に出て、胸の肌・腹は少しへこむ。帯は上にまいてさらに前へ
jacket = bm & red
chest = bm & skin_like
sash = bm & yellow
zf[jacket] -= 2.5
zf[sash] -= 2.2
zf[chest] += 1.2
BODY_BACK = np.where(body_ext, body_h, -99)

# 袖: 肩から肘、手首へ向かって手前に回る筒(中心 z はパスに沿って補間)
def arm_zc(pmask, p_sh, p_el, p_wr, z_sh, z_el, z_wr):
    zcarr = np.zeros((H, W))
    def seg(a, b):
        a = np.array(a, float); b = np.array(b, float); v = b - a
        return np.clip(((XX - a[0]) * v[0] + (YY - a[1]) * v[1]) / (v @ v), 0, 1), np.hypot(*(XX - (a[0] + np.clip(((XX - a[0]) * v[0] + (YY - a[1]) * v[1]) / (v @ v), 0, 1) * v[0]), YY - (a[1] + np.clip(((XX - a[0]) * v[0] + (YY - a[1]) * v[1]) / (v @ v), 0, 1) * v[1])))
    t1, d1 = seg(p_sh, p_el); t2, d2 = seg(p_el, p_wr)
    z1 = z_sh + (z_el - z_sh) * t1; z2 = z_el + (z_wr - z_el) * t2
    return np.where(d1 <= d2, z1, z2)
lm = part == LSLV
rm = part == RSLV
zl = arm_zc(lm, (X_(47), 64), (X_(26), 90), (X_(49), 97), 0.0, 4.0, -17.0)
zr = arm_zc(rm, (X_(95), 64), (X_(106), 102), (X_(82), 99), 0.0, 4.0, -17.0)
sl_h = tube(lm, 11.0, 11.0, 17, aspect=1.05)
sr_h = tube(rm, 11.5, 11.5, 17, aspect=1.05)
put(lm, sl_h, zl)
put(rm, sr_h, zr)
# 手: 胸の前で合わせている(いちばん手前)
hm2 = part == HANDS
hh = tube(hm2, 9.0, 9.0, 15, aspect=1.0)
put(hm2, hh, -20.0)

# 脚(短パン + ファー + すね)
LEG_H = {}
for lm_, nm_ in ((part == LLEG, "L"), (part == RLEG, "R")):
    if nm_ == "R":
        lm_ = lm_ | ((part == TAIL) & (YY >= 164))
    lh = tube(lm_, 15.0, 15.0, 33, aspect=0.92)
    if nm_ == "L":
        zc = 3.0 + (3.0) * np.clip((YY - 168) / 90.0, 0, 1)
    else:
        zc = -3.0 + (11.0) * np.clip((YY - 168) / 98.0, 0, 1)
    put(lm_, lh, zc)
    LEG_H[nm_] = (lh, zc)

# 帯の垂れ: 腰(胴)・太ももの前に、少し浮かせて垂らす(後ろは胴・脚までつなぐ)
tm = part == TAIL
upper = tm & (YY < 164)
lower = tm & (YY >= 164)
zf[upper] = (0.0 - body_h)[upper] - 3.0 - 0.0
zb[upper] = (0.0 + body_h)[upper]
valid[upper] = True
lh_, zc_ = LEG_H["R"]
zf[lower] = (zc_ - lh_)[lower] - 3.0
zb[lower] = (zc_ + lh_)[lower]
valid[lower] = True
# 足(ぞうり): ゆか上に平たく。左足は左を向き、右足は手前を向く
lf = part == LFOOT
rf = part == RFOOT
zcl = 8.0 - 22.0 * np.clip((X_(36) - XX) / 36.0, 0, 1)
put(lf, np.full((H, W), 6.5), zcl)
zcr = 12.0 - 34.0 * np.clip((YY - 264) / 23.0, 0, 1)
put(rf, np.full((H, W), 6.5), zcr)

# 帽子のクラウン: 頭の上でまるいドーム(傾き: 右下がり)
cm = part == HAT_C
hat_h = tube(cm, 21.0, 22.0, 49, aspect=1.0)
put(cm, hat_h, -1.0)
# 帽子の下の頭の続き(つばやクラウンの奥に頭の後ろまで)
ext_head_cols = cm & (HEAD_BACK > -50)
zb[ext_head_cols] = np.maximum(zb[ext_head_cols], HEAD_BACK[ext_head_cols])
ext_body_cols = ((part == LSLV) | (part == RSLV) | (part == HANDS)) & (BODY_BACK > -50)
zb[ext_body_cols] = np.maximum(zb[ext_body_cols], BODY_BACK[ext_body_cols])

# つば: 傾いた平らな円板。見えている帯の輪郭から、面の傾き y = A + B x - k z をあてはめる
HC_X = X_(75)
RC = 20.0
brim = part == BRIM
bx = np.where(brim.any(0))[0]
RB = (bx.max() - bx.min() + 1) / 2.0 + 0.5
HC_X = (bx.max() + bx.min() + 1) / 2.0
BK = 0.36                                       # 上から見下ろした絵なので、水平な円板が前後に BK 倍の高さで見える
rows, rhs = [], []
for x in bx:
    ys_ = np.where(brim[:, x])[0]
    u = x + 0.5 - HC_X
    zmax = math.sqrt(max(RB * RB - u * u, 0.0))
    if abs(u) > RC:
        rows.append([1.0, x + 0.5]); rhs.append((ys_.min() + ys_.max() + 1.0) / 2.0)
    else:
        ztop = math.sqrt(max(RC * RC - u * u, 0.0))
        rows.append([1.0, x + 0.5]); rhs.append(ys_.max() + 1.0 - BK * zmax)
        rows.append([1.0, x + 0.5]); rhs.append(ys_.min() - BK * ztop)
(BA, BB), *_ = np.linalg.lstsq(np.array(rows), np.array(rhs), rcond=None)
print("つばの面: A=%.2f B=%.3f k=%.3f RB=%.1f" % (BA, BB, BK, RB))
THICK = {}

# ------------------------------------------------------------------ 4. ボクセル化
D = 120
ZOFF = 60
vol_id = np.full((H, D, W), -1, np.int16)         # [y(下向き), z, x] → ブロック番号
colour_of = block.copy()
# 背面(奥側)の色
back_block = block.copy()
red_b = names.index("minecraft:red_concrete")
hair_b = names.index("minecraft:black_concrete")
skin_b = names.index("minecraft:white_terracotta")
for (y, x) in zip(*np.where(mask)):
    p = part[y, x]
    if p == HEAD and y < 50:
        back_block[y, x] = hair_b
    elif p == BODY and not yellow[y, x] and not blue[y, x] and y < 128:
        back_block[y, x] = red_b
    elif p in (BODY,) and yellow[y, x] is False:
        pass
    elif p == HANDS:
        back_block[y, x] = red_b
for (y, x) in zip(*np.where(mask & valid)):
    a = int(round(zf[y, x])) + ZOFF
    b = int(round(zb[y, x])) + ZOFF
    if b < a:
        b = a
    mid = (a + b) / 2.0
    zs = np.arange(a, b + 1)
    ids = np.where(zs <= mid, colour_of[y, x], back_block[y, x])
    vol_id[y, a:b + 1, x] = ids
# つばの板: 面の上の点 (x,z) から y を決めて、厚さ 2 の円板をつくる
under = names.index("minecraft:yellow_terracotta")
straw_b = names.index("minecraft:bamboo_planks")
occ0 = vol_id >= 0
first0 = np.where(occ0.any(1), occ0.argmax(1), 10 ** 6)
plate = np.zeros(vol_id.shape, bool)
for x in range(int(HC_X - RB) - 1, int(HC_X + RB) + 2):
    u = x + 0.5 - HC_X
    if abs(u) >= RB:
        continue
    zmax = math.sqrt(RB * RB - u * u)
    T = 2
    for z in range(-int(zmax), int(zmax) + 1):
        yf = BA + BB * (x + 0.5) - BK * (z + 0.5) - T / 2.0
        y0 = int(round(yf))
        for dy in range(T):
            c_ = under if dy == T - 1 else straw_b
            y = y0 + dy
            if not (0 <= y < H and 0 <= x < W) or not mask[y, x]:
                continue
            zi = z + ZOFF
            if part[y, x] != BRIM and zi <= first0[y, x] + 1:
                continue                                  # 顔・髪の前にはつばを置かない(正面の絵を守る)
            vol_id[y, zi, x] = colour_of[y, x] if (part[y, x] == BRIM and dy < T - 1) else c_
            plate[y, zi, x] = True

# 輪郭の外は消す(正面から見て、絵のシルエットと一致させる)
vol_id[~mask[:, None, :].repeat(D, 1)] = -1
# 正面の最前面の色を、絵の色に合わせる(奥行きが重なったところでも、正面は絵のとおり)
occ = vol_id >= 0
first = occ.argmax(1)                                    # (H,W) 各列で最初にある z
has = occ.any(1)
for (y, x) in zip(*np.where(has & mask)):
    z = first[y, x]
    vol_id[y, z, x] = colour_of[y, x]
    if z + 1 < D and vol_id[y, z + 1, x] >= 0:
        vol_id[y, z + 1, x] = colour_of[y, x]
# 絵があるのにボクセルが無い列は、となりの列の最前面に 1 ブロック置く
miss = mask & ~has
if miss.any():
    print("穴を埋める列:", int(miss.sum()))
    idx = ndimage.distance_transform_edt(~(has & mask), return_distances=False, return_indices=True)
    for (y, x) in zip(*np.where(miss)):
        yy, xx = idx[0][y, x], idx[1][y, x]
        z = first[yy, xx]
        vol_id[y, z, x] = colour_of[y, x]


# 横・上・下を向いた面の色: 部位ごと・行ごとの代表色にそろえる(絵の色をそのまま奥へのばすと縞になるため)
rowmode = {}
for p in range(1, 14):
    for y in range(H):
        sel = mask[y] & (part[y] == p)
        if p == BODY:
            sel2 = sel & ~skin_like[y]
            sel = sel2 if sel2.sum() >= 3 else sel
        if p == HEAD:
            rowmode[(p, y)] = hair_b if y < 49 else skin_b
            continue
        if sel.any():
            vals, cnt = np.unique(block[y][sel], return_counts=True)
            rowmode[(p, y)] = int(vals[cnt.argmax()])
occ = vol_id >= 0
front_ex = occ & ~np.concatenate([np.zeros((H, 1, W), bool), occ[:, :-1, :]], 1)
back_ex = occ & ~np.concatenate([occ[:, 1:, :], np.zeros((H, 1, W), bool)], 1)
side_ex = occ & ~front_ex & ~back_ex
for (y, z, x) in zip(*np.where(side_ex & ~plate)):
    c = rowmode.get((int(part[y, x]), int(y)))
    if c is not None:
        vol_id[y, z, x] = c

# ------------------------------------------------------------------ 5. 空洞にして書き出し
occ = vol_id >= 0
interior = ndimage.binary_erosion(occ, structure=ndimage.generate_binary_structure(3, 1), iterations=2, border_value=0)
shell = occ & ~interior
print("ブロック数(中身つき):", int(occ.sum()), " (空洞):", int(shell.sum()))
# JSON
CH = list("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
used = sorted(set(np.unique(vol_id[shell]).tolist()))
chars = {bid: CH[i] for i, bid in enumerate(used)}
palette = {chars[bid]: names[bid] for bid in used}
v = Vox()
ys_, zs_, xs_ = np.where(shell)
for y, z, x in zip(ys_, zs_, xs_):
    v.c[(int(x), int(H - 1 - y), int(z))] = chars[int(vol_id[y, z, x])]
text, size, n = v.dump("モンキー・D・ルフィ", palette, comment=(
    "150ブロック=1m。身長174cm=約261ブロック(麦わら帽子込みで約287)。参考画像を正面からピクセル化して、その絵に合わせて立体にした。"
    "中身は空洞(殻の厚さ2)。layers の先頭が1層目(いちばん下)。各層の先頭の行が像の正面、各行は正面から見て左→右、空白は何も置かない。"),
    version=4)
open(out_json, "w", encoding="utf-8").write(text)
print("size(x,z,y)=", size, "blocks=", n, "bytes=", len(text))
if pal_dump:
    json.dump({names[b]: CAND[names[b]] for b in used}, open(pal_dump, "w"))
