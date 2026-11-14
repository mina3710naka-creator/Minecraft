#!/usr/bin/env python3
"""正面・横(・背面・上)の画像から、シルエットを重ねて削って像の JSON を作る。

使い方:
  python3 views_to_statue.py --front 正面.png --side 横.png --height 200 -o 像.json
  python3 views_to_statue.py --front f.png --side s.png --back b.png --top t.png --side-faces left --height 150

どの画像も、背景は透明か、単色(白など)にして、キャラクターの全身を、足の裏から頭の先まで入れてください
(足の裏・頭の先が、画像の下端・上端にそろっているほうが正確です)。
必要なもの: Python 3、pip install pillow numpy
オプション:
  --front / --side   … 必須。正面の画像 / 真横の画像
  --back / --top     … 背面の画像 / 真上から見た画像(あれば精度が上がる)
  --side-faces left|right … 横の画像で、キャラクターが向いている方向(既定 left)。向きで前後がそろう
  --height N         … 像の高さ(ブロック)
  --bg / --tol       … 背景の消し方(image_to_relief.py と同じ。既定 auto / 40)
  --dither           … (使いません。色はそのまま近いブロックに置きかえます)
  --preview p.png    … 完成予想(正面・右・背面・上)
色: 正面から見える面は正面の画像、背面は背面の画像(なければ正面の画像)、横は横の画像、真上は上の画像から取ります。
注意: シルエットを重ねるだけなので、へこんだ所(腕と体のすきま、あごの下など)は埋まります。"""
import sys, os, argparse
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import blocklib


def load_view(path, height, bg, tol, fit_wh=None):
    img = Image.open(path)
    rgb, fg = blocklib.cutout(img, bg, tol)
    ys, xs = np.where(fg)
    if len(ys) == 0:
        raise SystemExit("%s: 前景が見つかりません(--bg / --tol を調整してください)" % path)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    crop = Image.fromarray(rgb[y0:y1, x0:x1].astype(np.uint8))
    cm = Image.fromarray((fg[y0:y1, x0:x1] * 255).astype(np.uint8))
    if fit_wh is None:
        w = max(1, round((x1 - x0) * height / (y1 - y0))); h = height
    else:
        w, h = fit_wh
    return (np.array(crop.resize((w, h), Image.LANCZOS)).astype(float),
            np.array(cm.resize((w, h), Image.BILINEAR)) >= 128)


def main():
    ap = argparse.ArgumentParser(description="正面・横(・背面・上)の画像 → 像")
    ap.add_argument("--front", required=True)
    ap.add_argument("--side", required=True)
    ap.add_argument("--back")
    ap.add_argument("--top")
    ap.add_argument("--side-faces", choices=["left", "right"], default="left")
    ap.add_argument("--height", type=int, default=200)
    ap.add_argument("--bg", default="auto")
    ap.add_argument("--tol", type=int, default=40)
    ap.add_argument("-o", "--out")
    ap.add_argument("--preview")
    ap.add_argument("--name")
    a = ap.parse_args()

    H = a.height
    Frgb, Fm = load_view(a.front, H, a.bg, a.tol)
    Srgb, Sm = load_view(a.side, H, a.bg, a.tol)
    W, D = Fm.shape[1], Sm.shape[1]
    if a.side_faces == "right":                           # 前が右にある横の画像は、前が左になるよう反転
        Srgb = Srgb[:, ::-1]; Sm = Sm[:, ::-1]
    Brgb = None
    if a.back:
        Brgb, Bm = load_view(a.back, H, a.bg, a.tol, fit_wh=(W, H))
        Brgb = Brgb[:, ::-1]                              # 背面は左右が逆
    T = None
    if a.top:
        Trgb, Tm = load_view(a.top, H, a.bg, a.tol, fit_wh=(W, D))     # 画像: 横=幅(x)、縦=奥行き(下が手前)
        T = np.array(Image.fromarray(Tm.astype(np.uint8) * 255).resize((W, D), Image.NEAREST)) >= 128
        Trgb = np.array(Image.fromarray(Trgb.astype(np.uint8)).resize((W, D), Image.LANCZOS)).astype(float)
    print("正面 %d × %d、横 %d × %d(幅 %d、奥行き %d、高さ %d)" % (W, H, D, H, W, D, H))

    # occ[x, y(下から), z(手前から)]
    F = Fm[::-1].T            # (x, y)
    S = Sm[::-1].T            # (z, y)
    occ = F[:, :, None] & S.T[None, :, :]           # (x, y, z) ← S.T は (y, z)
    if T is not None:
        # 上の画像は、下が手前(前)・上が奥、横は正面から見て左→右
        occ &= T[::-1].T[:, None, :]                # T[::-1] は(手前=0, x) → .T で (x, z)
    if not occ.any():
        raise SystemExit("重ねた結果が空です。画像の向きや大きさ(足の裏と頭の先の位置)を確認してください")

    rgb = np.zeros(occ.shape + (3,))
    X, Y, Z = occ.shape
    # 手前(正面)から見て最初のボクセル、奥から見て最初のボクセル
    first = occ.argmax(2); has = occ.any(2)
    last = Z - 1 - occ[:, :, ::-1].argmax(2)
    xi, yi = np.meshgrid(np.arange(X), np.arange(Y), indexing="ij")
    Fcol = Frgb[::-1].transpose(1, 0, 2)        # (x, y, 3)
    for x in range(X):
        for y in range(Y):
            if has[x, y]:
                rgb[x, y, first[x, y]] = Fcol[x, y]
                rgb[x, y, last[x, y]] = (Brgb[::-1].transpose(1, 0, 2)[x, y] if Brgb is not None else Fcol[x, y] * 0.9)
    # 横(左右の端)から見て最初・最後のボクセル → 横の画像の色
    Scol = Srgb[::-1].transpose(1, 0, 2)        # (z, y, 3)
    for y in range(Y):
        for z in range(Z):
            col = occ[:, y, z]
            if col.any():
                xs = np.where(col)[0]
                for xx in (xs.min(), xs.max()):
                    if not (rgb[xx, y, z] != 0).any():
                        rgb[xx, y, z] = Scol[z, y]
    # まだ色のない表面は、同じ (x,y) の正面の色
    miss = occ & ~(rgb != 0).any(-1)
    xs_, ys_, zs_ = np.where(miss)
    rgb[xs_, ys_, zs_] = Fcol[xs_, ys_]
    if T is not None:
        Tcol = Trgb[::-1].transpose(1, 0, 2)    # (x, z, 3)
        topy = Y - 1 - occ[:, ::-1, :].argmax(1)
        for x in range(X):
            for z in range(Z):
                if occ[x, :, z].any():
                    rgb[x, topy[x, z], z] = Tcol[x, z]
    occ_s = blocklib.shell(occ, 2)
    idx = np.zeros(occ.shape, int)
    idx[occ_s] = blocklib.nearest_blocks(rgb[occ_s])
    out = a.out or "views_statue.json"
    name = a.name or os.path.splitext(os.path.basename(out))[0]
    w, d, h, n, kinds = blocklib.write_statue(out, name, occ_s, idx, "正面・横の画像から作った像(views_to_statue.py)。layers の先頭が 1 層目(下)。各層の先頭の行が正面。")
    print("%s: %d × %d × %d ブロック、%d 個、%d 種類" % (out, w, d, h, n, kinds))
    if a.preview:
        blocklib.preview(occ_s, idx, a.preview)
        print("完成予想:", a.preview)
    print("ゲーム内: .minecraft/statues/ に入れて /statue build %s" % name)


if __name__ == "__main__":
    main()
