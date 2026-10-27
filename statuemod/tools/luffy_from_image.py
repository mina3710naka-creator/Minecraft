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

# 帽子の輪郭線(暗い画素)が頭に入らないよう、帽子の画素の近くにある縁の暗い画素は帽子にする
edge0 = mask & ~ndimage.binary_erosion(mask, iterations=2)
darkpix0 = mask & (R_ + G_ + B_ < 150)
is_hat = (part == HAT_C) | (part == BRIM)
d_hat, idx_hat = ndimage.distance_transform_edt(~is_hat, return_indices=True)
fix_ = top & (part == HEAD) & ((YY < 19) | (edge0 & (d_hat <= 2.6)))
part[fix_] = part[idx_hat[0][fix_], idx_hat[1][fix_]]

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
part[low & (YY >= 264) & (XX < X_(42)) & mask & ~(part == TAIL)] = LFOOT
part[low & (YY >= 271) & (XX >= X_(80)) & mask & ~(part == TAIL)] = RFOOT
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
block[darkpix & (part != 0) & (part != HAT_C) & (part != BRIM)] = names.index("minecraft:black_concrete")
# 帽子の輪郭線(暗い茶)は、黒ではなく、いちばん近いわら・赤にする
for p in (HAT_C, BRIM):
    sel = np.where(mask & (part == p) & darkpix)
    if len(sel[0]):
        allowed = sorted(set(FAM["straw"]) | set(FAM["red"]))
        dd = ((lab_img[sel][:, None, :] - cand_lab[None, allowed, :]) ** 2).sum(-1)
        block[sel] = np.array(allowed)[dd.argmin(1)]
# 輪郭の暗い線は、正面の絵では残し、横・後ろの色には近くの色を使う
edge = mask & ~ndimage.binary_erosion(mask, iterations=2)
outl = edge & np.isin(block, FAM["dark"]) & (part != HEAD) & (part != LFOOT) & (part != RFOOT)
block_side = block.copy()
if outl.any():
    ii = ndimage.distance_transform_edt(outl | ~mask, return_distances=False, return_indices=True)
    block_side[outl] = block[ii[0][outl], ii[1][outl]]
# すね(ファーより下)の画素は、肌と暗い線だけにする(輪郭のうすい色が白いファーにならないように)
for p in (LLEG, RLEG):
    wh = (part == p) & np.isin(block, FAM["white"]) & (YY < 235)
    if wh.any():
        fb = np.where(wh.any(1))[0].max()
        sel = np.where(mask & (part == p) & (YY > fb))
        allowed = sorted(set(FAM["skin"]) | set(FAM["dark"]))
        dd = ((lab_img[sel][:, None, :] - cand_lab[None, allowed, :]) ** 2).sum(-1)
        block[sel] = np.array(allowed)[dd.argmin(1)]
        block[darkpix & (part == p) & (YY > fb)] = names.index("minecraft:black_concrete")

# ------------------------------------------------------------------ 3. 奥行き
zf = np.zeros((H, W)); zb = np.zeros((H, W))
valid = np.zeros((H, W), bool)
XC = XX + 0.5; YC = YY + 0.5

def tube(pmask, hmax, rcap, win, aspect=1.0):
    """pmask の画素に、丸みのある厚み(半分の厚み h)をつける。輪郭までの距離から円の断面にする"""
    dt = ndimage.distance_transform_edt(pmask)
    rloc = np.clip(ndimage.maximum_filter(dt, size=win), 2.0, rcap)
    dd = np.minimum(dt, rloc)
    hcap = np.minimum(rloc * aspect, hmax)
    h = hcap * np.sqrt(np.clip(1 - (1 - dd / rloc) ** 2, 0, 1))
    return np.maximum(h, 1.0)

def put(pmask, h, zc):
    zc_arr = zc if isinstance(zc, np.ndarray) else np.full((H, W), float(zc))
    zf[pmask] = (zc_arr - h)[pmask]
    zb[pmask] = (zc_arr + h)[pmask]
    valid[pmask] = True

