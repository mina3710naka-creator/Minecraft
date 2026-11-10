#!/usr/bin/env python3
"""魔法MOD のテクスチャ・モデル・言語ファイル・レシピ・ルートテーブル・ワールド生成を作る。

使い方(magicmod/ で):
    python3 -I tools/gen_assets.py

Java 側の登録(ModItems / ModBlocks / ModEntities / ModEffects / Gear)と ID を合わせてある。
テクスチャはすべてこのスクリプトで描くドット絵(バニラの画像は使っていない)。
"""
import json
import math
import os
import random
import re
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "magicmod")
DATA = os.path.join(RES, "data", "magicmod")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "magicmod")
NS = "magicmod"

ELEMENTS = [("wind", "風", 0x9BE8B4), ("thunder", "電気", 0xF5E050), ("fire", "炎", 0xFF6A2A), ("water", "水", 0x3E8BFF),
            ("gravity", "重力", 0x8A5CD6), ("dark", "闇", 0x3B2F4F), ("light", "光", 0xFFF6C8), ("curse", "呪い", 0x7A1E5A)]
CRYSTAL_NAMES = ["風の結晶", "雷石", "火の結晶", "水の結晶", "重力の結晶", "闇の結晶", "光の結晶", "呪いの結晶"]
CORE_NAMES = ["暴風の核石", "雷光の核石", "業火の核石", "深海の核石", "重力の核石", "虚無の核石", "聖光の核石", "災厄の核石"]
MINION = [("wind_feather", "風の羽根片", "feather"), ("charged_fang", "帯電の牙", "fang"), ("lava_fang", "溶岩の牙", "fang"),
          ("slime_shard", "粘液の欠片", "blob"), ("void_shard", "虚空の欠片", "shard"), ("night_powder", "夜の鱗粉", "powder"),
          ("holy_shard", "聖なる欠片", "shard"), ("ominous_powder", "不吉な鱗粉", "powder")]
MIDBOSS = [("gale_feather", "疾風の羽根", "feather"), ("charged_crystal", "帯電結晶", "gem"), ("scorching_scale", "灼熱の鱗", "scale"),
           ("abyssal_scale", "深海の鱗", "scale"), ("compressed_stardust", "圧縮された星屑", "star"),
           ("cursed_shadow_shard", "呪われた影の欠片", "shard"), ("holy_gem", "聖なる輝石", "gem"),
           ("greater_curse_crystal", "呪いの結晶(上位)", "gem")]
WANDS = [("wood_wand", "木の杖", 0x8B5A2B, 0xA8E8FF), ("silver_wand", "銀の杖", 0xC8CCD8, 0x7FD0FF), ("magisteel_wand", "魔鋼の杖", 0x4A58C0, 0xC080FF),
         ("dragonbone_wand", "竜骨の杖", 0xEDE6D0, 0xFF5050), ("stardust_wand", "星屑の杖", 0x2A1A50, 0xFFF080)]
WEAPONS = [("long_sword", "ロングソード", "blade"), ("war_hammer", "ウォーハンマー", "blade"), ("naginata", "薙刀", "polearm"),
           ("twin_blades", "双剣", "dual"), ("claws", "クロー", "dual"), ("flail", "フレイル", "swing"), ("kusarigama", "クサリ鎌", "swing"),
           ("nunchaku", "ヌンチャク", "swing"), ("javelin", "投げ槍", "throw"), ("boomerang", "ブーメラン", "throw"),
           ("greatsword", "大剣", "heavy"), ("great_hammer", "戦槌", "heavy"), ("throwing_knife", "投げナイフ", "throw"),
           ("shuriken", "手裏剣", "throw")]
FAMILIARS = [("wolf", "ウルフ", 0x9a9a9a), ("mud_slime", "マッドスライム", 0x6b8e23), ("fairy", "フェアリー", 0x8fe8ff), ("hawk", "ホーク", 0x8b6b4a),
             ("golem", "ゴーレム", 0xb0b0b0), ("spirit_wolves", "スピリットウルフパック", 0x80c0ff), ("elemental", "エレメンタル", 0xff9030),
             ("guardian", "ガーディアン", 0xfff0a0), ("phoenix", "フェニックス", 0xff3020), ("ancient_dragon", "エンシェントドラゴン", 0x6020a0),
             ("pegasus", "ペガサス", 0xfff6c8), ("behemoth", "ベヒーモス", 0x7a4a2a)]
RUNES = [("might", 0xd04020), ("swift", 0x60e0a0), ("iron_wall", 0xa0a0b0), ("regen", 0xff6080), ("clarity", 0xc0f0ff),
         ("fighting_spirit", 0xff9020), ("guard", 0x6080c0), ("haste", 0xffe040), ("critical", 0xff3060), ("conqueror", 0xffd700)]


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def shade(c, f):
    r, g, b = rgb(c) if isinstance(c, int) else c
    if f >= 1:
        return (min(255, int(r + (255 - r) * (f - 1))), min(255, int(g + (255 - g) * (f - 1))), min(255, int(b + (255 - b) * (f - 1))))
    return (int(r * f), int(g * f), int(b * f))


