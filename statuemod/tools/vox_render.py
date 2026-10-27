#!/usr/bin/env python3
"""像 JSON を任意の角度から見た画像にする(検査用のレイキャスト)。
使い方: python3 -I vox_render.py <像.json> <出力.png> "<yaw>,<pitch>[;<yaw>,<pitch>...]" [倍率=2] [ymin,ymax]
 yaw=0 が正面、90 が像の右側面(向かって右から見る)、180 が背面。pitch は見下ろす角度。
 ymin,ymax を指定すると、その高さ(ブロック)の範囲だけをアップで見る(頭・手・足の検査用)。"""
import sys, json, math, os
import numpy as np
from numba import njit
from PIL import Image

RGB = {
    "white_terracotta": (209, 178, 161), "terracotta": (152, 94, 67), "jungle_planks": (160, 115, 80),
    "pink_terracotta": (161, 78, 78), "orange_terracotta": (161, 83, 37), "pink_wool": (237, 141, 172),
    "sandstone": (224, 214, 170), "black_concrete": (8, 10, 15), "black_terracotta": (37, 22, 16),
    "brown_terracotta": (77, 51, 36), "white_concrete": (207, 213, 214), "dark_oak_planks": (66, 43, 20),
    "red_concrete": (142, 33, 33), "red_wool": (160, 39, 34), "redstone_block": (175, 24, 5),
    "nether_wart_block": (114, 2, 2), "yellow_concrete": (240, 175, 21), "yellow_wool": (248, 197, 39),
    "gold_block": (246, 208, 61), "yellow_terracotta": (186, 133, 35), "light_blue_concrete": (36, 137, 199),
    "blue_concrete": (45, 47, 143), "lapis_block": (31, 67, 140), "blue_ice": (116, 167, 253),
    "blue_wool": (57, 70, 155), "blue_terracotta": (74, 60, 91), "white_wool": (233, 236, 236),
    "light_gray_wool": (142, 142, 134), "light_gray_concrete": (125, 125, 115), "snow_block": (249, 254, 254),
    "calcite": (223, 224, 220), "gray_concrete": (55, 58, 62), "bamboo_planks": (224, 203, 100),
    "raw_gold_block": (221, 169, 46), "oak_planks": (162, 130, 78), "sand": (219, 207, 163),
    "birch_planks": (192, 175, 121), "honeycomb_block": (229, 148, 29), "lime_concrete": (94, 168, 24),
    "green_concrete": (73, 91, 36), "pink_concrete": (213, 101, 142), "cyan_concrete": (21, 119, 136),
    "brown_concrete": (96, 60, 32), "orange_concrete": (224, 97, 0), "magenta_concrete": (169, 48, 159),
}

@njit(cache=True)
def cast(occ, col, H, D, W, cx, cy, cz, rx, ry, rz, ux, uy, uz, fx, fy, fz, w, h, inv, light, out):
    for j in range(h):
        for i in range(w):
            a = (i - w / 2.0) * inv
            b = (h / 2.0 - j) * inv
            ox = cx + rx * a + ux * b - fx * 400.0
            oy = cy + ry * a + uy * b - fy * 400.0
            oz = cz + rz * a + uz * b - fz * 400.0
            # DDA (x→W, y→H, z→D)
            px = int(math.floor(ox)); py = int(math.floor(oy)); pz = int(math.floor(oz))
            sx = 1 if fx > 0 else -1; sy = 1 if fy > 0 else -1; sz = 1 if fz > 0 else -1
            tdx = abs(1.0 / fx) if fx != 0 else 1e30
            tdy = abs(1.0 / fy) if fy != 0 else 1e30
            tdz = abs(1.0 / fz) if fz != 0 else 1e30
            tx = ((px + (1 if sx > 0 else 0)) - ox) / fx if fx != 0 else 1e30
            ty = ((py + (1 if sy > 0 else 0)) - oy) / fy if fy != 0 else 1e30
            tz = ((pz + (1 if sz > 0 else 0)) - oz) / fz if fz != 0 else 1e30
            axis = -1
            for _ in range(1200):
                if 0 <= px < W and 0 <= py < H and 0 <= pz < D:
                    if occ[py, pz, px]:
                        nx = 0.0; ny = 0.0; nz = 0.0
                        if axis == 0: nx = -sx
                        elif axis == 1: ny = -sy
                        elif axis == 2: nz = -sz
                        else: ny = 1.0
                        d = nx * light[0] + ny * light[1] + nz * light[2]
                        s = 0.52 + 0.48 * max(0.0, d)
                        c = col[py, pz, px]
                        out[j, i, 0] = min(255.0, c[0] * s)
                        out[j, i, 1] = min(255.0, c[1] * s)
                        out[j, i, 2] = min(255.0, c[2] * s)
                        break
                if tx <= ty and tx <= tz:
                    px += sx; tx += tdx; axis = 0
                elif ty <= tz:
                    py += sy; ty += tdy; axis = 1
                else:
                    pz += sz; tz += tdz; axis = 2

