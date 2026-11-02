#!/usr/bin/env python3
"""画像から「たいまつの上のコンクリートパウダーが落ちて、文字 → 画像ができる」仕掛けの JSON を作る。

できるもの(出力先に 2 つ):
  <名前>.json       … 建てる仕掛け(1 層目: たいまつ、2 層目: 文字を描いたコンクリートパウダー)。/statue build <名前> 0 で建てる
  <名前>.drop.json  … 最後に落ちてくる「画像」のコンクリートパウダー(16 色)。/statue drop が読む

ゲーム内の流れ:
  1. /statue build <名前> 0          仕掛けを建てる(足もとに草の台を敷いてから)
  2. /statue drop                   渦巻きの外側から 1 個飛ばしで、たいまつを壊す → 文字ができる
                                    そのあと、画像のコンクリートパウダーが、外側から 1 個飛ばしで落ちてきて、画像ができる

使い方:
  python3 image_to_drop.py 画像.png --text "HELLO" -o 出力フォルダ
  python3 image_to_drop.py --watch 画像のフォルダ -o 出力フォルダ     … フォルダに画像を入れると自動で作る(Ctrl+C で終了)
オプション:
  --size 400            … 画像の長辺のブロック数(上限)
  --text "文字"         … 文字(\\n で改行)。省略すると、ファイル名。同じ名前の .txt があれば、その中身を使う(日本語もこちらが楽)
  --text-color white --bg-color black … 文字と、文字の下地の色(16 色の名前: white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black)
  --font C:\\Windows\\Fonts\\meiryo.ttc … 文字のフォント(日本語を出すとき。省略すると、Windows の日本語フォントを自動で探す)
  --no-dither           … 画像の色を混ぜない(既定は混ぜる。コンクリートパウダーは 16 色しかないため)
  --preview p.png       … 完成予想(文字の段・画像の段)
必要なもの: Python 3、pip install pillow numpy"""
import sys, os, json, re, time, argparse
import numpy as np
from PIL import Image, ImageDraw, ImageFont

POWDER = {
    "black": (25, 27, 32), "blue": (70, 73, 167), "brown": (126, 85, 54), "cyan": (37, 148, 157),
    "gray": (77, 81, 85), "green": (97, 119, 45), "light_blue": (74, 181, 213), "light_gray": (155, 155, 148),
    "lime": (125, 189, 42), "magenta": (193, 84, 185), "orange": (227, 132, 32), "pink": (229, 153, 181),
    "purple": (132, 56, 178), "red": (168, 54, 51), "white": (226, 228, 228), "yellow": (233, 199, 55)}
NAMES = list(POWDER)
RGB = np.array([POWDER[n] for n in NAMES], float)
KEYS = "abcdefghijklmnop"
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\meiryo.ttc", r"C:\Windows\Fonts\msgothic.ttc", r"C:\Windows\Fonts\YuGothB.ttc",
    r"C:\Windows\Fonts\YuGothM.ttc", r"C:\Windows\Fonts\arialbd.ttf", r"C:\Windows\Fonts\arial.ttf",
    "/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc", "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]

def to_lab(rgb):
    c = rgb / 255.0
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    M = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = c @ M.T / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)
LAB = to_lab(RGB)

def quantize(img, w, h, dither):
    small = np.array(img.convert("RGB").resize((w, h), Image.LANCZOS)).astype(float)
    idx = np.zeros((h, w), int)
    if not dither:
        lab = to_lab(small)
        for y in range(h):
            idx[y] = ((lab[y][:, None, :] - LAB[None]) ** 2).sum(-1).argmin(-1)
        return idx
    buf = small.copy()
    for y in range(h):
        for x in range(w):
            old = np.clip(buf[y, x], 0, 255)
            i = ((to_lab(old[None]) - LAB) ** 2).sum(-1).argmin()
            idx[y, x] = i
            err = old - RGB[i]
            if x + 1 < w: buf[y, x + 1] += err * 7 / 16
            if y + 1 < h:
                if x > 0: buf[y + 1, x - 1] += err * 3 / 16
                buf[y + 1, x] += err * 5 / 16
                if x + 1 < w: buf[y + 1, x + 1] += err * 1 / 16
    return idx

def find_font(path):
    for p in ([path] if path else []) + FONT_CANDIDATES:
        if p and os.path.isfile(p):
            return p
    return None