class Canvas:
    def __init__(self, n=16):
        self.n = n
        self.img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        self.px = self.img.load()

    def set(self, x, y, c, a=255):
        if 0 <= x < self.n and 0 <= y < self.n:
            if isinstance(c, int):
                c = rgb(c)
            self.px[x, y] = (c[0], c[1], c[2], a)

    def get(self, x, y):
        return self.px[x, y]

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c, w=1):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            x = round(x0 + (x1 - x0) * i / steps)
            y = round(y0 + (y1 - y0) * i / steps)
            for dx in range(w):
                for dy in range(w):
                    self.set(x + dx, y + dy, c)

    def circle(self, cx, cy, r, c, fill=True):
        for y in range(self.n):
            for x in range(self.n):
                d = math.hypot(x - cx, y - cy)
                if (fill and d <= r) or (not fill and abs(d - r) < 0.6):
                    self.set(x, y, c)

    def outline(self, c=(20, 16, 24)):
        pts = []
        for y in range(self.n):
            for x in range(self.n):
                if self.px[x, y][3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < self.n and 0 <= ny < self.n and self.px[nx, ny][3] > 0 and self.px[nx, ny][:3] != c:
                            pts.append((x, y))
                            break
        for x, y in pts:
            self.set(x, y, c)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.img.save(path)


# ---------------------------------------------------------------------------------------------
# 絵柄

def gem(c, color, big=False):
    pts = [(8, 2), (12, 6), (8, 13), (4, 6)] if not big else [(8, 1), (14, 6), (8, 14), (2, 6)]
    for y in range(16):
        for x in range(16):
            if inside(x + .5, y + .5, pts):
                f = 1.35 if x < 8 and y < 7 else 1.0 if x < 8 else 0.75 if y < 7 else 0.6
                c.set(x, y, shade(color, f))
    c.set(6, 4, (255, 255, 255))
    c.set(7, 3, (255, 255, 255))
    c.outline()


def inside(x, y, pts):
    n = len(pts)
    ok = False
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi):
            ok = not ok
        j = i
    return ok


def draw(style, arg, c, rnd):
    if style == "gem":
        gem(c, arg)
    elif style == "core":
        c.circle(8, 8, 6.5, shade(arg, 0.45))
        gem(c, arg, True)
        c.circle(8, 8, 6.5, shade(arg, 1.5), fill=False)
    elif style == "essence":
        c.circle(8, 8, 5.5, shade(arg, 0.7))
        c.circle(7, 7, 3.5, shade(arg, 1.2))
        c.circle(6, 6, 1.5, shade(arg, 1.6))
        for i in range(6):
            a = i * math.pi / 3
            c.set(round(8 + math.cos(a) * 7), round(8 + math.sin(a) * 7), shade(arg, 1.3))
        c.outline()
    elif style in ("ingot", "raw"):
        if style == "ingot":
            for y in range(6, 12):
                x0 = 2 + (11 - y) // 2
                x1 = 13 - (y - 6) // 3
                for x in range(x0, x1 + 1):
                    c.set(x, y, shade(arg, 1.3 if y == 6 else 1.0 if y < 10 else 0.7))
        else:
            for y in range(4, 13):
                for x in range(3, 13):
                    if math.hypot(x - 8, (y - 8) * 1.2) < 5 + rnd.random():
                        c.set(x, y, shade(arg, 0.6 + rnd.random() * 0.6))
        c.outline()
    elif style == "shard":
        col = arg if arg else 0xA8E8FF
        c.line(5, 13, 10, 3, shade(col, 1.0), 2)
        c.line(8, 13, 12, 6, shade(col, 0.75), 2)
        c.line(4, 12, 6, 8, shade(col, 1.4))
        c.outline()
    elif style == "feather":
        c.line(4, 13, 12, 3, shade(arg, 0.5))
        for i in range(8):
            x, y = 5 + i, 12 - i
            c.line(x, y, x - 2, y - 2, shade(arg, 1.1 + 0.03 * i))
            c.line(x, y, x + 1, y + 1, shade(arg, 0.9))
        c.outline()
    elif style == "fang":
        pts = [(5, 3), (11, 3), (9, 13)]
        for y in range(16):
            for x in range(16):
                if inside(x + .5, y + .5, pts):
                    c.set(x, y, shade(0xF0E8D0, 1.0 if x < 8 else 0.8))
        c.rect(5, 3, 11, 4, shade(arg, 1.0))
        c.outline()
    elif style == "blob":
        c.circle(8, 9, 5, shade(arg, 0.9))
        c.circle(7, 8, 2.5, shade(arg, 1.3))
        c.outline()
    elif style == "powder":
        for i in range(30):
            x, y = int(8 + rnd.gauss(0, 2.6)), int(10 + rnd.gauss(0, 1.8))
            c.set(x, y, shade(arg, 0.7 + rnd.random() * 0.8))
        c.rect(5, 12, 11, 13, (90, 70, 50))
    elif style == "scale":
        pts = [(8, 2), (13, 7), (11, 13), (5, 13), (3, 7)]
        for y in range(16):
            for x in range(16):
                if inside(x + .5, y + .5, pts):
                    c.set(x, y, shade(arg, 1.2 - 0.04 * y))
        for y in (6, 9, 12):
            c.line(5, y, 11, y, shade(arg, 0.6))
        c.outline()
    elif style == "star":
        col = arg if arg else 0xFFF080
        for i in range(5):
            a = -math.pi / 2 + i * 2 * math.pi / 5
            c.line(8, 8, round(8 + math.cos(a) * 6), round(8 + math.sin(a) * 6), shade(col, 1.1), 2)
        c.circle(8, 8, 2.2, (255, 255, 255))
        c.outline((40, 20, 60))
    elif style == "bone":
        c.line(4, 12, 11, 4, (235, 230, 210), 2)
        c.circle(4, 12, 1.6, (245, 240, 225))
        c.circle(12, 4, 1.6, (245, 240, 225))
        c.outline()
    elif style == "runestone":
        c.rect(3, 2, 12, 13, (120, 120, 128))
        c.rect(4, 3, 11, 12, (150, 150, 160))
        c.line(6, 5, 9, 10, (110, 220, 255))
        c.line(9, 5, 6, 10, (110, 220, 255))
        c.outline()
    elif style == "catalyst":
        c.circle(8, 8, 5, (230, 200, 90))
        c.circle(8, 8, 3, (255, 120, 255))
        c.circle(8, 8, 1, (255, 255, 255))
        c.outline()
    elif style == "potion":
        c.rect(7, 2, 8, 4, (200, 200, 210))
        c.circle(8, 10, 4.2, (220, 230, 240))
        c.circle(8, 10, 3.2, shade(arg, 1.0))
        c.set(6, 8, (255, 255, 255))
        c.outline()
    elif style == "orb":
        c.circle(8, 8, 6, (120, 40, 160))
        c.circle(7, 7, 4, (200, 120, 255))
        c.circle(6, 6, 1.5, (255, 255, 255))
        c.outline()
    elif style in ("guide", "book"):
        col = arg if style == "book" else 0x7A4A28
        c.rect(3, 2, 12, 13, shade(col, 0.8))
        c.rect(4, 2, 12, 12, shade(col, 1.0))
        c.rect(4, 13, 12, 13, (235, 230, 215))
        c.line(3, 2, 3, 13, shade(col, 0.5))
        c.circle(8, 7, 2.2, shade(col, 1.6) if style == "book" else (230, 200, 90))
        c.outline()
    elif style == "scroll":
        c.rect(3, 4, 12, 11, (235, 225, 195))
        c.rect(2, 3, 3, 12, (190, 160, 110))
        c.rect(12, 3, 13, 12, (190, 160, 110))
        c.line(5, 6, 10, 6, (200, 80, 220))
        c.line(5, 9, 10, 9, (200, 80, 220))
        c.outline()
    elif style == "wand":
        stick, tip = arg
        c.line(3, 13, 10, 6, shade(stick, 1.0), 1)
        c.line(4, 13, 11, 6, shade(stick, 0.7), 1)
        c.circle(12, 4, 2.4, shade(tip, 1.0))
        c.set(11, 3, (255, 255, 255))
        c.outline()
    elif style == "rune":
        c.rect(3, 3, 12, 12, (90, 90, 100))
        c.rect(4, 4, 11, 11, (60, 60, 72))
        c.circle(8, 8, 3.5, shade(arg, 1.0), fill=False)
        c.line(8, 5, 8, 11, shade(arg, 1.3))
        c.line(5, 8, 11, 8, shade(arg, 1.3))
        c.outline()
    elif style == "contract":
        c.rect(3, 2, 12, 13, (240, 232, 205))
        for y in (4, 6, 8):
            c.line(5, y, 10, y, (150, 140, 120))
        c.circle(10, 11, 2.2, shade(arg, 1.0))
        c.outline()
    elif style == "hat":
        pts = [(9, 1), (13, 11), (3, 11)]
        for y in range(16):
            for x in range(16):
                if inside(x + .5, y + .5, pts):
                    c.set(x, y, shade(arg, 1.0 if x < 8 else 0.8))
        c.rect(1, 11, 14, 13, shade(arg, 0.7))
        c.line(4, 9, 12, 9, (230, 200, 90))
        c.outline()
    elif style == "pendant":
        c.circle(8, 6, 5, (210, 180, 90), fill=False)
        c.rect(0, 0, 15, 4, (0, 0, 0))
        for x in range(16):
            for y in range(5):
                c.px[x, y] = (0, 0, 0, 0)
        c.line(3, 4, 8, 9, (210, 180, 90))
        c.line(13, 4, 8, 9, (210, 180, 90))
        gem_small(c, 8, 11, arg)
        c.outline()
    elif style == "gloves":
        c.rect(4, 4, 11, 12, shade(arg, 1.0))
        c.rect(4, 2, 5, 4, shade(arg, 1.0))
        c.rect(7, 1, 8, 4, shade(arg, 1.0))
        c.rect(10, 2, 11, 4, shade(arg, 1.0))
        c.rect(12, 6, 13, 8, shade(arg, 0.9))
        c.rect(4, 12, 11, 13, shade(arg, 0.6))
        c.outline()
    elif style == "bracelet":
        c.circle(8, 8, 5, (210, 180, 90), fill=False)
        c.circle(8, 8, 4, (240, 210, 110), fill=False)
        gem_small(c, 8, 3, arg)
        c.outline()
    elif style == "weapon":
        weapon(c, arg)
        c.outline()


