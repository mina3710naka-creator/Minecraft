#!/usr/bin/env python3
"""魔法MOD の独自演出用の絵(大きな魔法陣・輪・斬撃・光の柱・稲妻・球)と独自パーティクルの絵を描く。

gen_assets.py から呼ばれる(単体でも python3 -I tools/gen_fx.py で動く)。すべて手続きで描いた絵。
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "magicmod")
NS = "magicmod"

# 主・副・光
PAL = {
    "wind": ((155, 232, 180), (230, 255, 240), (255, 255, 255)),
    "thunder": ((255, 232, 74), (110, 200, 255), (255, 255, 255)),
    "fire": ((255, 90, 26), (255, 192, 64), (255, 240, 176)),
    "water": ((47, 123, 255), (159, 224, 255), (232, 255, 255)),
    "gravity": ((140, 70, 240), (60, 20, 110), (215, 170, 255)),
    "dark": ((120, 60, 170), (40, 20, 60), (190, 120, 255)),
    "light": ((255, 236, 140), (255, 255, 255), (255, 255, 224)),
    "curse": ((200, 40, 150), (90, 10, 60), (255, 110, 200)),
}


def save(img, *path):
    p = os.path.join(ASSETS, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def glow(img, radius, strength=1.0):
    """線の絵に、ぼかした光を下に敷く。"""
    blur = img.filter(ImageFilter.GaussianBlur(radius))
    if strength != 1.0:
        a = blur.getchannel("A").point(lambda v: min(255, int(v * strength)))
        blur.putalpha(a)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out = Image.alpha_composite(out, blur)
    out = Image.alpha_composite(out, blur)
    return Image.alpha_composite(out, img)


def poly(cx, cy, r, n, rot=0.0):
    return [(cx + math.cos(rot + 2 * math.pi * i / n) * r, cy + math.sin(rot + 2 * math.pi * i / n) * r) for i in range(n)]


def star_lines(d, pts, step, col, w):
    n = len(pts)
    for i in range(n):
        d.line([pts[i], pts[(i + step) % n]], fill=col, width=w)


def magic_circle(el):
    S = 256
    c = S / 2
    main, sub, hi = PAL[el]
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    A = lambda col, a=255: col + (a,)
    d.ellipse([c - 122, c - 122, c + 122, c + 122], outline=A(main), width=5)
    d.ellipse([c - 112, c - 112, c + 112, c + 112], outline=A(hi, 220), width=2)
    d.ellipse([c - 86, c - 86, c + 86, c + 86], outline=A(main), width=3)
    rnd = random.Random(el)
    # 外周のルーン帯
    for i in range(36):
        a = 2 * math.pi * i / 36
        r1, r2 = 92, 108
        x1, y1 = c + math.cos(a) * r1, c + math.sin(a) * r1
        x2, y2 = c + math.cos(a) * r2, c + math.sin(a) * r2
        kind = rnd.randint(0, 3)
        if kind == 0:
            d.line([(x1, y1), (x2, y2)], fill=A(hi), width=2)
        elif kind == 1:
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2
            d.ellipse([mx - 4, my - 4, mx + 4, my + 4], outline=A(hi), width=2)
        elif kind == 2:
            b = a + 0.06
            d.line([(x1, y1), (c + math.cos(b) * r2, c + math.sin(b) * r2)], fill=A(hi), width=2)
            d.line([(x2, y2), (c + math.cos(b) * r1, c + math.sin(b) * r1)], fill=A(hi), width=2)
        else:
            mx, my = (x1 + x2) / 2, (y1 + y2) / 2
            d.rectangle([mx - 3, my - 3, mx + 3, my + 3], outline=A(hi), width=2)
    # 属性ごとの紋様
    if el == "fire":
        star_lines(d, poly(c, c, 84, 6, -math.pi / 2), 2, A(sub), 3)
        for i in range(6):
            a = -math.pi / 2 + 2 * math.pi * i / 6
            x, y = c + math.cos(a) * 60, c + math.sin(a) * 60
            d.polygon([(x, y - 12), (x + 7, y + 6), (x - 7, y + 6)], outline=A(hi), width=2)
        d.polygon([(c, c - 30), (c + 18, c + 18), (c, c + 6), (c - 18, c + 18)], fill=A(main, 200), outline=A(hi), width=2)
    elif el == "thunder":
        star_lines(d, poly(c, c, 84, 8, 0), 3, A(sub), 3)
        bolt = [(c + 8, c - 40), (c - 16, c + 4), (c + 2, c + 4), (c - 10, c + 42), (c + 20, c - 6), (c + 2, c - 6)]
        d.polygon(bolt, fill=A(main, 230), outline=A(hi), width=2)
    elif el == "water":
        for r in (70, 56, 42):
            d.ellipse([c - r, c - r, c + r, c + r], outline=A(sub, 220), width=2)
        for i in range(8):
            a = 2 * math.pi * i / 8
            pts = [(c + math.cos(a + t * 0.03) * (30 + t), c + math.sin(a + t * 0.03) * (30 + t)) for t in range(0, 50, 2)]
            d.line(pts, fill=A(main), width=2)
        d.ellipse([c - 14, c - 8, c + 14, c + 22], fill=A(main, 220), outline=A(hi), width=2)
        d.polygon([(c, c - 26), (c + 12, c), (c - 12, c)], fill=A(main, 220))
    elif el == "wind":
        for k in range(4):
            base = k * math.pi / 2
            pts = [(c + math.cos(base + t * 0.05) * (10 + t * 1.2), c + math.sin(base + t * 0.05) * (10 + t * 1.2)) for t in range(0, 64)]
            d.line(pts, fill=A(main), width=4)
            d.line(pts, fill=A(hi, 200), width=1)
        star_lines(d, poly(c, c, 84, 4, math.pi / 4), 1, A(sub), 2)
    elif el == "gravity":
        for k in range(4):
            pts = poly(c, c, 80 - k * 16, 4, k * math.pi / 8)
            d.polygon(pts, outline=A(main if k % 2 == 0 else hi), width=3)
        d.ellipse([c - 16, c - 16, c + 16, c + 16], fill=(5, 0, 12, 255), outline=A(hi), width=3)
    elif el == "dark":
        star_lines(d, poly(c, c, 84, 5, math.pi / 2), 2, A(main), 3)
        d.ellipse([c - 28, c - 14, c + 28, c + 14], outline=A(hi), width=3)
        d.ellipse([c - 9, c - 9, c + 9, c + 9], fill=A(hi, 255))
        d.ellipse([c - 4, c - 4, c + 4, c + 4], fill=(0, 0, 0, 255))
    elif el == "light":
        for i in range(24):
            a = 2 * math.pi * i / 24
            r1 = 24 if i % 2 == 0 else 36
            d.line([(c + math.cos(a) * r1, c + math.sin(a) * r1), (c + math.cos(a) * 84, c + math.sin(a) * 84)], fill=A(main, 230),
                   width=3 if i % 2 == 0 else 1)
        d.ellipse([c - 20, c - 20, c + 20, c + 20], fill=A(hi, 255), outline=A(main), width=3)
    elif el == "curse":
        star_lines(d, poly(c, c, 84, 5, -math.pi / 2), 2, A(main), 3)
        for i in range(5):
            a = -math.pi / 2 + 2 * math.pi * i / 5
            x, y = c + math.cos(a) * 84, c + math.sin(a) * 84
            d.ellipse([x - 8, y - 8, x + 8, y + 8], fill=A(sub), outline=A(hi), width=2)
        # 小さな髑髏
        d.ellipse([c - 18, c - 22, c + 18, c + 10], fill=A(hi, 240))
        d.rectangle([c - 10, c + 4, c + 10, c + 16], fill=A(hi, 240))
        d.ellipse([c - 11, c - 10, c - 3, c - 2], fill=(20, 0, 15, 255))
        d.ellipse([c + 3, c - 10, c + 11, c - 2], fill=(20, 0, 15, 255))
    return glow(img, 5, 1.2)


def ring(col, hi):
    S = 128
    c = S / 2
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i, r in enumerate(range(62, 46, -1)):
        a = int(255 * (1 - i / 16) ** 1.6)
        d.ellipse([c - r, c - r, c + r, c + r], outline=col + (a,), width=1)
    d.ellipse([c - 61, c - 61, c + 61, c + 61], outline=hi + (255,), width=2)
    return glow(img, 3)


def slash(col, hi):
    """三日月の斬撃: 外側の弧に沿って、真ん中が太く両端が細い帯。"""
    S = 128
    cx, cy = 64, 92

    def crescent(r_out, thick, n=60):
        outer, inner = [], []
        for i in range(n + 1):
            t = i / n
            a = math.radians(200 + 140 * t)
            w = thick * math.sin(math.pi * t) ** 0.8
            outer.append((cx + math.cos(a) * r_out, cy + math.sin(a) * r_out))
            inner.append((cx + math.cos(a) * (r_out - w), cy + math.sin(a) * (r_out - w)))
        return outer + inner[::-1]

    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon(crescent(60, 22), fill=col + (170,))
    d.polygon(crescent(60, 12), fill=tuple(int((a + b) / 2) for a, b in zip(col, hi)) + (220,))
    d.polygon(crescent(60, 5), fill=hi + (255,))
    return glow(img, 2)


def beam(col, hi):
    S = 64
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(col[0] * 7 + col[1])
    for y in range(S):
        flick = 0.85 + 0.15 * rnd.random()
        for x in range(S):
            d = abs(x - 31.5) / 32
            core = max(0.0, 1 - d * 5)
            body = max(0.0, 1 - d * 1.2) ** 2
            a = min(1.0, body * 0.7 + core) * flick
            r = int(col[0] + (hi[0] - col[0]) * core)
            g = int(col[1] + (hi[1] - col[1]) * core)
            b = int(col[2] + (hi[2] - col[2]) * core)
            px[x, y] = (r, g, b, int(255 * a))
    return img


def bolt_tex(col, hi):
    S = 128
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = random.Random(42)
    pts = [(64, 0)]
    y = 0
    while y < S:
        y += rnd.randint(8, 16)
        pts.append((64 + rnd.randint(-18, 18), min(S, y)))
    for p0, p1 in zip(pts, pts[1:]):
        d.line([p0, p1], fill=col + (255,), width=7)
    for p0, p1 in zip(pts, pts[1:]):
        d.line([p0, p1], fill=hi + (255,), width=3)
    for i in range(3):
        k = rnd.randint(2, len(pts) - 3)
        bx, by = pts[k]
        tx, ty = bx + rnd.choice([-1, 1]) * rnd.randint(14, 30), by + rnd.randint(10, 26)
        d.line([(bx, by), (tx, ty)], fill=col + (230,), width=3)
        d.line([(bx, by), (tx, ty)], fill=hi + (255,), width=1)
    return glow(img, 4, 1.3)


def orb(dark):
    S = 128
    c = S / 2
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    for y in range(S):
        for x in range(S):
            r = math.hypot(x - c + 0.5, y - c + 0.5) / 60
            if r > 1:
                continue
            if dark:
                rim = max(0.0, 1 - abs(r - 0.82) * 9)
                core = r < 0.78
                if core:
                    px[x, y] = (4, 0, 10, 255)
                else:
                    px[x, y] = (int(150 + 100 * rim), int(60 + 120 * rim), 255, int(255 * max(rim, (1 - r) * 3)))
            else:
                a = (1 - r) ** 0.8
                px[x, y] = (255, 255, int(200 + 55 * a), int(255 * a))
    return glow(img, 3) if dark else img


# ---------------------------------------------------------------------------------------------
# パーティクル(16×16、コマ送り)

def P():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def particles():
    out = {}
    fr = []
    for k in range(4):
        im = P()
        d = ImageDraw.Draw(im)
        h = 13 - k * 2
        w = 5 - k
        d.polygon([(8, 15 - h), (8 + w, 13), (8, 15), (8 - w, 13)], fill=(255, 120 + k * 20, 30, 255))
        d.polygon([(8, 15 - h + 4), (8 + max(1, w - 2), 13), (8, 15), (8 - max(1, w - 2), 13)], fill=(255, 230, 140, 255))
        fr.append(glow(im, 1))
    out["ember"] = fr
    fr = []
    rnd = random.Random(3)
    for k in range(4):
        im = P()
        d = ImageDraw.Draw(im)
        pts = [(rnd.randint(2, 6), 1)]
        y = 1
        while y < 15:
            y += rnd.randint(3, 5)
            pts.append((rnd.randint(3, 12), min(15, y)))
        d.line(pts, fill=(255, 240, 120, 255), width=2)
        d.line(pts, fill=(255, 255, 255, 255), width=1)
        fr.append(glow(im, 1))
    out["spark"] = fr
    fr = []
    for k in range(3):
        im = P()
        d = ImageDraw.Draw(im)
        if k < 2:
            d.ellipse([5, 7 - k, 11, 14], fill=(70, 150, 255, 230))
            d.polygon([(8, 2 - k), (11, 9), (5, 9)], fill=(70, 150, 255, 230))
            d.point((7, 9), fill=(230, 250, 255, 255))
        else:
            for a in range(6):
                ang = a * math.pi / 3
                d.point((8 + round(math.cos(ang) * 5), 10 + round(math.sin(ang) * 3)), fill=(140, 200, 255, 255))
        fr.append(im)
    out["droplet"] = fr
    fr = []
    for k in range(4):
        im = P()
        d = ImageDraw.Draw(im)
        d.arc([1, 1, 15, 15], start=200 + k * 25, end=330 + k * 25, fill=(230, 255, 240, 220), width=2)
        d.arc([4, 4, 12, 12], start=20 + k * 25, end=150 + k * 25, fill=(155, 232, 180, 200), width=1)
        fr.append(im)
    out["gale"] = fr
    fr = []
    for k in range(4):
        im = P()
        d = ImageDraw.Draw(im)
        r = 6 - k
        d.ellipse([8 - r, 8 - r, 8 + r, 8 + r], outline=(200, 140, 255, 255), width=1)
        d.ellipse([8 - max(1, r - 2), 8 - max(1, r - 2), 8 + max(1, r - 2), 8 + max(1, r - 2)], fill=(20, 0, 40, 255))
        fr.append(glow(im, 1))
    out["void_mote"] = fr
    fr = []
    for k in range(4):
        im = P()
        d = ImageDraw.Draw(im)
        for i in range(5 + k * 2):
            x, y = 8 + rnd.gauss(0, 2 + k * 0.6), 8 + rnd.gauss(0, 2 + k * 0.6)
            rr = 2 + rnd.random() * 2
            d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(40 + k * 8, 15, 60 + k * 10, 150))
        d.ellipse([6, 6, 10, 10], fill=(150, 80, 220, 120))
        fr.append(im.filter(ImageFilter.GaussianBlur(0.6)))
    out["shadow_wisp"] = fr
    fr = []
    for k in range(4):
        im = P()
        d = ImageDraw.Draw(im)
        L = [7, 6, 4, 3][k]
        d.line([(8, 8 - L), (8, 8 + L)], fill=(255, 250, 200, 255), width=1)
        d.line([(8 - L, 8), (8 + L, 8)], fill=(255, 250, 200, 255), width=1)
        d.ellipse([6, 6, 10, 10], fill=(255, 255, 255, 255))
        fr.append(glow(im, 1, 1.4))
    out["holy_star"] = fr
    fr = []
    for k in range(4):
        im = P()
        d = ImageDraw.Draw(im)
        a = 255 if k % 2 == 0 else 190
        d.ellipse([4, 3, 12, 10], fill=(255, 120, 210, a))
        d.rectangle([6, 9, 10, 12], fill=(255, 120, 210, a))
        d.point([(6, 6), (7, 6), (9, 6), (10, 6)], fill=(40, 0, 30, 255))
        d.point([(7, 11), (9, 11)], fill=(40, 0, 30, 255))
        fr.append(glow(im, 1))
    out["curse_mote"] = fr
    fr = []
    shapes = [
        [(4, 3), (12, 3), (8, 13)], [(3, 8), (13, 8)], [(8, 2), (8, 14), (4, 9), (12, 9)], [(4, 4), (12, 12), (12, 4), (4, 12)],
        [(3, 12), (8, 3), (13, 12), (3, 12)], [(8, 2), (13, 8), (8, 14), (3, 8), (8, 2)], [(4, 3), (4, 13), (12, 13)],
        [(3, 3), (13, 3), (13, 13), (3, 13), (3, 3)],
    ]
    for k in range(8):
        im = P()
        d = ImageDraw.Draw(im)
        s = shapes[k]
        if k == 1:
            d.line(s, fill=(170, 230, 255, 255), width=1)
            d.ellipse([5, 5, 11, 11], outline=(170, 230, 255, 255))
        elif k == 3:
            d.line(s[:2], fill=(170, 230, 255, 255), width=1)
            d.line(s[2:], fill=(170, 230, 255, 255), width=1)
        else:
            d.line(s, fill=(170, 230, 255, 255), width=1)
        fr.append(glow(im, 1, 1.5))
    out["rune"] = fr
    fr = []
    for k in range(3):
        im = P()
        d = ImageDraw.Draw(im)
        L = [7, 5, 3][k]
        for a in range(4):
            ang = a * math.pi / 4
            d.line([(8 - math.cos(ang) * L, 8 - math.sin(ang) * L), (8 + math.cos(ang) * L, 8 + math.sin(ang) * L)], fill=(255, 255, 255, 255))
        fr.append(glow(im, 1, 1.4))
    out["steel_flash"] = fr
    return out


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def flat_model(tex):
    return {"textures": {"t": tex, "particle": tex}, "elements": [{
        "from": [0, 8, 0], "to": [16, 8, 16], "shade": False, "light_emission": 15,
        "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#t"}, "down": {"uv": [0, 16, 16, 0], "texture": "#t"}}}]}


def vertical_model(tex):
    return {"textures": {"t": tex, "particle": tex}, "elements": [{
        "from": [0, 0, 8], "to": [16, 16, 8], "shade": False, "light_emission": 15,
        "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#t"}, "south": {"uv": [0, 0, 16, 16], "texture": "#t"}}}]}


def fx_item(name, img, vertical):
    save(img, "textures", "item", "fx", name + ".png")
    tex = "%s:item/fx/%s" % (NS, name)
    write_json(os.path.join(ASSETS, "models", "item", "fx_" + name + ".json"), vertical_model(tex) if vertical else flat_model(tex))
    write_json(os.path.join(ASSETS, "items", "fx_" + name + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/fx_%s" % (NS, name)}})


def main():
    for el, (main_c, sub_c, hi_c) in PAL.items():
        fx_item("circle_" + el, magic_circle(el), False)
        fx_item("ring_" + el, ring(main_c, hi_c), False)
        fx_item("slash_" + el, slash(main_c, hi_c), False)
        fx_item("beam_" + el, beam(main_c, hi_c), True)
    fx_item("slash_steel", slash((190, 200, 215), (255, 255, 255)), False)
    fx_item("ring_steel", ring((200, 200, 210), (255, 255, 255)), False)
    fx_item("bolt", bolt_tex((120, 190, 255), (255, 255, 255)), True)
    fx_item("orb_dark", orb(True), True)
    fx_item("orb_light", orb(False), True)
    for pid, frames in particles().items():
        names = []
        for i, im in enumerate(frames):
            save(im, "textures", "particle", "%s_%d.png" % (pid, i))
            names.append("%s:%s_%d" % (NS, pid, i))
        write_json(os.path.join(ASSETS, "particles", pid + ".json"), {"textures": names})
    print("fx done")


if __name__ == "__main__":
    main()
