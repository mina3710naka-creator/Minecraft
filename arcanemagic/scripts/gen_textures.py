#!/usr/bin/env python3
"""PILなしで16x16(と128x128)のシンプルなプレースホルダーPNGテクスチャを生成するスクリプト。
zlibだけを使って手書きでPNGバイナリを組み立てている。
出来上がる絵はかなり簡素なので、後で好きなドット絵に差し替えるのを推奨(README参照)。
"""
import os
import struct
import zlib

OUT_ITEM = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "arcanemagic", "textures", "item")
OUT_BLOCK = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "arcanemagic", "textures", "block")
OUT_ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "arcanemagic")


def write_png(path, width, height, pixels):
    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)

    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type 0 (none)
        for x in range(width):
            raw.extend(pixels[y][x])

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)

    with open(path, "wb") as f:
        f.write(sig)
        f.write(chunk(b"IHDR", ihdr))
        f.write(chunk(b"IDAT", idat))
        f.write(chunk(b"IEND", b""))


def canvas(w, h, bg=(0, 0, 0, 0)):
    return [[bg for _ in range(w)] for _ in range(h)]


def px(cv, x, y, color):
    if 0 <= y < len(cv) and 0 <= x < len(cv[0]):
        cv[y][x] = color


def rect(cv, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(cv, x, y, color)


def line_diag(cv, x0, y0, x1, y1, color, width=1):
    steps = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(steps + 1):
        t = i / steps if steps else 0
        x = round(x0 + (x1 - x0) * t)
        y = round(y0 + (y1 - y0) * t)
        for w in range(width):
            px(cv, x + w, y, color)


WOOD = (121, 85, 58, 255)
WOOD_LIGHT = (158, 118, 79, 255)
WOOD_DARK = (84, 58, 38, 255)
GEM_PURPLE = (176, 96, 235, 255)
GEM_PURPLE_DARK = (110, 46, 168, 255)
PAPER = (222, 200, 150, 255)
PAPER_DARK = (188, 163, 116, 255)
PAPER_SHADOW = (150, 128, 90, 255)
BOOK_COVER = (95, 42, 130, 255)
BOOK_COVER_DARK = (63, 24, 92, 255)
BOOK_PAGES = (232, 217, 181, 255)
BOOK_GEM = (230, 200, 80, 255)
CRYSTAL_LIGHT = (206, 148, 255, 255)
CRYSTAL = (156, 68, 226, 255)
CRYSTAL_DARK = (100, 34, 156, 255)
STONE = (112, 102, 132, 255)
STONE_DARK = (70, 62, 92, 255)
STONE_LIGHT = (150, 140, 170, 255)
GOLD = (230, 200, 80, 255)

SPELL_COLORS = {
    "fireball": ((235, 120, 48, 255), (160, 60, 20, 255)),
    "ice_shard": ((130, 210, 235, 255), (60, 130, 170, 255)),
    "heal": ((130, 226, 150, 255), (50, 150, 80, 255)),
    "lightning": ((235, 222, 96, 255), (170, 150, 30, 255)),
    "blink": ((150, 130, 235, 255), (90, 60, 170, 255)),
    "push": ((235, 130, 186, 255), (170, 60, 120, 255)),
}


def gen_wand():
    cv = canvas(16, 16)
    line_diag(cv, 2, 13, 11, 4, WOOD, width=1)
    line_diag(cv, 3, 13, 12, 4, WOOD_DARK, width=1)
    line_diag(cv, 2, 12, 10, 4, WOOD_LIGHT, width=1)
    # 柄の先の宝石
    rect(cv, 10, 2, 12, 4, GEM_PURPLE_DARK)
    px(cv, 11, 3, GEM_PURPLE)
    px(cv, 11, 2, CRYSTAL_LIGHT)
    write_png(os.path.join(OUT_ITEM, "wand.png"), 16, 16, cv)


def gen_spellbook():
    cv = canvas(16, 16)
    rect(cv, 2, 2, 13, 13, BOOK_COVER_DARK)
    rect(cv, 2, 2, 12, 12, BOOK_COVER)
    rect(cv, 4, 4, 11, 11, BOOK_PAGES)
    for y in range(5, 11):
        px(cv, 7, y, PAPER_SHADOW)
    rect(cv, 6, 6, 8, 8, BOOK_GEM)
    px(cv, 7, 7, (255, 240, 200, 255))
    write_png(os.path.join(OUT_ITEM, "spellbook.png"), 16, 16, cv)


def gen_crystal():
    cv = canvas(16, 16)
    pts_outer = [(8, 2), (12, 6), (10, 14), (6, 14), (4, 6)]
    rect(cv, 6, 4, 10, 12, CRYSTAL_DARK)
    rect(cv, 6, 5, 9, 11, CRYSTAL)
    rect(cv, 7, 5, 8, 9, CRYSTAL_LIGHT)
    px(cv, 8, 3, CRYSTAL_LIGHT)
    px(cv, 7, 3, CRYSTAL)
    write_png(os.path.join(OUT_ITEM, "arcane_crystal.png"), 16, 16, cv)


def gen_scroll(name, main_color, dark_color):
    cv = canvas(16, 16)
    rect(cv, 3, 5, 12, 10, PAPER_DARK)
    rect(cv, 3, 4, 12, 4, dark_color)
    rect(cv, 3, 11, 12, 11, dark_color)
    rect(cv, 4, 6, 11, 9, PAPER)
    rect(cv, 6, 7, 9, 8, main_color)
    px(cv, 7, 7, (255, 255, 255, 180))
    write_png(os.path.join(OUT_ITEM, f"{name}_scroll.png"), 16, 16, cv)


def gen_altar_top():
    cv = canvas(16, 16)
    rect(cv, 0, 0, 15, 15, STONE_DARK)
    rect(cv, 1, 1, 14, 14, STONE)
    rect(cv, 3, 3, 12, 12, STONE_LIGHT)
    rect(cv, 6, 6, 9, 9, CRYSTAL_DARK)
    rect(cv, 7, 7, 8, 8, CRYSTAL_LIGHT)
    write_png(os.path.join(OUT_BLOCK, "arcane_altar_top.png"), 16, 16, cv)


def gen_altar_side():
    cv = canvas(16, 16)
    rect(cv, 0, 0, 15, 15, STONE_DARK)
    rect(cv, 0, 0, 15, 3, STONE_LIGHT)
    rect(cv, 1, 4, 14, 14, STONE)
    for x in (2, 6, 10, 13):
        rect(cv, x, 5, x, 13, STONE_DARK)
    rect(cv, 6, 6, 9, 9, GEM_PURPLE_DARK)
    rect(cv, 7, 7, 8, 8, GEM_PURPLE)
    write_png(os.path.join(OUT_BLOCK, "arcane_altar_side.png"), 16, 16, cv)


def gen_altar_bottom():
    cv = canvas(16, 16)
    rect(cv, 0, 0, 15, 15, STONE_DARK)
    rect(cv, 1, 1, 14, 14, STONE)
    write_png(os.path.join(OUT_BLOCK, "arcane_altar_bottom.png"), 16, 16, cv)


def gen_icon():
    size = 64
    cv = canvas(size, size, bg=(43, 27, 64, 255))
    cx = cy = size // 2
    for y in range(size):
        for x in range(size):
            dx, dy = x - cx, y - cy
            dist = (dx * dx + dy * dy) ** 0.5
            if dist < size * 0.42:
                t = dist / (size * 0.42)
                r = int(176 - 60 * t)
                g = int(96 - 40 * t)
                b = int(235 - 60 * t)
                cv[y][x] = (r, g, b, 255)
    # 星型のきらめき
    for i in range(-10, 11):
        px(cv, cx + i, cy, (255, 255, 255, 120))
        px(cv, cx, cy + i, (255, 255, 255, 120))
    write_png(os.path.join(OUT_ROOT, "icon.png"), size, size, cv)


def main():
    os.makedirs(OUT_ITEM, exist_ok=True)
    os.makedirs(OUT_BLOCK, exist_ok=True)
    gen_wand()
    gen_spellbook()
    gen_crystal()
    for name, (main_color, dark_color) in SPELL_COLORS.items():
        gen_scroll(name, main_color, dark_color)
    gen_altar_top()
    gen_altar_side()
    gen_altar_bottom()
    gen_icon()
    print("done")


if __name__ == "__main__":
    main()