def gem_small(c, x, y, col):
    c.set(x, y - 1, shade(col, 1.3))
    c.set(x - 1, y, shade(col, 1.1))
    c.set(x, y, shade(col, 1.0))
    c.set(x + 1, y, shade(col, 0.8))
    c.set(x, y + 1, shade(col, 0.6))


STEEL, DARKSTEEL, WOOD, GOLD = (200, 205, 215), (120, 125, 140), (120, 80, 45), (220, 180, 70)


def weapon(c, wid):
    if wid in ("long_sword", "greatsword"):
        w = 2 if wid == "greatsword" else 1
        c.line(4, 11, 13, 2, STEEL, w)
        c.line(5, 11, 13, 3, DARKSTEEL)
        c.line(2, 9, 6, 13, GOLD)
        c.line(1, 14, 4, 11, WOOD, 2)
    elif wid in ("war_hammer", "great_hammer"):
        c.line(2, 14, 10, 6, WOOD, 1)
        s = 3 if wid == "great_hammer" else 2
        c.rect(9 - s, 2, 13, 4 + s, DARKSTEEL)
        c.rect(10 - s, 3, 12, 3 + s, STEEL)
    elif wid in ("naginata", "javelin"):
        c.line(1, 14, 11, 4, WOOD)
        c.line(11, 4, 14, 1, STEEL, 1)
        if wid == "naginata":
            c.line(10, 3, 14, 1, STEEL)
            c.line(12, 5, 14, 2, DARKSTEEL)
    elif wid == "twin_blades":
        c.line(3, 12, 10, 3, STEEL)
        c.line(6, 13, 13, 4, STEEL)
        c.line(2, 13, 3, 12, WOOD, 2)
        c.line(5, 14, 6, 13, WOOD, 2)
    elif wid == "claws":
        c.rect(3, 9, 9, 13, (90, 70, 60))
        for i in range(3):
            c.line(5 + 2 * i, 9, 9 + 2 * i, 2, STEEL)
    elif wid == "flail":
        c.line(2, 14, 7, 9, WOOD, 1)
        c.line(7, 9, 9, 6, (150, 150, 160))
        c.circle(11, 4, 2.6, DARKSTEEL)
        for a in range(4):
            c.set(11 + [3, -3, 0, 0][a], 4 + [0, 0, 3, -3][a], STEEL)
    elif wid == "kusarigama":
        c.line(2, 14, 6, 10, WOOD, 1)
        c.line(6, 10, 10, 2, STEEL)
        c.line(10, 2, 14, 4, STEEL)
        c.line(6, 10, 13, 12, (150, 150, 160))
    elif wid == "nunchaku":
        c.line(2, 13, 6, 9, WOOD, 2)
        c.line(10, 3, 13, 7, WOOD, 2)
        c.line(6, 9, 10, 5, (150, 150, 160))
    elif wid == "boomerang":
        c.line(3, 12, 8, 3, WOOD, 2)
        c.line(8, 3, 13, 12, WOOD, 2)
    elif wid == "throwing_knife":
        c.line(4, 12, 11, 5, STEEL)
        c.line(5, 12, 11, 6, DARKSTEEL)
        c.line(2, 14, 4, 12, (90, 60, 40), 1)
    elif wid == "shuriken":
        for a in range(4):
            ang = a * math.pi / 2 + math.pi / 4
            c.line(8, 8, round(8 + math.cos(ang) * 6), round(8 + math.sin(ang) * 6), STEEL, 1)
        c.circle(8, 8, 2, DARKSTEEL)
        c.set(8, 8, (40, 40, 40))