def ell(cx, cr, cz, rx, ry, rz, pw=0.5):
    """楕円体(pw<0.5 で正面がより平ら)。戻り値: 前, 後ろ, 内側か"""
    t = 1 - ((XC - cx) / rx) ** 2 - ((YC - cr) / ry) ** 2
    s = np.where(t > 0, t, 0.0) ** pw
    return cz - rz * s, cz + rz * s, t > 0

def merge(*parts):
    f = np.full((H, W), 1e9); bk = np.full((H, W), -1e9); ok = np.zeros((H, W), bool)
    for (pf, pb, po) in parts:
        f = np.where(po, np.minimum(f, pf), f); bk = np.where(po, np.maximum(bk, pb), bk); ok |= po
    return f, bk, ok

# ---- 頭: 頭がい骨 + あご + 髪のボリューム(後頭部) + 鼻 + 耳。帽子の下の隠れた部分も作る
HCX = X_(73.0)
skull = ell(HCX, 34, 0.5, 16.8, 19.5, 17.0, 0.42)
jaw = ell(HCX, 44.5, -0.5, 14.2, 9.8, 13.5, 0.42)
hairvol = ell(X_(74.0), 31, 1.5, 18.5, 17.0, 15.5, 0.5)
ears = [ell(X_(55.0), 40, 2.5, 3.0, 6.0, 5.0), ell(X_(91.5), 40, 2.5, 3.0, 6.0, 5.0)]
head_f, head_b, head_ok = merge(skull, jaw, hairvol, *ears)
nose = ell(X_(73.6), 44.2, -16.0, 3.4, 3.6, 3.6, 0.5)
nose_f = np.where(nose[2], nose[0], 1e9)
HEAD_BACK = np.where(head_ok, head_b, -99)
hm = part == HEAD
valid_h = hm & head_ok
zf[hm] = head_f[hm]; zb[hm] = head_b[hm]
miss_h = hm & ~head_ok
if miss_h.any():                                   # 髪の毛先など、楕円の外にはみ出した画素は、近い所の厚みを借りる
    ii = ndimage.distance_transform_edt(~(hm & head_ok), return_distances=False, return_indices=True)
    zf[miss_h] = zf[ii[0][miss_h], ii[1][miss_h]] * 0.0 + (head_f[ii[0][miss_h], ii[1][miss_h]] * 0.6 + 0.4 * head_b[ii[0][miss_h], ii[1][miss_h]])
    zb[miss_h] = head_b[ii[0][miss_h], ii[1][miss_h]] * 0.8
valid[hm] = True
nm = part == NECK
neck_hw = 8.0
ncx = X_(73.5)
nt = np.clip(1 - ((XC - ncx) / neck_hw) ** 2, 0, 1) ** 0.5
zf[nm] = (2.0 - 7.5 * nt)[nm] - 0.0; zb[nm] = (2.0 + 7.5 * nt)[nm]; valid[nm] = True

# ---- 胴(+ 隠れた部分: 袖・手の後ろ)
body_ext = (part == BODY) | ((part == TAIL) & (YY < 164)) | (((part == LSLV) | (part == RSLV) | (part == HANDS)) & (XX >= X_(40)) & (XX <= X_(100)) & (YY < 135)) \
    | (((XX >= X_(44)) & (XX <= X_(96))) & (YY >= 56) & (YY < 136) & mask)
body_h = tube(body_ext, 14.0, 20.0, 45, aspect=0.8)
hy = np.interp(YY, [56, 100, 135, 168], [1.0, 1.0, 0.93, 0.88])
body_h = body_h * hy
bm = part == BODY
put(bm, body_h, 0.0)
BODY_BACK = np.where(body_ext, body_h, -99)

# ---- 袖: 肩から肘、手首へ向かって手前に回る筒
def seg_t(a, b):
    a = np.array(a, float); b = np.array(b, float); v = b - a
    t = np.clip(((XX - a[0]) * v[0] + (YY - a[1]) * v[1]) / (v @ v), 0, 1)
    d = np.hypot(XX - (a[0] + t * v[0]), YY - (a[1] + t * v[1]))
    return t, d
