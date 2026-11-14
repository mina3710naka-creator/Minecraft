#!/usr/bin/env python3
"""画像 1 枚から、立ててならべる「厚みのある切りぬきの像」の JSON を作る(簡易版の像)。
 - 背景(透明・ふちの色)を消して、キャラクターだけを切りぬく
 - 輪郭までの距離から、まるみのある厚みをつける(お面・クッションのような立体)
 - 正面は絵のとおり。裏はたいらにするか(既定)、丸くふくらませる
 - 中は空洞

使い方:
  python3 image_to_relief.py キャラ.png --height 200 -o 像.json
  python3 image_to_relief.py キャラ.png --height 150 --thickness 40 --bg white --preview p.png
必要なもの: Python 3、pip install pillow numpy
オプション:
  --height N       … 像の高さ(ブロック)。絵の縦をこの高さにする
  --thickness N    … いちばん厚い所の厚さ(ブロック)。既定は幅の 1/3
  --bg auto|alpha|white|black|RRGGBB … 背景の消し方(既定 auto: 透明があれば透明、なければ、ふちの色)
  --tol N          … 背景の色の許容(既定 40)。背景が残るなら大きく、絵が欠けるなら小さく
  --back flat|round … 裏をたいらにする(既定)/丸くする
  --dither         … 色を混ぜて、階調を細かく出す
  --preview p.png  … 完成予想(正面・右・背面・上)"""
import sys, os, argparse
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import blocklib


def quantize_2d(rgb, mask, dither):
    H, W, _ = rgb.shape
    idx = np.zeros((H, W), int)
    if not dither:
        idx[mask] = blocklib.nearest_blocks(rgb[mask])
        return idx
    from image_to_floor import to_lab
    buf = rgb.copy()
    for y in range(H):
        for x in range(W):
            if not mask[y, x]:
                continue
            old = np.clip(buf[y, x], 0, 255)
            i = ((to_lab(old[None]) - blocklib.LAB) ** 2).sum(-1).argmin()
            idx[y, x] = i
            err = old - blocklib.RGB[i]
            if x + 1 < W and mask[y, x + 1]: buf[y, x + 1] += err * 7 / 16
            if y + 1 < H:
                if x > 0 and mask[y + 1, x - 1]: buf[y + 1, x - 1] += err * 3 / 16
                if mask[y + 1, x]: buf[y + 1, x] += err * 5 / 16
                if x + 1 < W and mask[y + 1, x + 1]: buf[y + 1, x + 1] += err * 1 / 16
    return idx


def main():
    ap = argparse.ArgumentParser(description="画像 1 枚 → 厚みのある切りぬきの像")
    ap.add_argument("image")
    ap.add_argument("-o", "--out")
    ap.add_argument("--height", type=int, default=200)
    ap.add_argument("--thickness", type=int)
    ap.add_argument("--bg", default="auto")
    ap.add_argument("--tol", type=int, default=40)
    ap.add_argument("--back", choices=["flat", "round"], default="flat")
    ap.add_argument("--dither", action="store_true")
    ap.add_argument("--preview")
    ap.add_argument("--name")
    a = ap.parse_args()

    img = Image.open(a.image)
    rgb0, fg0 = blocklib.cutout(img, a.bg, a.tol)
    ys, xs = np.where(fg0)
    if len(ys) == 0:
        raise SystemExit("前景が見つかりません。--bg か --tol を変えてください")
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    crop = Image.fromarray(rgb0[y0:y1, x0:x1].astype(np.uint8))
    cm = Image.fromarray((fg0[y0:y1, x0:x1] * 255).astype(np.uint8))
    h = a.height
    w = max(1, round((x1 - x0) * h / (y1 - y0)))
    rgb = np.array(crop.resize((w, h), Image.LANCZOS)).astype(float)
    mask = np.array(cm.resize((w, h), Image.BILINEAR)) >= 128
    # 前景だけで色を平均し直す(背景の色がにじまないように)
    wgt = Image.fromarray((fg0[y0:y1, x0:x1] * 255).astype(np.uint8))
    idx2 = quantize_2d(rgb, mask, a.dither)

    T = a.thickness or max(8, w // 3)
    half = T / 2.0
    dt = blocklib.distance_in(mask)
    R = half
    hh = half * np.sqrt(np.clip(1 - (1 - np.minimum(dt, R) / R) ** 2, 0, 1))
    hh = np.where(mask, np.maximum(hh, 1.0), 0)
    # 奥行き: 手前(z=0)が表。表の面は z=half-hh、裏は(flat なら)z=half、(round なら)half+hh
    depth = int(np.ceil(T)) + 2
    occ = np.zeros((w, h, depth), bool)
    idx = np.zeros((w, h, depth), int)
    # 深いところの色は、まわりの平均(縞にならないよう、ぼかした色)
    from PIL import ImageFilter
    m3 = Image.fromarray((mask * 255).astype(np.uint8))
    blur_rgb = np.array(Image.fromarray((rgb * mask[..., None]).astype(np.uint8)).filter(ImageFilter.GaussianBlur(max(3, w // 25)))).astype(float)
    blur_m = np.array(m3.filter(ImageFilter.GaussianBlur(max(3, w // 25)))).astype(float) / 255.0
    deep_rgb = blur_rgb / np.maximum(blur_m, 0.05)[..., None]
    deep_idx = blocklib.nearest_blocks(deep_rgb.reshape(-1, 3)).reshape(h, w)
    for yy in range(h):
        for xx in np.where(mask[yy])[0]:
            if a.back == "round":
                zf = int(round(half - hh[yy, xx])); zb = int(round(half + hh[yy, xx]))
            else:                                                              # 裏はたいら: 表だけ 2 倍ふくらませて、最大の厚さを T にする
                zb = int(round(T)); zf = int(round(T - 2 * hh[yy, xx]))
            zb = max(zb, zf)
            occ[xx, h - 1 - yy, zf:zb + 1] = True
            idx[xx, h - 1 - yy, zf:zb + 1] = deep_idx[yy, xx]
            idx[xx, h - 1 - yy, zf:min(zf + 3, zb + 1)] = idx2[yy, xx]          # 表面の 3 層だけ、絵の色
    occ = blocklib.shell(occ, 2)
    out = a.out or os.path.splitext(a.image)[0] + "_relief.json"
    name = a.name or os.path.splitext(os.path.basename(out))[0]
    ww, dd, hh_, n, kinds = blocklib.write_statue(out, name, occ, idx, "1 枚の画像から作った厚みのある像(image_to_relief.py)。layers の先頭が 1 層目(下)。各層の先頭の行が正面。")
    print("%s: %d × %d × %d ブロック、%d 個、%d 種類" % (out, ww, dd, hh_, n, kinds))
    if a.preview:
        blocklib.preview(occ, idx, a.preview)
        print("完成予想:", a.preview)
    print("ゲーム内: .minecraft/statues/ に入れて /statue build %s" % name)


if __name__ == "__main__":
    main()