def stone_tex(base, rnd, n=16):
    c = Canvas(n)
    for y in range(n):
        for x in range(n):
            f = 0.85 + rnd.random() * 0.25
            c.set(x, y, shade(base, f))
    return c


def ore_tex(base, col, rnd):
    c = stone_tex(base, rnd)
    for _ in range(4):
        cx, cy = rnd.randint(2, 13), rnd.randint(2, 13)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (1, 1)):
            if rnd.random() < 0.85:
                c.set(cx + dx, cy + dy, shade(col, 0.8 + rnd.random() * 0.5))
    return c


STATION = {
    "magic_table": (0x3A2A5A, 0x8A5CD6, 0xC8A0FF),
    "forge_table": (0x4A4A52, 0x8A8A96, 0xFF8030),
    "summoning_altar": (0x4A3A30, 0xB0A090, 0x60E0C0),
    "arcane_altar": (0x303848, 0x7080A0, 0x80D0FF),
    "archetype_altar": (0x504030, 0xD0C090, 0xFFD040),
    "seal_altar": (0x201820, 0x503040, 0xFF4060),
    "guild_board": (0x4A3420, 0x9A7040, 0xFFD060),
    "transform_altar": (0x1C1028, 0x5A2A70, 0xFF40A0),
}


def station_tex(name, face, rnd):
    dark, mid, glow = STATION[name]
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, shade(mid if face != "bottom" else dark, 0.85 + rnd.random() * 0.2))
    if face == "top":
        c.circle(7.5, 7.5, 5.5, glow, fill=False)
        c.circle(7.5, 7.5, 3, shade(glow, 0.8), fill=False)
        c.line(4, 4, 11, 11, shade(glow, 1.1))
        c.line(11, 4, 4, 11, shade(glow, 1.1))
        for x in range(16):
            c.set(x, 0, shade(dark, 1.0))
            c.set(x, 15, shade(dark, 1.0))
            c.set(0, x, shade(dark, 1.0))
            c.set(15, x, shade(dark, 1.0))
    elif face == "side":
        c.rect(0, 0, 15, 2, shade(dark, 1.1))
        c.rect(0, 13, 15, 15, shade(dark, 1.1))
        c.rect(5, 5, 10, 10, shade(dark, 0.9))
        c.rect(6, 6, 9, 9, glow)
        c.set(7, 7, (255, 255, 255))
    return c


EFFECT_COLORS = {}


def effect_icon(color, rnd):
    c = Canvas(18)
    c.circle(8.5, 8.5, 7, shade(color, 0.55))
    c.circle(8.5, 8.5, 5.5, shade(color, 1.0))
    c.circle(7, 7, 2, shade(color, 1.5))
    return c