def arm_zc(p_sh, p_el, p_wr, z_sh, z_el, z_wr):
    t1, d1 = seg_t(p_sh, p_el); t2, d2 = seg_t(p_el, p_wr)
    return np.where(d1 <= d2, z_sh + (z_el - z_sh) * t1, z_el + (z_wr - z_el) * t2)
lm = part == LSLV
rm = part == RSLV
zl = arm_zc((X_(47), 64), (X_(26), 90), (X_(49), 97), 0.0, 4.0, -16.0)
zr = arm_zc((X_(95), 64), (X_(106), 102), (X_(82), 99), 0.0, 4.0, -16.0)
put(lm, tube(lm, 8.5, 9.0, 17, aspect=1.0), zl)
put(rm, tube(rm, 9.0, 9.5, 17, aspect=1.0), zr)

# ---- 手: 胸の前で合わせた 2 つのこぶし(楕円体)
hm2 = part == HANDS
hand_parts = []
for side, sel in (("R", hm2 & (XX < X_(62))), ("L", hm2 & (XX >= X_(62)))):
    ys_, xs_ = np.where(sel)
    if len(ys_) == 0:
        continue
    cx_, cr_ = (xs_.min() + xs_.max() + 1) / 2.0, (ys_.min() + ys_.max() + 1) / 2.0
    rx_, ry_ = (xs_.max() - xs_.min() + 1) / 2.0 + 0.8, (ys_.max() - ys_.min() + 1) / 2.0 + 0.8
    cz_ = -16.5 if side == "R" else -18.0
    e = ell(cx_, cr_, cz_, rx_, ry_, min(rx_, ry_, 8.5) * 1.0, 0.5)
    f_, b_, o_ = e
    zf[sel] = np.where(o_, f_, cz_ - 3.0)[sel]; zb[sel] = np.where(o_, b_, cz_ + 3.0)[sel]
    valid[sel] = True

# ---- 脚: 短パン + ファー(DT の筒)、すねは 1 行ごとの楕円
FUR = {}
shin_rows = {}
for nm_, lmask in (("L", part == LLEG), ("R", part == RLEG)):
    whites = lmask & np.isin(block, FAM["white"])
    fur_bottom = np.where(whites.any(1))[0].max()
    shin_rows[nm_] = fur_bottom + 2
LEG_H = {}
for lm_, nm_ in ((part == LLEG, "L"), (part == RLEG, "R")):
    if nm_ == "R":
        lm_ = lm_ | ((part == TAIL) & (YY >= 164))
    shin = lm_ & (YY >= shin_rows[nm_]) & (part != TAIL)
    shorts = lm_ & ~shin
    lh = tube(lm_, 15.0, 15.0, 33, aspect=0.92)
    zc = (3.0 + 3.0 * np.clip((YY - 168) / 90.0, 0, 1)) if nm_ == "L" else (-3.0 + 11.0 * np.clip((YY - 168) / 98.0, 0, 1))
    put(shorts, lh, zc)
    LEG_H[nm_] = (lh, zc)
    # すね: 各行で、左右の端から楕円の断面(前後は幅の 0.95 倍)
    r0 = shin_rows[nm_]
    rows_ = [r for r in range(r0, H) if shin[r].any()]
    cxs, hws = [], []
    for r in rows_:
        xs_ = np.where(shin[r])[0]
        cxs.append((xs_.min() + xs_.max() + 1) / 2.0); hws.append((xs_.max() - xs_.min() + 1) / 2.0)
    cxs = ndimage.uniform_filter1d(np.array(cxs), 5, mode="nearest"); hws = ndimage.uniform_filter1d(np.array(hws), 5, mode="nearest")
    for r, cxr, hwr in zip(rows_, cxs, hws):
        zc_r = (3.0 + 3.0 * (r - r0) / 55.0) if nm_ == "L" else (0.0 + 7.0 * (r - r0) / 55.0)
        hd = hwr * 0.97 + 0.8
        xs_ = np.where(shin[r])[0]
        for x in xs_:
            t = max(0.0, 1 - ((x + 0.5 - cxr) / (hwr + 0.4)) ** 2) ** 0.5
            zf[r, x] = zc_r - hd * t; zb[r, x] = zc_r + hd * t; valid[r, x] = True

