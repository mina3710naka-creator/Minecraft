"""3D の像づくりツール(model_to_statue / views_to_statue / image_to_relief)で共通に使う部品。
色の候補は image_to_floor.py の TABLE(約 200 種のブロック)。tools フォルダごと使うこと。"""
import os, sys, json
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from image_to_floor import TABLE, to_lab, CH            # noqa: E402

NAMES = ["minecraft:" + n for n, _ in TABLE]
RGB = np.array([c for _, c in TABLE], float)
LAB = to_lab(RGB)


def nearest_blocks(rgb):
    """rgb: (N,3) の色(0〜255)→ ブロック番号(N,)。同じ色はまとめて計算する"""
    rgb = np.clip(np.asarray(rgb, float), 0, 255)
    q = np.round(rgb / 4).astype(np.int64)                     # 4 刻みにまとめて高速化
    key = q[:, 0] * 4096 + q[:, 1] * 64 + q[:, 2]
    uniq, inv = np.unique(key, return_inverse=True)
    first = np.zeros(len(uniq), int)
    first[inv] = np.arange(len(inv))
    lab = to_lab(rgb[first])
    out = np.zeros(len(uniq), int)
    for s in range(0, len(uniq), 2000):
        d = ((lab[s:s + 2000, None, :] - LAB[None]) ** 2).sum(-1)
        out[s:s + 2000] = d.argmin(1)
    return out[inv]


def _shift(a, axis, step):
    """配列を axis 方向に step(±1) ずらす(はみ出した所は False/0)"""
    out = np.zeros_like(a)
    src = [slice(None)] * a.ndim
    dst = [slice(None)] * a.ndim
    if step > 0:
        src[axis] = slice(0, -1); dst[axis] = slice(1, None)
    else:
        src[axis] = slice(1, None); dst[axis] = slice(0, -1)
    out[tuple(dst)] = a[tuple(src)]
    return out


def thicken(occ, rgb, n):
    """面でつながるように n ボクセルぶん太らせる。新しいボクセルの色は、となりの色の平均"""
    for _ in range(n):
        acc = np.zeros(rgb.shape, float); cnt = np.zeros(occ.shape, float)
        for axis in range(3):
            for step in (1, -1):
                so = _shift(occ, axis, step)
                sr = _shift(rgb * occ[..., None], axis, step)
                acc += sr; cnt += so
        new = ~occ & (cnt > 0)
        rgb = np.where(new[..., None], acc / np.maximum(cnt, 1)[..., None], rgb)
        occ = occ | new
    return occ, rgb


def shell(occ, thickness=2):
    """内側をくりぬいて、表面から thickness ボクセルの殻だけ残す"""
    inner = occ.copy()
    for _ in range(thickness):
        e = inner.copy()
        for axis in range(3):
            for step in (1, -1):
                e &= _shift(inner, axis, step) | False
        # 端(配列の外)は「空」とみなす: _shift が False を入れるので、端のボクセルは自動で外側になる
        inner = e
    return occ & ~inner


def write_statue(path, name, occ, idx, comment):
    """occ[x,y,z]: ボクセルの有無(x=正面から見て左→右、y=下→上、z=手前→奥)。idx[x,y,z]: ブロック番号"""
    xs, ys, zs = np.where(occ)
    x0, x1, y0, y1, z0, z1 = xs.min(), xs.max(), ys.min(), ys.max(), zs.min(), zs.max()
    sub = occ[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1]
    sidx = idx[x0:x1 + 1, y0:y1 + 1, z0:z1 + 1]
    used = sorted(set(sidx[sub].tolist()))
    if len(used) > len(CH):
        raise SystemExit("色の種類が多すぎます")
    chars = {b: CH[k] for k, b in enumerate(used)}
    W, H, D = sub.shape
    lut = np.full(max(used) + 1, " ", dtype="<U1")
    for b in used:
        lut[b] = chars[b]
    out = ['{\n', '  "name": %s,\n' % json.dumps(name, ensure_ascii=False),
           '  "comment": %s,\n' % json.dumps(comment, ensure_ascii=False),
           '  "size": [%d, %d, %d],\n' % (W, D, H),
           '  "palette": {\n' + ",\n".join("    %s: %s" % (json.dumps(chars[b]), json.dumps(NAMES[b])) for b in used) + "\n  },\n",
           '  "layers": [\n']
    layers = []
    for y in range(H):
        rows = []
        for z in range(D):
            line = np.where(sub[:, y, z], lut[np.where(sub[:, y, z], sidx[:, y, z], 0)], " ")
            rows.append("".join(line).rstrip())
        while len(rows) > 1 and not rows[-1]:
            rows.pop()
        layers.append("    [ " + ", ".join(json.dumps(r) for r in rows) + " ]")
    out.append(",\n".join(layers) + "\n  ]\n}\n")
    with open(path, "w", encoding="utf-8") as f:
        f.write("".join(out))
    return W, D, H, int(sub.sum()), len(used)