# ---------------------------------------------------------------------------------------------

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def main():
    rnd = random.Random(1234)
    items = []  # (id, ja, style, arg, handheld)
    for i, s in enumerate(["crystal_shard", "raw_silver", "silver_ingot", "magisteel_ingot", "dragon_bone", "stardust_shard", "rune_stone",
                           "synth_catalyst", "purifying_water", "rebirth_orb", "magic_guide", "synth_scroll"]):
        pass
    items += [("crystal_shard", "水晶欠片", "shard", 0xA8E8FF, False), ("raw_silver", "銀の原石", "raw", 0xC8CCD8, False),
              ("silver_ingot", "銀インゴット", "ingot", 0xD8DCE8, False), ("magisteel_ingot", "魔鋼インゴット", "ingot", 0x5A6AD8, False),
              ("dragon_bone", "ドラゴンの骨", "bone", None, False), ("stardust_shard", "星屑の欠片", "star", 0xFFF080, False),
              ("rune_stone", "刻印石", "runestone", None, False), ("synth_catalyst", "合成触媒", "catalyst", None, False),
              ("purifying_water", "解呪の聖水", "potion", 0xFFF6C8, False), ("rebirth_orb", "転身の宝珠", "orb", None, False),
              ("magic_guide", "魔法の手引き", "guide", None, False), ("life_tome", "生活の魔導書", "book", 0x60D060, False), ("synth_scroll", "合成魔法の巻物", "scroll", None, False),
              ("omnis_heart", "魔王の心臓", "core", 0xFF40A0, False)]
    for i, (el, ja, col) in enumerate(ELEMENTS):
        items.append((el + "_crystal", CRYSTAL_NAMES[i], "gem", col, False))
        items.append((el + "_essence", ja + "のエッセンス", "essence", col, False))
        items.append((el + "_ingot", ja + "の属性インゴット", "ingot", col, False))
        items.append((MINION[i][0], MINION[i][1], MINION[i][2], col, False))
        items.append((MIDBOSS[i][0], MIDBOSS[i][1], MIDBOSS[i][2], col, False))
        items.append((el + "_core", CORE_NAMES[i], "core", col, False))
        items.append((el + "_book", ja + "の魔法書", "book", col, False))
    for wid, ja, stick, tip in WANDS:
        items.append((wid, ja, "wand", (stick, tip), True))
    bal = json.load(open(os.path.join(RES, "magicmod", "balance.json"), encoding="utf-8"))
    rune_names = {r["no"]: r["name"] for r in bal["runes"]}
    fam_names = {f["no"]: f["name"] for f in bal["familiars"]}
    for i, (rid, col) in enumerate(RUNES):
        items.append(("rune_" + rid, rune_names[i + 1], "rune", col, False))
    for i, (fid, ja, col) in enumerate(FAMILIARS):
        items.append(("contract_" + fid, "使い魔契約書(" + fam_names.get(i + 1, ja) + ")", "contract", col, False))
    gear_src = open(os.path.join(JAVA, "Gear.java"), encoding="utf-8").read()
    for m in re.finditer(r'[A-Z_]+\("([a-z_]+)", "([^"]+)", Slot\.([A-Z]+)', gear_src):
        gid, ja, slot = m.group(1), m.group(2), m.group(3).lower()
        col = 0x6040A0
        for el, eja, ecol in ELEMENTS:
            if gid.startswith(el + "_"):
                col = ecol
        col = {"clarity_pendant": 0xC0F0FF, "life_pendant": 0xFF6080, "martial_amulet": 0xD04020, "arcane_amulet": 0x8A5CD6,
               "apprentice_hat": 0x4060A0, "sage_hat": 0x6A2AA0, "warding_hat": 0x406050, "fighter_gloves": 0x8B5A2B,
               "swift_gloves": 0xE0C040, "iron_gauntlet": 0xA0A0B0, "casting_gloves": 0x7040C0, "mana_bracelet": 0x3E8BFF,
               "contract_bracelet": 0x60E0C0, "expansion_bracelet": 0xFFD700}.get(gid, col)
        items.append((gid, ja, slot, col, False))
    for wid, ja, cat in WEAPONS:
        items.append((wid, ja, "weapon", wid, True))

    # 言語
    lang = {"itemGroup.magicmod": "魔法MOD"}
    for iid, ja, style, arg, hand in items:
        lang["item.%s.%s" % (NS, iid)] = ja
        c = Canvas()
        draw(style, arg, c, rnd)
        c.save(os.path.join(ASSETS, "textures", "item", iid + ".png"))
        write_json(os.path.join(ASSETS, "models", "item", iid + ".json"),
                   {"parent": "minecraft:item/handheld" if hand else "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, iid)}})
        write_json(os.path.join(ASSETS, "items", iid + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, iid)}})

    blocks_src = open(os.path.join(JAVA, "ModBlocks.java"), encoding="utf-8").read()
    blocks = re.findall(r'block\("([a-z_]+)", "([^"]+)", "([^"]+)"', blocks_src)
    blocks = [b for b in blocks if b[0] != "id"]
    for bid, ja, style in blocks:
        lang["block.%s.%s" % (NS, bid)] = ja
        if style.startswith("station:"):
            name = style.split(":")[1]
            for face in ("top", "side", "bottom"):
                station_tex(name, face, rnd).save(os.path.join(ASSETS, "textures", "block", "%s_%s.png" % (bid, face)))
            model = {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": "%s:block/%s_top" % (NS, bid),
                     "side": "%s:block/%s_side" % (NS, bid), "bottom": "%s:block/%s_bottom" % (NS, bid)}}
        else:
            _, base, col = style.split(":")
            basecol = 0x7A7A7A if base == "stone" else 0x4A4A50
            ore_tex(basecol, int(col, 16), rnd).save(os.path.join(ASSETS, "textures", "block", bid + ".png"))
            model = {"parent": "minecraft:block/cube_all", "textures": {"all": "%s:block/%s" % (NS, bid)}}
        write_json(os.path.join(ASSETS, "models", "block", bid + ".json"), model)
        write_json(os.path.join(ASSETS, "blockstates", bid + ".json"), {"variants": {"": {"model": "%s:block/%s" % (NS, bid)}}})
        write_json(os.path.join(ASSETS, "items", bid + ".json"), {"model": {"type": "minecraft:model", "model": "%s:block/%s" % (NS, bid)}})

    ent_src = open(os.path.join(JAVA, "ModEntities.java"), encoding="utf-8").read()
    for eid, ja in re.findall(r'mob\("([a-z_]+)", "([^"]+)"', ent_src):
        lang["entity.%s.%s" % (NS, eid)] = ja
    lang["entity.%s.magic_bolt" % NS] = "魔法弾"
    eff_src = open(os.path.join(JAVA, "ModEffects.java"), encoding="utf-8").read()
    for m in re.finditer(r'reg\("([a-z_]+)", "([^"]+)", new [A-Za-z]+\((?:MobEffectCategory\.[A-Z]+, )?(0x[0-9A-Fa-f]+)?', eff_src):
        eid, ja, col = m.group(1), m.group(2), m.group(3)
        lang["effect.%s.%s" % (NS, eid)] = ja
        color = int(col, 16) if col else 0x8080FF
        effect_icon(color, rnd).save(os.path.join(ASSETS, "textures", "mob_effect", eid + ".png"))
    keys = {"wheel": "魔法ホイール(押している間)", "dodge": "回避ステップ", "cast": "魔法を撃つ", "prev": "前の魔法", "next": "次の魔法", "tech": "物理わざを使う", "tech_next": "わざ切替",
            "familiar": "使い魔を召喚/送還(押しながらホイールで選択)", "status": "ステータス画面", "skill": "使い魔のスキル", "transform": "変身(押しながらホイールで切替)"}
    for k, v in keys.items():
        lang["key.%s.%s" % (NS, k)] = v
    lang["key.category.%s.keys" % NS] = "魔法MOD"
    lang["death.attack.%s.spell" % NS] = "%1$s は魔法で倒れた"
    lang["death.attack.%s.spell.player" % NS] = "%1$s は %2$s の魔法で倒れた"
    lang["death.attack.%s.spell.item" % NS] = "%1$s は %2$s の %3$s で倒れた"
    write_json(os.path.join(ASSETS, "lang", "ja_jp.json"), lang)
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), lang)

    gen_data(items, blocks)
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen_fx
    gen_fx.main()
    import gen_icons
    gen_icons.main()
    print("items:", len(items), "blocks:", len(blocks), "lang:", len(lang))