# ---- 帯の垂れ: 腰(胴)・太ももの前に浮かせて垂らす(後ろは胴・脚までつなぐ)
tm = part == TAIL
upper = tm & (YY < 164)
lower = tm & (YY >= 164)
zf[upper] = (0.0 - body_h)[upper]
zb[upper] = (0.0 + body_h)[upper]
valid[upper] = True
lh_, zc_ = LEG_H["R"]
zf[lower] = (zc_ - lh_)[lower]
zb[lower] = (zc_ + lh_)[lower]
valid[lower] = True

# ---- 足(ぞうり): ゆか上のたいらな底 + 丸いこう。左足は左前、右足は手前を向く
CLIP = []
def foot_depth(fmask, heel, toe, hh_heel, hh_toe, hw_heel, hw_toe):
    hx, hz = heel; tx, tz = toe
    ax, az = tx - hx, tz - hz; L = math.hypot(ax, az); ux, uz = ax / L, az / L; nx, nz = -uz, ux
    ground = np.where(fmask.any(1))[0].max() + 1.0
    zs_all = np.arange(-50, 50, 0.5)
    for y, x in zip(*np.where(fmask)):
        height = ground - (y + 0.5)
        px = x + 0.5 - hx; pz = zs_all - hz
        t = (px * ux + pz * uz) / L; s = px * nx + pz * nz
        tc = np.clip(t, 0, 1)
        hw = hw_heel + (hw_toe - hw_heel) * tc
        hmax = hh_heel + (hh_toe - hh_heel) * tc
        inside = (t > -0.05) & (t < 1.05) & (np.abs(s) <= hw + 0.5)
        top = np.maximum(hmax * np.sqrt(np.clip(1 - (s / hw) ** 2, 0, 1)), 3.0)
        ok = inside & (height <= top + 0.5)
        if ok.any():
            zf[y, x] = zs_all[ok].min(); zb[y, x] = zs_all[ok].max() + 0.5; valid[y, x] = True
        else:
            CLIP.append((y, x))
lf = part == LFOOT
rf = part == RFOOT
foot_depth(lf, (X_(34.0), 9.0), (X_(1.0), -17.0), 16.0, 9.0, 5.8, 7.0)
foot_depth(rf, (X_(94.0), 12.0), (X_(107.0), -25.0), 16.0, 9.0, 5.8, 7.0)
for (y, x) in CLIP:
    mask[y, x] = False; part[y, x] = 0; valid[y, x] = False
print('足で切った画素:', len(CLIP))

# ---- 帽子のクラウン: 傾きは帯の下の線にそろえた、円断面のドーム
cm = part == HAT_C
crx = np.where(cm.any(0))[0]
HC_X = (crx.min() + crx.max() + 1) / 2.0
RCR = (crx.max() - crx.min() + 1) / 2.0
bcols = np.array([x for x in range(W) if band_bottom[x] >= 0])
bfit = np.polyfit(bcols + 0.5, band_bottom[bcols] + 1.0, 1)
rb = np.polyval(bfit, XC)                          # 各列の「帯の下」の行
hh_ = np.maximum(rb - YC, 0.0)                     # 帽子の底からの高さ
hat_top = hh_[cm].max()
prof = np.where(hh_ <= 16, 1.0, np.sqrt(np.clip(1 - ((hh_ - 16) / (hat_top - 16 + 0.5)) ** 2, 0, 1)))
arad = RCR * prof
dz = np.sqrt(np.clip(arad ** 2 - (XC - HC_X) ** 2, 0, None))
dz = np.maximum(dz, 1.0)
zf[cm] = (1.0 - dz)[cm]; zb[cm] = (1.0 + dz)[cm]; valid[cm] = True
# 帽子の下の頭・胴の続き(奥まで)
ext_head_cols = cm & (HEAD_BACK > -50)
zb[ext_head_cols] = np.maximum(zb[ext_head_cols], np.minimum(HEAD_BACK[ext_head_cols], zb[ext_head_cols] - 1.0))
ext_body_cols = ((part == LSLV) | (part == RSLV) | (part == HANDS)) & (BODY_BACK > -50)
zb[ext_body_cols] = np.maximum(zb[ext_body_cols], BODY_BACK[ext_body_cols])

