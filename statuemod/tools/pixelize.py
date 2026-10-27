#!/usr/bin/env python3
"""参考画像(正面)をブロックの解像度にピクセル化する。
使い方: python3 -I pixelize.py <画像> <出力フォルダ> [ブロック/px=0.629]
出力: front.npy(H,W,3 色), mask.npy(H,W 真偽), preview.png"""
import sys, os
import numpy as np
from PIL import Image
from scipy import ndimage

src, outdir = sys.argv[1], sys.argv[2]
S = float(sys.argv[3]) if len(sys.argv) > 3 else 0.629
os.makedirs(outdir, exist_ok=True)

im = Image.open(src).convert("RGB")
a = np.array(im).astype(int)
white = a.min(axis=2) >= 244
# 背景 = 画像の端につながる白 + 大きな囲まれた白(腕と体のすきまなど)
lab, n = ndimage.label(white)
bg = np.zeros_like(white)
border = set(lab[0, :]) | set(lab[-1, :]) | set(lab[:, 0]) | set(lab[:, -1])
for i in range(1, n + 1):
    comp = lab == i
    if i in border or comp.sum() > 120:
        bg |= comp
fg = ~bg
fg = ndimage.binary_opening(fg, iterations=1) | (fg & ~ndimage.binary_erosion(fg, iterations=1) & False)
ys, xs = np.where(fg)
x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
print("figure bbox px:", x0, x1, y0, y1)
crop = a[y0:y1, x0:x1].astype(np.uint8)
fgc = fg[y0:y1, x0:x1]
H, W = int((y1 - y0) * S), int((x1 - x0) * S)
print("blocks:", W, "x", H)
# 色: 前景だけで重み付き平均(背景の白が混ざらないように)
wgt = fgc.astype(float)
out = np.zeros((H, W, 3)); cov = np.zeros((H, W))
cimg = Image.fromarray(crop)
for c in range(3):
    ch = Image.fromarray((crop[:, :, c] * wgt).astype(np.float32), mode="F").resize((W, H), Image.BOX)
    out[:, :, c] = np.array(ch)
cov = np.array(Image.fromarray(wgt.astype(np.float32), mode="F").resize((W, H), Image.BOX))
mask = cov > 0.5
color = np.where(cov[..., None] > 0, out / np.maximum(cov[..., None], 1e-6), 255)
np.save(os.path.join(outdir, "front.npy"), np.clip(color, 0, 255).astype(np.uint8))
np.save(os.path.join(outdir, "mask.npy"), mask)
prev = np.where(mask[..., None], np.clip(color, 0, 255), 255).astype(np.uint8)
Image.fromarray(prev).resize((W * 3, H * 3), Image.NEAREST).save(os.path.join(outdir, "preview.png"))
print("saved")