def load(jf):
    d = json.load(open(jf, encoding="utf-8"))
    pal = {k: RGB.get(v.split(":")[1].split("[")[0], (200, 0, 200)) for k, v in d["palette"].items()}
    L = d["layers"]; H = len(L); D = max(len(l) for l in L); W = max(len(r) for l in L for r in l)
    occ = np.zeros((H, D, W), np.bool_); col = np.zeros((H, D, W, 3), np.float64)
    for y, l in enumerate(L):
        for z, r in enumerate(l):
            for x, c in enumerate(r):
                if c != " ":
                    occ[y, z, x] = True; col[y, z, x] = pal[c]
    return occ, col

def render(occ, col, yaw, pitch, scale=2.0, yr=None):
    H, D, W = occ.shape
    y0, y1 = (0, H) if yr is None else yr
    th = math.radians(yaw); ph = math.radians(pitch)
    cdir = np.array([math.sin(th) * math.cos(ph), math.sin(ph), -math.cos(th) * math.cos(ph)])
    f = -cdir
    r = np.array([math.cos(th), 0.0, math.sin(th)])
    u = np.cross(f, r)
    ctr = np.array([W / 2.0, (y0 + y1) / 2.0, D / 2.0])
    span = max(W, D, (y1 - y0)) * 1.15 if yr is not None else max(H * 0.62, W * 1.0, D * 1.0) * 1.1
    w = int(span * scale * (1.0 if yr is not None else 1.0)); h = int((y1 - y0) * 1.1 * scale) if yr is not None else int(H * 1.08 * scale)
    if yr is None:
        w = int(max(W, D) * 1.5 * scale)
    out = np.full((h, w, 3), 255.0)
    light = np.array([-0.4, 0.8, -0.45]); light /= np.linalg.norm(light)
    cast(occ, col, H, D, W, ctr[0], ctr[1], ctr[2], r[0], r[1], r[2], u[0], u[1], u[2], f[0], f[1], f[2], w, h, 1.0 / scale, light, out)
    return Image.fromarray(out.astype(np.uint8))

if __name__ == "__main__":
    jf, outp, views = sys.argv[1], sys.argv[2], sys.argv[3]
    scale = float(sys.argv[4]) if len(sys.argv) > 4 else 2.0
    yr = tuple(int(v) for v in sys.argv[5].split(",")) if len(sys.argv) > 5 else None
    occ, col = load(jf)
    H = occ.shape[0]
    imgs = []
    for v in views.split(";"):
        yaw, pitch = (float(t) for t in v.split(","))
        imgs.append(render(occ, col, yaw, pitch, scale, yr))
    tot = Image.new("RGB", (sum(i.width for i in imgs), max(i.height for i in imgs)), (255, 255, 255)); x = 0
    for i in imgs:
        tot.paste(i, (x, 0)); x += i.width
    tot.save(outp); print(tot.size)