# ---- なめらかにして、服・髪・ファーなどの起伏を足す
def smooth_part(arr, pm, sigma):
    num = ndimage.gaussian_filter(np.where(pm, arr, 0.0), sigma)
    den = ndimage.gaussian_filter(pm.astype(float), sigma)
    return np.where(pm, num / np.maximum(den, 1e-6), arr)
for p in (HEAD, NECK, BODY, LSLV, RSLV, HANDS, LLEG, RLEG, TAIL, HAT_C):
    pm = (part == p) & valid
    zf[:] = smooth_part(zf, pm, 1.1); zb[:] = smooth_part(zb, pm, 1.1)
# 起伏: 暗い線(しわ・指の間・髪の毛すじ)はへこみ、明るいところは出っぱる
Lum = 0.3 * R_ + 0.59 * G_ + 0.11 * B_
Lbl = ndimage.gaussian_filter(np.where(mask, Lum, 255.0), 2.0)
relief = np.clip((Lum - Lbl) * 0.045, -1.4, 1.0)
jacket = bm & np.isin(block, FAM["red"])
chest = bm & np.isin(block, FAM["skin"])
sash = bm & np.isin(block, FAM["yellow"])
zf[jacket] -= 2.5
zf[sash] -= 2.2
zf[chest] += 1.2
for p, g in ((BODY, 1.0), (LSLV, 1.0), (RSLV, 1.0), (HANDS, 1.3), (LLEG, 0.8), (RLEG, 0.8), (TAIL, 1.0), (HAT_C, 0.8)):
    zf[part == p] -= (relief * g)[part == p]
# 髪(黒)は頭の肌より前に出す。目・口・まゆ・まつ毛の線は少しへこませる
hair_px = hm & (block == names.index("minecraft:black_concrete"))
face_rows = (YY >= 35) & (YY <= 51)
zf[hair_px & ~face_rows] -= 2.6
zf[hair_px & face_rows & ((XX < X_(58)) | (XX > X_(90)))] -= 2.6
zf[hair_px & face_rows & (XX >= X_(58)) & (XX <= X_(90))] += 0.8       # 黒目・口のすじ
zf[hm & (block == names.index("minecraft:white_concrete"))] += 0.4
nose_zone = hm & (nose_f < 1e8)
zf[nose_zone] = np.minimum(zf[nose_zone], nose_f[nose_zone])
fur_px = np.isin(block, FAM["white"]) & ((part == LLEG) | (part == RLEG))
zf[fur_px] -= 1.5
zf[hm & ~hair_px] -= (relief * 0.8)[hm & ~hair_px]
zf[:] = np.minimum(zf, zb - 1.0)

# ---- つば: 地面にたいらで、前のふちが少したれた円板(厚みは見えている帯の厚さに合わせる)
brim = part == BRIM
bx = np.where(brim.any(0))[0]
RB = (bx.max() - bx.min() + 1) / 2.0 + 0.5
HB_X = (bx.max() + bx.min() + 1) / 2.0
y0s = np.zeros(W); y1s = np.zeros(W)
for x in bx:
    ys_ = np.where(brim[:, x])[0]
    y0s[x] = ys_.min(); y1s[x] = ys_.max()
sm = lambda a: ndimage.median_filter(a[bx.min():bx.max() + 1], size=3, mode="nearest")
y0s[bx.min():bx.max() + 1] = sm(y0s); y1s[bx.min():bx.max() + 1] = sm(y1s)
print("つば: 中心 x=%.1f 半径 %.1f / クラウン半径 %.1f" % (HB_X, RB, RCR))

# ------------------------------------------------------------------ 4. ボクセル化
red_b = names.index("minecraft:red_concrete")
hair_b = names.index("minecraft:black_concrete")
skin_b = names.index("minecraft:white_terracotta")
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
            vals, cnt = np.unique(block_side[y][sel], return_counts=True)
            rowmode[(p, y)] = int(vals[cnt.argmax()])