def text_mask(text, w, h, font_path):
    """w×h の真偽の画像。文字の部分が True。大きさは、はみ出さない最大に合わせる"""
    text = text.replace("\\n", "\n")
    fp = find_font(font_path)
    def font(size):
        return ImageFont.truetype(fp, size) if fp else ImageFont.load_default(size)
    lo, hi, best = 8, max(w, h), 8
    probe = ImageDraw.Draw(Image.new("L", (10, 10)))
    while lo <= hi:
        mid = (lo + hi) // 2
        l, t, r, b = probe.multiline_textbbox((0, 0), text, font=font(mid), align="center", spacing=mid // 6)
        if (r - l) <= w * 0.9 and (b - t) <= h * 0.7:
            best = mid; lo = mid + 1
        else:
            hi = mid - 1
    f = font(best)
    im = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(im)
    l, t, r, b = d.multiline_textbbox((0, 0), text, font=f, align="center", spacing=best // 6)
    d.multiline_text(((w - (r - l)) / 2 - l, (h - (b - t)) / 2 - t), text, font=f, fill=255, align="center", spacing=best // 6)
    return np.array(im) >= 128

def convert(image_path, out_dir, text, size, text_color, bg_color, font, dither, preview=None):
    img = Image.open(image_path).convert("RGB")
    scale = size / max(img.width, img.height)
    w = max(1, round(img.width * scale)); h = max(1, round(img.height * scale))
    stem = re.sub(r"[^A-Za-z0-9_.+-]", "_", os.path.splitext(os.path.basename(image_path))[0]).strip("_") or "image"
    idx = quantize(img, w, h, dither)                       # 画像(上が画像の上)
    mask = text_mask(text, w, h, font)
    # 行は「先頭 = 手前(プレイヤー側) = 画像の下」。画像・文字は上下をひっくり返して並べる
    img_rows = ["".join(KEYS[i] for i in idx[y]) for y in range(h - 1, -1, -1)]
    torch_rows = ["T" * w for _ in range(h)]
    text_rows = ["".join("a" if mask[y, x] else "b" for x in range(w)) for y in range(h - 1, -1, -1)]
    os.makedirs(out_dir, exist_ok=True)
    structure = {
        "name": stem, "size": [w, h, 2],
        "comment": "たいまつの上にコンクリートパウダーを載せた仕掛け。/statue build %s 0 で建てて、/statue drop で落とす" % stem,
        "drop": stem + ".drop.json",
        "palette": {"T": "minecraft:torch", "a": "minecraft:%s_concrete_powder" % text_color, "b": "minecraft:%s_concrete_powder" % bg_color},
        "layers": [torch_rows, text_rows]}
    drop = {"name": stem, "width": w, "height": h,
            "palette": {KEYS[i]: "minecraft:%s_concrete_powder" % NAMES[i] for i in sorted(set(idx.ravel().tolist()))},
            "rows": img_rows}
    def dump(obj, path):
        with open(path, "w", encoding="utf-8") as f:
            f.write("{\n")
            items = list(obj.items())
            for k, (key, val) in enumerate(items):
                if key in ("layers",):
                    f.write('  "layers": [\n' + ",\n".join("    [\n" + ",\n".join("      " + json.dumps(r) for r in layer) + "\n    ]" for layer in val) + "\n  ]")
                elif key == "rows":
                    f.write('  "rows": [\n' + ",\n".join("    " + json.dumps(r) for r in val) + "\n  ]")
                else:
                    f.write("  %s: %s" % (json.dumps(key), json.dumps(val, ensure_ascii=False)))
                f.write(",\n" if k + 1 < len(items) else "\n")
            f.write("}\n")
    p1 = os.path.join(out_dir, stem + ".json"); p2 = os.path.join(out_dir, stem + ".drop.json")
    dump(structure, p1); dump(drop, p2)
    if preview:
        pimg = RGB[idx].astype(np.uint8)
        tcol = np.array(POWDER[text_color]); bcol = np.array(POWDER[bg_color])
        timg = np.where(mask[..., None], tcol, bcol).astype(np.uint8)
        k = max(1, 700 // max(w, h))
        both = np.concatenate([timg, np.full((h, 8, 3), 255, np.uint8), pimg], 1)
        Image.fromarray(both).resize((both.shape[1] * k, both.shape[0] * k), Image.NEAREST).save(preview)
    print("%s: %d × %d ブロック(たいまつ %d + 文字 %d + 画像 %d)" % (stem, w, h, w * h, w * h, w * h))
    print("  → %s / %s" % (p1, p2))
    print("  ゲーム内: 2 つを .minecraft/statues/ に入れて、/statue build %s 0 → /statue drop" % stem)
    return stem

def text_for(image_path, default_text):
    side = os.path.splitext(image_path)[0] + ".txt"
    if os.path.isfile(side):
        for enc in ("utf-8-sig", "cp932"):
            try:
                return open(side, encoding=enc).read().strip()
            except UnicodeDecodeError:
                continue
    return default_text if default_text else os.path.splitext(os.path.basename(image_path))[0]

if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="画像 → 落ちて文字と画像ができる仕掛けの JSON")
    ap.add_argument("image", nargs="?")
    ap.add_argument("--watch", help="このフォルダに画像が入ったら、自動で作る")
    ap.add_argument("-o", "--out", default=".", help="出力フォルダ(.minecraft/statues を指定すると、そのままゲームで使える)")
    ap.add_argument("--size", type=int, default=400)
    ap.add_argument("--text")
    ap.add_argument("--text-color", default="white", choices=NAMES)
    ap.add_argument("--bg-color", default="black", choices=NAMES)
    ap.add_argument("--font")
    ap.add_argument("--no-dither", action="store_true")
    ap.add_argument("--preview")
    a = ap.parse_args()
    run = lambda path: convert(path, a.out, text_for(path, a.text), a.size, a.text_color, a.bg_color, a.font, not a.no_dither, a.preview)
    if a.watch:
        print("見はり中: %s(Ctrl+C で終了)。画像を入れると、%s に JSON を作ります" % (a.watch, a.out))
        seen = {}
        try:
            while True:
                for fn in sorted(os.listdir(a.watch)):
                    p = os.path.join(a.watch, fn)
                    if os.path.splitext(fn)[1].lower() in (".png", ".jpg", ".jpeg", ".webp", ".bmp") and os.path.isfile(p):
                        m = os.path.getmtime(p)
                        if seen.get(p) != m and time.time() - m > 1.0:       # 書きこみ中でない
                            seen[p] = m
                            try:
                                run(p)
                            except Exception as e:
                                print("%s: 失敗 %s" % (fn, e))
                time.sleep(2)
        except KeyboardInterrupt:
            print("終了")
    elif a.image:
        run(a.image)
    else:
        ap.error("画像、または --watch フォルダを指定してください")