def I(x):
    return x if ":" in x else NS + ":" + x


def shaped(name, pattern, key, result, count=1):
    used = set("".join(pattern))
    key = {k: v for k, v in key.items() if k in used}
    write_json(os.path.join(DATA, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
               "key": {k: I(v) if not v.startswith("#") else v for k, v in key.items()}, "result": {"id": I(result), "count": count}})


def shapeless(name, ingredients, result, count=1):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
               "ingredients": [I(i) if not i.startswith("#") else i for i in ingredients], "result": {"id": I(result), "count": count}})


def smelt(name, ing, result, kind="smelting", time=200):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {"type": "minecraft:" + kind, "cookingtime": time, "experience": 0.7,
               "ingredient": I(ing), "result": {"id": I(result)}})


def els0():
    return ["wind", "thunder", "fire", "water", "gravity", "dark", "light", "curse"]


def gen_data(items, blocks):
    # ダメージの種類
    write_json(os.path.join(DATA, "damage_type", "spell.json"), {"exhaustion": 0.1, "message_id": "magicmod.spell",
               "scaling": "when_caused_by_living_non_player"})
    # ブロックのドロップ
    for bid, ja, style in blocks:
        if bid.endswith("crystal_ore"):
            drop = {"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": I(bid)},
                {"type": "minecraft:item", "name": I("crystal_shard"), "functions": [
                    {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}},
                    {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                    {"function": "minecraft:explosion_decay"}]}]}
        elif bid.endswith("silver_ore"):
            drop = {"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": I(bid)},
                {"type": "minecraft:item", "name": I("raw_silver"), "functions": [
                    {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                    {"function": "minecraft:explosion_decay"}]}]}
        else:
            drop = {"type": "minecraft:item", "name": I(bid)}
        write_json(os.path.join(DATA, "loot_table", "blocks", bid + ".json"), {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [drop], "conditions": [{"condition": "minecraft:survives_explosion"}] if not bid.endswith("ore") else []}],
            "random_sequence": "%s:blocks/%s" % (NS, bid)})
    write_json(os.path.join(RES, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"),
               {"replace": False, "values": [I(b[0]) for b in blocks]})
    write_json(os.path.join(RES, "data", "minecraft", "tags", "block", "needs_stone_tool.json"),
               {"replace": False, "values": [I(b[0]) for b in blocks if not b[0].endswith("ore") or "silver" in b[0]]})
    write_json(os.path.join(RES, "data", "minecraft", "tags", "block", "needs_iron_tool.json"),
               {"replace": False, "values": [I("crystal_ore"), I("deepslate_crystal_ore")]})
    write_json(os.path.join(RES, "data", "minecraft", "tags", "entity_type", "undead.json"),
               {"replace": False, "values": [I("cursed_puppet"), I("abyss_executor"), I("tideling"), I("melusine")]})
    # 属性の遺跡(地形生成・宝箱の中身)
    minion = {"wind": "wind_feather", "thunder": "charged_fang", "fire": "lava_fang", "water": "slime_shard", "gravity": "void_shard",
              "dark": "night_powder", "light": "holy_shard", "curse": "ominous_powder"}
    write_json(os.path.join(DATA, "worldgen", "feature", "guild_hall.json"), {"type": "magicmod:guild_hall", "style": "wood"})
    write_json(os.path.join(DATA, "worldgen", "placed_feature", "guild_hall.json"), {"feature": I("guild_hall"), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 40}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
    mid = {"wind": "gale_feather", "thunder": "charged_crystal", "fire": "scorching_scale", "water": "abyssal_scale",
           "gravity": "compressed_stardust", "dark": "cursed_shadow_shard", "light": "holy_gem", "curse": "greater_curse_crystal"}
    for el in els0():
        write_json(os.path.join(DATA, "worldgen", "feature", "ruin_" + el + ".json"), {"type": "magicmod:ruin", "element": el})
        write_json(os.path.join(DATA, "worldgen", "placed_feature", "ruin_" + el + ".json"), {"feature": I("ruin_" + el), "placement": [
            {"type": "minecraft:rarity_filter", "chance": 30}, {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
        def entry(item, lo, hi, w=1):
            return {"type": "minecraft:item", "name": I(item), "weight": w, "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}
        write_json(os.path.join(DATA, "loot_table", "chests", "ruin_" + el + ".json"), {"type": "minecraft:chest", "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
                entry(el + "_crystal", 2, 5, 6), entry("crystal_shard", 2, 6, 4), entry("rune_stone", 1, 3, 3), entry("minecraft:lapis_lazuli", 3, 8, 4),
                entry("silver_ingot", 1, 3, 2), entry(minion[el], 1, 3, 3), entry(el + "_essence", 1, 1, 1)]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.4}], "entries": [entry(mid[el], 1, 1, 3), entry("synth_catalyst", 1, 2, 2),
                entry("purifying_water", 1, 1, 2)]},
            {"rolls": 1, "conditions": [{"condition": "minecraft:random_chance", "chance": 0.06}], "entries": [entry(el + "_ingot", 1, 1, 1), entry("rebirth_orb", 1, 1, 1)]}]})
    # ワールド生成(鉱石)
    def ore_feature(name, stone, deep, size):
        write_json(os.path.join(DATA, "worldgen", "feature", name + ".json"), {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0,
                   "size": size, "targets": [
                       {"state": I(stone), "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
                       {"state": I(deep), "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]})

    def placed(name, feature, count, lo, hi, kind="trapezoid"):
        write_json(os.path.join(DATA, "worldgen", "placed_feature", name + ".json"), {"feature": I(feature), "placement": [
            {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:" + kind, "min_inclusive": {"absolute": lo},
                                                          "max_inclusive": {"absolute": hi}}},
            {"type": "minecraft:biome"}]})
    ore_feature("ore_crystal", "crystal_ore", "deepslate_crystal_ore", 6)
    ore_feature("ore_silver", "silver_ore", "deepslate_silver_ore", 8)
    placed("ore_crystal", "ore_crystal", 7, -16, 80)
    placed("ore_crystal_deep", "ore_crystal", 4, -64, 0, "uniform")
    placed("ore_silver", "ore_silver", 8, -40, 48)

    # レシピ
    els = [e[0] for e in ELEMENTS]
    extra = {"wind": ["minecraft:feather", "minecraft:string"], "thunder": ["minecraft:copper_ingot", "minecraft:redstone"],
             "fire": ["minecraft:coal", "minecraft:flint"], "water": ["minecraft:kelp", "minecraft:clay_ball"],
             "gravity": ["minecraft:iron_ingot", "minecraft:obsidian"], "dark": ["minecraft:ink_sac", "minecraft:bone"],
             "light": ["minecraft:glowstone_dust", "minecraft:gold_nugget"], "curse": ["minecraft:spider_eye", "minecraft:rotten_flesh"]}
    for el in els:
        shapeless(el + "_crystal", ["crystal_shard", "crystal_shard"] + extra[el], el + "_crystal", 2)
        shapeless(el + "_essence", [el + "_crystal"] * 4 + ["minecraft:glowstone_dust", "minecraft:amethyst_shard"], el + "_essence")
        shapeless(el + "_ingot", ["minecraft:iron_ingot"] + [el + "_crystal"] * 4, el + "_ingot")
        shapeless(el + "_book", ["minecraft:book", el + "_crystal", el + "_crystal", "minecraft:lapis_lazuli"], el + "_book")
        shaped(el + "_pendant", [" S ", "S S", " C "], {"S": "minecraft:string", "C": el + "_essence"}, el + "_pendant")
    smelt("silver_ingot_from_smelting", "raw_silver", "silver_ingot")
    smelt("silver_ingot_from_blasting", "raw_silver", "silver_ingot", "blasting", 100)
    shapeless("magisteel_ingot", ["minecraft:iron_ingot", "crystal_shard", "crystal_shard", "crystal_shard", "crystal_shard",
                                  "minecraft:lapis_lazuli"], "magisteel_ingot")
    shapeless("dragon_bone", ["minecraft:bone_block", "minecraft:dragon_breath", "minecraft:netherite_scrap"], "dragon_bone")
    shapeless("stardust_shard", ["compressed_stardust", "compressed_stardust", "compressed_stardust", "compressed_stardust",
                                 "minecraft:amethyst_shard", "minecraft:diamond"], "stardust_shard")
    shapeless("rune_stone", ["minecraft:smooth_stone", "crystal_shard", "crystal_shard", "crystal_shard", "crystal_shard"], "rune_stone", 2)
    shapeless("synth_catalyst", ["crystal_shard", "crystal_shard", "minecraft:glowstone_dust", "minecraft:redstone",
                                 "minecraft:lapis_lazuli"], "synth_catalyst", 2)
    shapeless("purifying_water", ["minecraft:glass_bottle", "light_crystal", "minecraft:sugar"], "purifying_water")
    shapeless("rebirth_orb", ["minecraft:diamond"] + [e + "_crystal" for e in els], "rebirth_orb")
    shapeless("magic_guide", ["minecraft:book", "crystal_shard"], "magic_guide")
    shaped("wood_wand", ["  C", " S ", "S  "], {"C": "crystal_shard", "S": "minecraft:stick"}, "wood_wand")
    shaped("silver_wand", ["  I", " S ", "I  "], {"I": "silver_ingot", "S": "minecraft:stick"}, "silver_wand")
    shaped("magisteel_wand", [" LM", " MS", "S  "], {"M": "magisteel_ingot", "L": "minecraft:lapis_block", "S": "minecraft:stick"},
           "magisteel_wand")
    shaped("dragonbone_wand", [" NB", " B ", "B  "], {"B": "dragon_bone", "N": "minecraft:netherite_ingot"}, "dragonbone_wand")
    shaped("stardust_wand", [" SD", " M ", "M  "], {"S": "stardust_shard", "D": "minecraft:diamond", "M": "magisteel_ingot"}, "stardust_wand")
    # ブロック
    shaped("magic_table", [" B ", "CTC", "OOO"], {"B": "minecraft:book", "C": "crystal_shard", "T": "minecraft:crafting_table",
                                                  "O": "minecraft:obsidian"}, "magic_table")
    shaped("guild_board", ["PPP", "BCB", "LLL"], {"P": "minecraft:paper", "B": "minecraft:book", "C": "crystal_shard", "L": "minecraft:oak_planks"}, "guild_board")
    shapeless("life_tome", ["minecraft:book", "crystal_shard", "minecraft:wheat_seeds", "minecraft:iron_pickaxe"], "life_tome")
    shaped("transform_altar", ["CNC", "OEO", "OOO"], {"C": "crystal_shard", "N": "minecraft:nether_star", "O": "minecraft:obsidian", "E": "minecraft:emerald_block"}, "transform_altar")
    shaped("forge_table", ["II ", "SS ", "PP "], {"I": "minecraft:iron_ingot", "S": "minecraft:smithing_table", "P": "minecraft:cobblestone"},
           "forge_table")
    shaped("summoning_altar", ["CBC", "SGS", "SSS"], {"C": "crystal_shard", "B": "minecraft:bone_block", "G": "minecraft:gold_ingot",
                                                      "S": "minecraft:stone_bricks"}, "summoning_altar")
    shaped("arcane_altar", ["RRR", "RLR", "SSS"], {"R": "rune_stone", "L": "minecraft:lapis_block", "S": "minecraft:stone_bricks"},
           "arcane_altar")
    shaped("archetype_altar", ["CDC", "OSO", "OOO"], {"C": "crystal_shard", "D": "minecraft:diamond", "O": "minecraft:obsidian",
                                                      "S": "minecraft:smooth_stone"}, "archetype_altar")
    shaped("seal_altar", ["CGC", "OEO", "OOO"], {"C": "crystal_shard", "G": "minecraft:gold_block", "O": "minecraft:crying_obsidian",
                                                 "E": "minecraft:ender_eye"}, "seal_altar")
    # 装備
    shaped("apprentice_hat", [" L ", "LCL", "L L"], {"L": "minecraft:leather", "C": "crystal_shard"}, "apprentice_hat")
    shapeless("sage_hat", ["apprentice_hat", "light_essence", "minecraft:gold_ingot"], "sage_hat")
    shapeless("warding_hat", ["apprentice_hat", "gravity_essence", "minecraft:iron_ingot"], "warding_hat")
    shaped("clarity_pendant", [" S ", "S S", " C "], {"S": "minecraft:string", "C": "minecraft:amethyst_shard"}, "clarity_pendant")
    shaped("life_pendant", [" S ", "S S", " C "], {"S": "minecraft:string", "C": "minecraft:golden_apple"}, "life_pendant")
    shaped("fighter_gloves", ["L L", "LIL"], {"L": "minecraft:leather", "I": "minecraft:iron_ingot"}, "fighter_gloves")
    shaped("swift_gloves", ["L L", "LTL"], {"L": "minecraft:leather", "T": "thunder_essence"}, "swift_gloves")
    shaped("mana_bracelet", [" G ", "GLG", " G "], {"G": "minecraft:gold_ingot", "L": "minecraft:lapis_block"}, "mana_bracelet")
    shaped("contract_bracelet", [" G ", "GBG", " G "], {"G": "minecraft:gold_ingot", "B": "minecraft:bone_block"}, "contract_bracelet")
    shaped("expansion_bracelet", ["MGM", "GDG", "MGM"], {"G": "minecraft:gold_ingot", "M": "magisteel_ingot", "D": "minecraft:diamond"},
           "expansion_bracelet")
    shaped("martial_amulet", [" S ", "S S", " X "], {"S": "minecraft:iron_chain", "X": "stardust_shard"}, "martial_amulet")
    shaped("arcane_amulet", [" S ", "S S", " X "], {"S": "minecraft:string", "X": "stardust_shard"}, "arcane_amulet")
    # 武器
    W = {"I": "minecraft:iron_ingot", "S": "minecraft:stick", "B": "minecraft:iron_block", "C": "minecraft:iron_chain", "P": "#minecraft:planks"}
    shaped("long_sword", ["  I", " I ", "S  "], W, "long_sword")
    shaped("war_hammer", ["III", " S ", " S "], W, "war_hammer")
    shaped("naginata", ["  I", " S ", "S  "], {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}, "naginata")
    shaped("twin_blades", ["I I", "S S"], W, "twin_blades")
    shaped("claws", ["I I", "III"], W, "claws")
    shaped("flail", ["  B", " C ", "S  "], W, "flail")
    shaped("kusarigama", ["II ", " SC", "  C"], W, "kusarigama")
    shaped("nunchaku", ["S S", " C "], W, "nunchaku")
    shaped("javelin", ["  I", " S ", "S  "], {"I": "minecraft:flint", "S": "minecraft:stick"}, "javelin", 2)
    shaped("boomerang", ["PP ", "  P", "  P"], W, "boomerang")
    shaped("greatsword", [" II", "III", "SI "], W, "greatsword")
    shaped("great_hammer", ["BBB", " S ", " S "], W, "great_hammer")
    shaped("throwing_knife", [" I", "S "], W, "throwing_knife", 8)
    shaped("shuriken", [" I ", "I I", " I "], W, "shuriken", 12)


if __name__ == "__main__":
    main()