D = 120
ZOFF = 60
vol_id = np.full((H, D, W), -1, np.int16)         # [y(下向き), z, x] → ブロック番号
colour_of = block.copy()
# 背面(奥側)の色
back_block = block_side.copy()
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
    a = min(max(int(round(zf[y, x])) + ZOFF, 0), D - 1)
    b = min(max(int(round(zb[y, x])) + ZOFF, 0), D - 1)
    if b < a:
        b = a
    mid = (a + b) / 2.0
    zs = np.arange(a, b + 1)
    # 正面の絵の色は表面の 3 層だけ。それより奥の前半は部位の代表色、後ろ半分は背面の色
    side_c = rowmode.get((int(part[y, x]), int(y)), colour_of[y, x])
    ids = np.where(zs - a <= 2, colour_of[y, x], np.where(zs <= mid, side_c, back_block[y, x]))
    vol_id[y, a:b + 1, x] = ids
# つばの板: 列ごとに、見えている帯の上端→下端を、クラウンの根もと→前のふち(または奥→手前)にわりあてる
under = names.index("minecraft:yellow_terracotta")
straw_b = names.index("minecraft:bamboo_planks")
plate = np.zeros(vol_id.shape, bool)
for x in bx:
    u = x + 0.5 - HB_X
    if abs(u) >= RB:
        continue
    zmax = math.sqrt(RB * RB - u * u)
    in_crown = abs(u) <= RCR
    z_a = -math.sqrt(max(RCR * RCR - u * u, 0.0)) if in_crown else zmax        # 帯の上端に対応する z
    z_b = -zmax                                                              # 帯の下端(前のふち)
    y0, y1 = int(y0s[x]), int(y1s[x])
    n_rows = y1 - y0 + 1
    rng = min(max(n_rows - 2, 0), 4)
    T = max(2, n_rows - rng)
    for z in range(-int(zmax), int(zmax) + 1):
        g = min(max((z_a - z) / (z_a - z_b), 0.0), 1.0) if z_a != z_b else 0.0
        s_row = y0 + rng * g
        r0 = int(round(s_row))
        for dy in range(T):
            y = r0 + dy
            if not (0 <= y < H) or not mask[y, x]:
                continue
            zi = z + ZOFF
            if vol_id[y, zi, x] >= 0 and part[y, x] != BRIM:
                continue                                  # 頭・クラウンの中は、そのまま
            vol_id[y, zi, x] = colour_of[y, x] if (part[y, x] == BRIM and dy < T - 1) else (under if dy == T - 1 else straw_b)
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


occ = vol_id >= 0
front_ex = occ & ~np.concatenate([np.zeros((H, 1, W), bool), occ[:, :-1, :]], 1)
back_ex = occ & ~np.concatenate([occ[:, 1:, :], np.zeros((H, 1, W), bool)], 1)
side_ex = occ & ~front_ex & ~back_ex
for (y, z, x) in zip(*np.where(side_ex & ~plate)):
    c = rowmode.get((int(part[y, x]), int(y)))
    if c is not None:
        vol_id[y, z, x] = c

# 正面から見た絵が、ピクセル化した参考画像とどれだけ一致するか
occ_ = vol_id >= 0
has_ = occ_.any(1)
first_ = occ_.argmax(1)
yy_, xx_ = np.mgrid[0:H, 0:W]
front_id = np.take_along_axis(vol_id, first_[:, None, :], 1)[:, 0, :]
m_ref = block >= 0
print("正面の一致: 絵の画素 %d 中、ブロックがある %.1f%%、同じブロック %.1f%%、輪郭の外にはみ出し %d" % (
    m_ref.sum(), 100.0 * (has_ & m_ref).sum() / m_ref.sum(), 100.0 * ((front_id == block) & m_ref & has_).sum() / m_ref.sum(),
    int((has_ & ~m_ref).sum())))

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
    version=5)
open(out_json, "w", encoding="utf-8").write(text)
print("size(x,z,y)=", size, "blocks=", n, "bytes=", len(text))
if pal_dump:
    json.dump({names[b]: CAND[names[b]] for b in used}, open(pal_dump, "w"))
