#!/usr/bin/env python3
"""104 魔法 + 物理わざ 7 カテゴリのアイコンを描く(32×32、属性色の板 + 図柄 + ランクの枠)。

gen_assets.py から呼ばれる。アイコンはアイテムとして登録されるので、画面・HUD・ホイールのどこでも ItemStack として描ける。
図柄は 30 種ほどの部品(球・稲妻・斬撃・盾・竜巻…)を、属性ごとに 13 魔法へ割り当てている。
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "magicmod")
NS = "magicmod"
SS = 4  # 4 倍で描いて縮める
N = 32 * SS

PAL = {
    "wind": ((155, 232, 180), (60, 120, 90), (255, 255, 255)),
    "thunder": ((255, 232, 74), (60, 90, 170), (255, 255, 255)),
    "fire": ((255, 110, 30), (140, 30, 10), (255, 240, 176)),
    "water": ((60, 140, 255), (20, 50, 130), (232, 255, 255)),
    "gravity": ((150, 80, 240), (50, 20, 100), (230, 190, 255)),
    "dark": ((165, 105, 225), (40, 20, 70), (225, 180, 255)),
    "light": ((255, 236, 140), (160, 130, 50), (255, 255, 235)),
    "curse": ((210, 50, 160), (80, 10, 55), (255, 130, 215)),
}

# 属性ごとの 13 魔法の図柄 (部品名, 変化)
MAP = {
    "wind": [("slash", 0), ("wings", 0), ("burst", 0), ("shield", 0), ("vortex_in", 0), ("dash", 0), ("multislash", 0), ("storm", 0),
             ("wings", 1), ("storm", 1), ("chain", 0), ("claw", 0), ("boomerang", 0)],
    "thunder": [("bolt", 0), ("fist", 1), ("chain", 1), ("field", 0), ("spear", 0), ("burst", 1), ("wall", 0), ("pillar", 1),
                ("mana", 0), ("bolt", 2), ("fist", 0), ("whip", 0), ("dash", 1)],
    "fire": [("orb", 1), ("fist", 2), ("field", 1), ("burst", 2), ("spear", 1), ("mist", 1), ("wall", 1), ("meteor", 0),
             ("wings", 2), ("beam", 1), ("fist", 0), ("claw", 1), ("whip", 1)],
    "water": [("drop", 0), ("fist", 3), ("orb", 2), ("rain", 0), ("wave", 0), ("star", 0), ("shield", 1), ("vortex_in", 1),
              ("heal", 0), ("wave", 1), ("fist", 4), ("whip", 5), ("orb", 7)],
    "gravity": [("orb", 3), ("feather", 0), ("chain", 3), ("up", 0), ("down", 0), ("up", 1), ("wall", 2), ("blackhole", 0),
                ("down", 1), ("blackhole", 1), ("fist", 5), ("boomerang", 1), ("feather", 1)],
    "dark": [("orb", 4), ("fist", 6), ("eye", 0), ("mist", 0), ("drain", 0), ("dash", 2), ("doom", 0), ("eye", 1), ("clone", 0),
             ("portal", 0), ("claw", 2), ("whip", 2), ("orb", 5)],
    "light": [("spear", 2), ("heal", 1), ("hex", 0), ("sun", 1), ("beam", 2), ("field", 2), ("rain", 1), ("shield", 2), ("revive", 0),
              ("pillar", 2), ("fist", 7), ("whip", 3), ("orb", 6)],
    "curse": [("down", 2), ("chain", 2), ("eye", 2), ("drop", 1), ("shield", 3), ("ban", 0), ("drain", 1), ("spiral", 0), ("lock", 0),
              ("doom", 1), ("fist", 8), ("whip", 4), ("skull", 1)],
}


def P(x):
    return int(round(x * SS))


def glow(img, radius, strength=1.0):
    blur = img.filter(ImageFilter.GaussianBlur(radius * SS))
    if strength != 1.0:
        blur.putalpha(blur.getchannel("A").point(lambda v: min(255, int(v * strength))))
    return Image.alpha_composite(Image.alpha_composite(Image.new("RGBA", img.size, (0, 0, 0, 0)), blur), img)


class G:
    def __init__(self, el, var):
        self.m, self.s, self.h = PAL[el]
        self.var = var
        self.img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def A(self, c, a=255):
        return tuple(c) + (a,)

    def line(self, pts, col, w):
        self.d.line([(P(x), P(y)) for x, y in pts], fill=self.A(col), width=max(1, P(w)), joint="curve")

    def poly(self, pts, col, outline=None):
        self.d.polygon([(P(x), P(y)) for x, y in pts], fill=self.A(col), outline=self.A(outline) if outline else None)

    def ell(self, x0, y0, x1, y1, fill=None, outline=None, w=1):
        self.d.ellipse([P(x0), P(y0), P(x1), P(y1)], fill=self.A(fill) if fill else None, outline=self.A(outline) if outline else None,
                       width=max(1, P(w)))

    def arc(self, box, a0, a1, col, w):
        self.d.arc([P(box[0]), P(box[1]), P(box[2]), P(box[3])], a0, a1, fill=self.A(col), width=max(1, P(w)))


def orb(g):
    r = 7 + (g.var % 3)
    g.ell(16 - r - 2, 16 - r - 2, 16 + r + 2, 16 + r + 2, fill=g.s)
    g.ell(16 - r, 16 - r, 16 + r, 16 + r, fill=g.m)
    g.ell(16 - r + 2, 16 - r + 2, 16 + r - 4, 16 + r - 4, fill=tuple(min(255, int(c * 1.2)) for c in g.m))
    g.ell(11, 10, 15, 14, fill=g.h)
    if g.var >= 5:
        for i in range(6):
            a = i * math.pi / 3
            g.line([(16 + math.cos(a) * (r + 2), 16 + math.sin(a) * (r + 2)), (16 + math.cos(a) * (r + 5), 16 + math.sin(a) * (r + 5))], g.h, 1)


def bolt(g):
    k = 1 + g.var * 0.12
    pts = [(18, 3), (9, 17), (15, 17), (12, 29), (24, 13), (17, 13), (21, 3)]
    pts = [(16 + (x - 16) * k, 16 + (y - 16) * k) for x, y in pts]
    g.poly(pts, g.m, g.h)
    g.line([pts[0], pts[1]], g.h, 1)
    if g.var == 2:
        g.arc((3, 3, 29, 29), 200, 340, g.h, 1)


def slash(g):
    g.arc((-2, 8, 30, 40), 235, 320, g.s, 7)
    g.arc((-2, 8, 30, 40), 238, 317, g.m, 5)
    g.arc((-2, 8, 30, 40), 245, 310, g.h, 2)
    g.arc((2, 2, 34, 34), 240, 300, g.m, 2)
    g.ell(24, 6, 27, 9, fill=g.h)


def multislash(g):
    for i in range(3):
        g.line([(6 + i * 7, 26), (14 + i * 7, 6)], g.m, 3)
        g.line([(7 + i * 7, 24), (14 + i * 7, 7)], g.h, 1)


def claw(g):
    for i in range(3):
        g.arc((2 + i * 5, -4 + i * 2, 26 + i * 5, 28 + i * 2), 40, 110, g.m, 3)
        g.arc((2 + i * 5, -4 + i * 2, 26 + i * 5, 28 + i * 2), 50, 100, g.h, 1)


def wings(g):
    for sgn in (-1, 1):
        for k in range(4):
            x0 = 16 + sgn * 3
            pts = [(x0, 17 - k * 2), (16 + sgn * (11 + k * 2), 8 + k * 4), (16 + sgn * (8 + k * 2), 18 + k * 3)]
            g.poly(pts, g.m if k % 2 == 0 else g.h, g.s)
    g.ell(14, 13, 18, 22, fill=g.s)
    if g.var == 1:
        for i in range(3):
            g.line([(10 + i * 6, 26), (11 + i * 6, 30)], g.h, 1)
    if g.var == 2:
        g.ell(13, 5, 19, 11, fill=g.h)


def burst(g):
    pts = []
    for i in range(16):
        r = 13 if i % 2 == 0 else 6
        a = math.pi * 2 * i / 16 + g.var * 0.2
        pts.append((16 + math.cos(a) * r, 16 + math.sin(a) * r))
    g.poly(pts, g.m, g.s)
    g.ell(11, 11, 21, 21, fill=g.h)


def shield(g):
    pts = [(16, 3), (27, 7), (25, 19), (16, 29), (7, 19), (5, 7)]
    g.poly(pts, g.s, g.h)
    g.poly([(16, 6), (24, 9), (22, 18), (16, 25), (10, 18), (8, 9)], g.m)
    g.line([(16, 8), (16, 23)], g.h, 1)
    if g.var == 3:
        g.line([(12, 6), (17, 14), (13, 18), (18, 26)], (20, 5, 20), 2)


def spiral(g):
    pts = []
    for i in range(70):
        a = i * 0.28 + g.var
        r = 1.2 + i * 0.19
        pts.append((16 + math.cos(a) * r, 16 + math.sin(a) * r))
    g.line(pts, g.m, 2.5)
    g.line(pts[::2], g.h, 1)


def vortex_in(g):
    spiral(g)
    for a in (0, 2.1, 4.2):
        x, y = 16 + math.cos(a) * 14, 16 + math.sin(a) * 14
        g.poly([(x, y), (x - math.cos(a + 0.5) * 4, y - math.sin(a + 0.5) * 4), (x - math.cos(a - 0.5) * 4, y - math.sin(a - 0.5) * 4)], g.h)


def dash(g):
    for i in range(3):
        o = i * 6
        g.line([(5 + o, 6), (13 + o, 16), (5 + o, 26)], g.m if i else g.h, 3)
    if g.var == 2:
        g.ell(24, 13, 29, 18, fill=g.h)


def storm(g):
    for i in range(6):
        w = 12 - i * 1.7
        y = 5 + i * 4.2
        g.ell(16 - w, y, 16 + w, y + 4, outline=g.m if i % 2 == 0 else g.h, w=1.5)
    if g.var == 1:
        g.line([(14, 26), (18, 30)], g.h, 2)
        for i in range(4):
            g.ell(4 + i * 8, 2 + (i % 2) * 3, 9 + i * 8, 6 + (i % 2) * 3, fill=g.h)


def fist(g):
    g.poly([(8, 12), (22, 10), (26, 18), (23, 27), (11, 27), (7, 20)], g.m, g.s)
    for i in range(4):
        g.line([(9 + i * 4.2, 10), (9 + i * 4.2, 15)], g.s, 1)
    for i in range(5):
        a = -0.6 + i * 0.4
        g.line([(22 + math.cos(a) * 3, 9 + math.sin(a) * 3), (22 + math.cos(a) * 8, 9 + math.sin(a) * 8)], g.h, 1.5)
    g.ell(10, 15, 16, 19, fill=g.h)


def whip(g):
    pts = [(4, 26), (9, 16), (15, 20), (20, 9), (27, 5)]
    g.line(pts, g.s, 4)
    g.line(pts, g.m, 2.4)
    g.line(pts, g.h, 0.8)
    g.ell(25, 3, 30, 8, fill=g.h)


def boomerang(g):
    g.arc((3, 3, 29, 29), 190, 350, g.s, 6)
    g.arc((3, 3, 29, 29), 195, 345, g.m, 4)
    g.arc((3, 3, 29, 29), 205, 335, g.h, 1.5)
    g.arc((8, 8, 24, 24), 10, 170, g.m, 2)


def beam(g):
    g.line([(3, 16), (28, 16)], g.s, 9)
    g.line([(3, 16), (28, 16)], g.m, 6)
    g.line([(3, 16), (28, 16)], g.h, 2.5)
    g.ell(23, 11, 30, 21, fill=g.h)
    if g.var == 2:
        g.line([(3, 9), (28, 9)], g.m, 1.5)
        g.line([(3, 23), (28, 23)], g.m, 1.5)


def wall(g):
    for r in range(3):
        for c in range(3):
            off = 4 if r % 2 else 0
            x = 3 + c * 9 - off
            g.poly([(x, 4 + r * 9), (x + 8, 4 + r * 9), (x + 8, 11 + r * 9), (x, 11 + r * 9)], g.m if (r + c) % 2 else g.s, g.h)


def pillar(g):
    g.poly([(10, 6), (22, 6), (24, 28), (8, 28)], g.m, g.h)
    g.poly([(13, 6), (19, 6), (20, 28), (12, 28)], g.h)
    g.ell(6, 2, 26, 9, outline=g.h, w=1.5)
    if g.var == 1:
        g.poly([(18, 4), (13, 14), (17, 14), (14, 24), (21, 12), (17, 12), (20, 4)], g.h)


def meteor(g):
    g.line([(5, 4), (19, 18)], g.s, 7)
    g.line([(6, 9), (17, 19)], g.m, 4)
    g.ell(16, 15, 28, 27, fill=g.s)
    g.ell(17, 16, 27, 26, fill=g.m)
    g.ell(19, 18, 24, 23, fill=g.h)


def flame(g):
    g.poly([(16, 3), (24, 14), (22, 26), (16, 29), (10, 26), (8, 14), (13, 18)], g.m, g.s)
    g.poly([(16, 12), (20, 20), (18, 26), (14, 26), (12, 20)], g.h)


def wave(g):
    for k in range(3):
        pts = [(2 + i * 1.5, 10 + k * 7 + math.sin(i * 0.7 + k) * (3 + g.var)) for i in range(20)]
        g.line(pts, g.m if k != 1 else g.h, 3)


def drop(g):
    g.poly([(16, 3), (25, 18), (23, 25), (16, 29), (9, 25), (7, 18)], g.s, g.h)
    g.ell(8, 13, 24, 29, fill=g.m)
    g.poly([(16, 6), (22, 18), (10, 18)], g.m)
    g.ell(11, 18, 15, 24, fill=g.h)


def heal(g):
    g.poly([(12, 4), (20, 4), (20, 12), (28, 12), (28, 20), (20, 20), (20, 28), (12, 28), (12, 20), (4, 20), (4, 12), (12, 12)], g.m, g.h)
    g.poly([(14, 8), (18, 8), (18, 14), (24, 14), (24, 18), (18, 18), (18, 24), (14, 24), (14, 18), (8, 18), (8, 14), (14, 14)], g.h)


def rain(g):
    for i in range(6):
        x = 5 + i * 4.6
        y = 4 + (i % 3) * 7
        g.line([(x, y), (x - 2, y + 7)], g.m if g.var == 0 else g.h, 2)
        g.ell(x - 1.5, y + 6, x + 0.5, y + 9, fill=g.h)
    g.ell(3, 22, 29, 29, outline=g.h, w=1.2)


def field(g):
    for r, w in ((13, 1.5), (9, 1.5), (5, 1.5)):
        g.ell(16 - r, 16 - r, 16 + r, 16 + r, outline=g.m if r != 9 else g.h, w=w)
    for i in range(8):
        a = i * math.pi / 4 + g.var
        g.ell(16 + math.cos(a) * 13 - 1.5, 16 + math.sin(a) * 13 - 1.5, 16 + math.cos(a) * 13 + 1.5, 16 + math.sin(a) * 13 + 1.5, fill=g.h)


def chain(g):
    for i in range(4):
        x, y = 5 + i * 6, 6 + i * 6
        g.ell(x - 1, y - 2, x + 7, y + 6, outline=g.m if i % 2 == 0 else g.h, w=2)
    if g.var == 1:
        g.line([(2, 4), (4, 9)], g.h, 1)
        g.line([(28, 22), (30, 28)], g.h, 1)


def feather(g):
    g.poly([(6, 27), (10, 12), (20, 4), (26, 5), (25, 14), (17, 24)], g.m, g.s)
    g.line([(6, 27), (24, 7)], g.h, 1.5)
    for i in range(4):
        g.line([(10 + i * 3.5, 22 - i * 4), (14 + i * 3.5, 22 - i * 3.2)], g.s, 1)


def updown(g, up):
    s = -1 if up else 1
    cy = 16
    g.poly([(16, cy + s * 12), (26, cy - s * 2), (19, cy - s * 2), (19, cy - s * 12), (13, cy - s * 12), (13, cy - s * 2), (6, cy - s * 2)], g.m, g.h)
    if g.var == 1 or g.var == 2:
        g.line([(5, cy + s * 14), (27, cy + s * 14)], g.h, 2)


def up(g):
    updown(g, True)


def down(g):
    updown(g, False)


def blackhole(g):
    g.ell(2, 2, 30, 30, outline=g.m, w=2)
    g.ell(5, 5, 27, 27, outline=g.h, w=1)
    g.ell(8, 8, 24, 24, fill=(4, 0, 10))
    g.arc((4, 4, 28, 28), 20, 150, g.h, 2)
    if g.var == 1:
        g.ell(0, 0, 32, 32, outline=g.s, w=1.2)


def portal(g):
    g.ell(8, 2, 24, 30, fill=g.s, outline=g.h, w=2)
    g.ell(11, 6, 21, 26, fill=(6, 0, 14))
    g.arc((9, 3, 23, 29), 100, 260, g.m, 2)


def eye(g):
    g.poly([(2, 16), (10, 8), (22, 8), (30, 16), (22, 24), (10, 24)], g.s, g.m)
    g.ell(10, 9, 22, 23, fill=g.h)
    g.ell(13, 12, 19, 20, fill=(10, 0, 14) if g.var != 2 else (140, 0, 80))
    if g.var == 1:
        g.line([(4, 27), (28, 5)], g.m, 2)


def skull(g):
    g.ell(7, 4, 25, 22, fill=g.h)
    g.poly([(11, 20), (21, 20), (20, 28), (12, 28)], g.h)
    g.ell(10, 11, 15, 17, fill=(15, 0, 12))
    g.ell(17, 11, 22, 17, fill=(15, 0, 12))
    g.line([(14, 24), (14, 28)], g.s, 1)
    g.line([(18, 24), (18, 28)], g.s, 1)
    if g.var == 1:
        g.arc((2, 2, 30, 30), 180, 360, g.m, 1.5)


def clone(g):
    for dx, col in ((-5, g.s), (4, g.m)):
        g.ell(16 + dx - 5, 4, 16 + dx + 5, 14, fill=col, outline=g.h, w=1)
        g.poly([(16 + dx - 8, 28), (16 + dx - 6, 15), (16 + dx + 6, 15), (16 + dx + 8, 28)], col, g.h)


def sun(g):
    for i in range(12):
        a = i * math.pi / 6
        g.line([(16 + math.cos(a) * 9, 16 + math.sin(a) * 9), (16 + math.cos(a) * 14, 16 + math.sin(a) * 14)], g.m, 2)
    g.ell(8, 8, 24, 24, fill=g.m, outline=g.h, w=1.5)
    g.ell(11, 11, 21, 21, fill=g.h)


def revive(g):
    g.arc((4, 2, 28, 26), 190, 350, g.h, 2)
    g.poly([(14, 8), (18, 8), (18, 14), (24, 14), (24, 18), (18, 18), (18, 29), (14, 29), (14, 18), (8, 18), (8, 14), (14, 14)], g.m, g.h)
    for dx in (-8, 8):
        g.line([(16 + dx, 22), (16 + dx * 1.3, 17)], g.h, 1.5)


def drain(g):
    spiral(g)
    g.ell(12, 12, 20, 20, fill=g.h if g.var == 0 else g.m)
    g.line([(3, 28), (11, 20)], g.h, 2)


def mist(g):
    for x, y, r in ((9, 17, 6), (16, 12, 7), (23, 17, 6), (16, 20, 7)):
        g.ell(x - r, y - r, x + r, y + r, fill=g.m if (x + y) % 2 else g.s)
    if g.var == 1:
        g.ell(11, 10, 15, 14, fill=g.h)


def star(g):
    pts = []
    for i in range(10):
        r = 14 if i % 2 == 0 else 6
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((16 + math.cos(a) * r, 16 + math.sin(a) * r))
    g.poly(pts, g.m, g.h)


def hexs(g):
    for rot in (0, math.pi / 6):
        pts = [(16 + math.cos(rot - math.pi / 2 + i * 2 * math.pi / 3) * 13, 16 + math.sin(rot - math.pi / 2 + i * 2 * math.pi / 3) * 13) for i in range(3)]
        g.poly(pts, g.m, g.h)
    g.ell(11, 11, 21, 21, fill=g.h)


def doom(g):
    g.poly([(8, 4), (24, 4), (17, 16), (24, 28), (8, 28), (15, 16)], g.m, g.h)
    g.poly([(10, 5), (22, 5), (16, 14)], g.h)
    g.poly([(10, 27), (22, 27), (16, 19)], g.s)
    if g.var == 1:
        g.arc((0, 0, 32, 32), 0, 360, g.s, 1.5)


def mana(g):
    g.poly([(16, 2), (26, 14), (16, 30), (6, 14)], g.m, g.h)
    g.poly([(16, 2), (26, 14), (16, 14)], g.h)
    g.line([(6, 14), (26, 14)], g.s, 1)
    g.line([(27, 3), (27, 8)], g.h, 1.5)
    g.line([(24.5, 5.5), (29.5, 5.5)], g.h, 1.5)


def ban(g):
    g.ell(4, 4, 28, 28, outline=g.m, w=3.5)
    g.line([(8, 24), (24, 8)], g.m, 3.5)
    g.ell(12, 12, 20, 20, outline=g.h, w=1)


def lock(g):
    g.arc((9, 3, 23, 19), 180, 360, g.h, 3)
    g.poly([(7, 14), (25, 14), (25, 28), (7, 28)], g.m, g.h)
    g.ell(14, 18, 18, 22, fill=g.s)
    g.line([(16, 21), (16, 25)], g.s, 1.5)


def spear(g):
    g.line([(4, 28), (22, 10)], g.s if g.var != 2 else g.m, 3)
    g.poly([(21, 5), (28, 4), (27, 11), (23, 14), (17, 12)], g.h, g.m)
    g.line([(5, 27), (21, 11)], g.h, 1)


GLYPH = {
    "orb": orb, "bolt": bolt, "slash": slash, "multislash": multislash, "claw": claw, "wings": wings, "burst": burst, "shield": shield,
    "spiral": spiral, "vortex_in": vortex_in, "dash": dash, "storm": storm, "fist": fist, "whip": whip, "boomerang": boomerang, "beam": beam,
    "wall": wall, "pillar": pillar, "meteor": meteor, "flame": flame, "wave": wave, "drop": drop, "heal": heal, "rain": rain, "field": field,
    "chain": chain, "feather": feather, "up": up, "down": down, "blackhole": blackhole, "portal": portal, "eye": eye, "skull": skull,
    "clone": clone, "sun": sun, "revive": revive, "drain": drain, "mist": mist, "star": star, "hex": hexs, "doom": doom, "mana": mana,
    "ban": ban, "lock": lock, "spear": spear,
}

FRAME = {
    "basic": ((150, 150, 165), 1),
    "middle": ((210, 215, 230), 2),
    "upper": ((255, 205, 80), 2),
    "max": ((255, 90, 220), 3),
    "melee": ((215, 140, 70), 2),
    "rapid": ((90, 220, 200), 2),
    "throw": ((255, 160, 60), 2),
}


def plate(el):
    m, s, h = PAL[el]
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()
    for y in range(N):
        for x in range(N):
            dx, dy = x - N / 2 + 0.5, y - N / 2 + 0.5
            r = max(abs(dx), abs(dy)) / (N / 2)
            if r > 0.97:
                continue
            d = math.hypot(dx, dy) / (N / 2)
            k = min(1.0, d)
            col = tuple(int(10 + (s[i] - 10) * (1 - k) * 0.9) for i in range(3))
            px[x, y] = col + (255,)
    # 角を丸める
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rounded_rectangle([P(1), P(1), P(31) - 1, P(31) - 1], radius=P(5), fill=255)
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (N, N), 0), mask))
    return img


def frame(img, rank):
    col, w = FRAME.get(rank, FRAME["basic"])
    d = ImageDraw.Draw(img)
    for i in range(w):
        d.rounded_rectangle([P(1 + i * 0.8), P(1 + i * 0.8), P(31 - i * 0.8) - 1, P(31 - i * 0.8) - 1], radius=P(5 - i * 0.6),
                            outline=col + (255 - i * 40,), width=SS)
    if rank in ("upper", "max"):
        for cx, cy in ((3, 3), (29, 3), (3, 29), (29, 29)):
            d.ellipse([P(cx - 1.3), P(cy - 1.3), P(cx + 1.3), P(cy + 1.3)], fill=col + (255,))
    if rank == "max":
        d.polygon([(P(16), P(0)), (P(18), P(3)), (P(16), P(5)), (P(14), P(3))], fill=(255, 240, 160, 255))
    return img


def icon(el, no, glyph, var, rank):
    base = plate(el)
    g = G(el, var)
    GLYPH[glyph](g)
    layer = glow(g.img, 0.7, 0.8)
    base = Image.alpha_composite(base, layer)
    base = frame(base, rank)
    return base.resize((32, 32), Image.LANCZOS)


def tech_icon(cat):
    img = plate("light" if cat in ("blade", "heavy") else "fire")
    g = G("light", 0)
    if cat == "blade":
        g.line([(7, 25), (24, 8)], (225, 230, 240), 4)
        g.line([(7, 25), (24, 8)], (255, 255, 255), 1.5)
        g.line([(5, 20), (12, 27)], (210, 170, 70), 3)
        g.line([(4, 28), (8, 24)], (130, 85, 50), 3)
    elif cat == "polearm":
        spear(G.__new__(G)) if False else None
        g.m, g.s, g.h = (225, 230, 240), (140, 90, 50), (255, 255, 255)
        spear(g)
    elif cat == "dual":
        for sgn in (-1, 1):
            g.line([(16 - sgn * 9, 26), (16 + sgn * 6, 7)], (225, 230, 240), 3)
            g.line([(16 - sgn * 9, 26), (16 - sgn * 7, 22)], (130, 85, 50), 3)
    elif cat == "swing":
        g.line([(7, 27), (14, 14)], (130, 85, 50), 3)
        g.line([(14, 14), (21, 9)], (170, 175, 190), 2)
        g.ell(19, 3, 29, 13, fill=(110, 115, 130), outline=(225, 230, 240), w=1.5)
    elif cat == "throw":
        for i in range(4):
            a = i * math.pi / 2 + math.pi / 4
            g.poly([(16, 16), (16 + math.cos(a - 0.35) * 12, 16 + math.sin(a - 0.35) * 12), (16 + math.cos(a + 0.35) * 14, 16 + math.sin(a + 0.35) * 14)],
                   (200, 205, 220), (255, 255, 255))
        g.ell(13, 13, 19, 19, fill=(60, 60, 75))
    elif cat == "bow":
        g.arc((3, 3, 27, 29), 270, 90, (190, 130, 70), 3)
        g.line([(15, 4), (15, 28)], (230, 230, 230), 1)
        g.line([(7, 16), (28, 16)], (200, 205, 215), 2)
        g.poly([(28, 16), (23, 13), (23, 19)], (255, 255, 255))
    elif cat == "heavy":
        g.line([(8, 27), (21, 12)], (130, 85, 50), 4)
        g.poly([(14, 4), (29, 8), (27, 17), (19, 15)], (150, 155, 170), (225, 230, 240))
        g.poly([(14, 4), (29, 8), (27, 10), (17, 7)], (225, 230, 240))
    img = Image.alpha_composite(img, glow(g.img, 0.6, 0.6))
    img = frame(img, "middle")
    return img.resize((32, 32), Image.LANCZOS)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def main():
    bal = json.load(open(os.path.join(ROOT, "src", "main", "resources", "magicmod", "balance.json"), encoding="utf-8"))
    lang = {}
    names = {}
    for s in bal["spells"]:
        names[(s["element"], s["no"])] = (s["name"], s["rank"])
    n = 0
    for el, glyphs in MAP.items():
        for i, (gl, var) in enumerate(glyphs):
            no = i + 1
            name, rank = names[(el, no)]
            iid = "icon_%s_%d" % (el, no)
            img = icon(el, no, gl, var, rank)
            p = os.path.join(ASSETS, "textures", "item", "icon", iid + ".png")
            os.makedirs(os.path.dirname(p), exist_ok=True)
            img.save(p)
            write_json(os.path.join(ASSETS, "models", "item", iid + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/icon/%s" % (NS, iid)}})
            write_json(os.path.join(ASSETS, "items", iid + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, iid)}})
            lang["item.%s.%s" % (NS, iid)] = name
            n += 1
    for cat in ("blade", "polearm", "dual", "swing", "throw", "bow", "heavy"):
        iid = "icon_tech_" + cat
        img = tech_icon(cat)
        p = os.path.join(ASSETS, "textures", "item", "icon", iid + ".png")
        img.save(p)
        write_json(os.path.join(ASSETS, "models", "item", iid + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/icon/%s" % (NS, iid)}})
        write_json(os.path.join(ASSETS, "items", iid + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, iid)}})
        lang["item.%s.%s" % (NS, iid)] = "物理わざ"
        n += 1
    for lp in ("ja_jp", "en_us"):
        path = os.path.join(ASSETS, "lang", lp + ".json")
        cur = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
        cur.update(lang)
        write_json(path, cur)
    print("icons:", n)


if __name__ == "__main__":
    main()