def preview(occ, idx, path, scale=3):
    """正面・右側面・背面・上から見た完成予想(ブロックの色)"""
    from PIL import Image
    X, Y, Z = occ.shape
    col = RGB[np.where(occ, idx, 0)].astype(np.uint8)
    def view(axis, rev):
        o = np.moveaxis(occ, axis, 0); c = np.moveaxis(col, axis, 0)
        if rev:
            o = o[::-1]; c = c[::-1]
        first = o.argmax(0); has = o.any(0)
        img = np.take_along_axis(c, first[None, ..., None].repeat(3, -1), 0)[0]
        # 奥行きの段差で、すこし暗くして立体感を出す
        d = np.where(has, first.astype(float), np.nan)
        g = np.nan_to_num(np.maximum(np.abs(np.diff(d, axis=0, prepend=d[:1])), np.abs(np.diff(d, axis=1, prepend=d[:, :1]))))
        img = (img * np.clip(1 - 0.05 * g, 0.55, 1)[..., None]).astype(np.uint8)
        return np.where(has[..., None], img, 255).astype(np.uint8)
    # axis 2 = z: 手前(z=0)から見た正面。x は左→右、y は下→上
    front = view(2, False).transpose(1, 0, 2)[::-1]
    back = view(2, True).transpose(1, 0, 2)[::-1, ::-1]
    side = view(0, True)[::-1]                                     # +x 側から見た側面(手前が左)
    top = view(1, True).transpose(1, 0, 2)                         # 上から(手前が下)
    top = top[::-1]
    imgs = [Image.fromarray(a) for a in (front, side, back, top)]
    Hh = max(i.height for i in imgs)
    tot = Image.new("RGB", (sum(i.width for i in imgs) + 12 * 3, Hh), (255, 255, 255))
    x = 0
    for i in imgs:
        tot.paste(i, (x, Hh - i.height)); x += i.width + 12
    tot = tot.resize((tot.width * scale, tot.height * scale), Image.NEAREST)
    tot.save(path)


# ---------------------------------------------------------------- 画像の切りぬき(背景を消す)
def cutout(img, bg="auto", tol=40):
    """PIL の画像 → (RGB の画像, 前景の真偽(H,W))。背景は、透明・画像のふちの色・指定色のうち、ふちにつながる部分"""
    from PIL import Image
    rgba = img.convert("RGBA")
    a = np.array(rgba).astype(float)
    rgb = a[..., :3]
    alpha = a[..., 3]
    H, W = alpha.shape
    if bg == "alpha" or (bg == "auto" and (alpha < 250).mean() > 0.01):
        fg = alpha >= 128
    else:
        if bg in ("auto", "corner"):
            border = np.concatenate([rgb[0], rgb[-1], rgb[:, 0], rgb[:, -1]])
            ref = np.median(border, axis=0)
        elif bg == "white":
            ref = np.array([255.0, 255.0, 255.0])
        elif bg == "black":
            ref = np.array([0.0, 0.0, 0.0])
        else:
            ref = np.array([int(bg[i:i + 2], 16) for i in (0, 2, 4)], float)      # RRGGBB
        cand = np.sqrt(((rgb - ref) ** 2).sum(-1)) <= tol
        outside = np.zeros_like(cand)
        outside[0, :] = cand[0, :]; outside[-1, :] = cand[-1, :]; outside[:, 0] = cand[:, 0]; outside[:, -1] = cand[:, -1]
        while True:                                                     # ふちからつながる背景を広げる
            grown = outside.copy()
            grown[1:] |= outside[:-1]; grown[:-1] |= outside[1:]; grown[:, 1:] |= outside[:, :-1]; grown[:, :-1] |= outside[:, 1:]
            grown &= cand
            if (grown == outside).all():
                break
            outside = grown
        fg = ~outside
    return rgb, fg


def distance_in(mask, limit=200):
    """各画素から、いちばん近い背景(マスクの外)までの距離(4 近傍の面取り距離)。numpy だけで計算"""
    H, W = mask.shape
    INF = 10 ** 6
    d = np.where(mask, INF, 0).astype(np.int64)
    # 面取り距離(縦横 3、斜め 4)を 2 回なぞって近似
    for y in range(H):
        row = d[y]
        if y > 0:
            row = np.minimum(row, d[y - 1] + 3)
            row[1:] = np.minimum(row[1:], d[y - 1][:-1] + 4)
            row[:-1] = np.minimum(row[:-1], d[y - 1][1:] + 4)
        for x in range(1, W):
            if row[x - 1] + 3 < row[x]:
                row[x] = row[x - 1] + 3
        d[y] = row
    for y in range(H - 1, -1, -1):
        row = d[y]
        if y < H - 1:
            row = np.minimum(row, d[y + 1] + 3)
            row[1:] = np.minimum(row[1:], d[y + 1][:-1] + 4)
            row[:-1] = np.minimum(row[:-1], d[y + 1][1:] + 4)
        for x in range(W - 2, -1, -1):
            if row[x + 1] + 3 < row[x]:
                row[x] = row[x + 1] + 3
        d[y] = row
    # 画像のふち(外側)は背景として扱う
    out = d / 3.0
    yy, xx = np.mgrid[0:H, 0:W]
    edge = np.minimum(np.minimum(yy + 1, H - yy), np.minimum(xx + 1, W - xx)).astype(float)
    return np.minimum(np.where(mask, out, 0.0), edge)
