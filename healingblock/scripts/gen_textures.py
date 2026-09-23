#!/usr/bin/env python3
"""PILなしで16x16(と32x32)のシンプルなプレースホルダーPNGテクスチャを生成するスクリプト。
zlibだけを使って手書きでPNGバイナリを組み立てている(arcanemagic MODのスクリプトと同じ手法)。
出来上がる絵はかなり簡素なので、後で好きなドット絵に差し替えるのを推奨(README参照)。
"""
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "healingblock")
OUT_BLOCK = os.path.join(ROOT, "textures", "block")


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

	os.makedirs(os.path.dirname(path), exist_ok=True)
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


def scale2x(cv):
	h = len(cv)
	w = len(cv[0])
	out = canvas(w * 2, h * 2)
	for y in range(h):
		for x in range(w):
			c = cv[y][x]
			px(out, x * 2, y * 2, c)
			px(out, x * 2 + 1, y * 2, c)
			px(out, x * 2, y * 2 + 1, c)
			px(out, x * 2 + 1, y * 2 + 1, c)
	return out


STONE = (168, 168, 168, 255)
STONE_LIGHT = (196, 196, 196, 255)
STONE_DARK = (128, 128, 128, 255)
CROSS = (86, 214, 122, 255)
CROSS_LIGHT = (150, 240, 170, 255)
CROSS_DARK = (48, 150, 78, 255)
GOLD = (240, 210, 90, 255)


def gen_healing_altar():
	cv = canvas(16, 16, STONE)

	# 石タイルっぽい格子模様(継ぎ目)
	for i in range(0, 16, 8):
		for x in range(16):
			px(cv, x, i, STONE_DARK)
		for y in range(16):
			px(cv, i, y, STONE_DARK)
	for x in range(16):
		px(cv, x, 0, STONE_LIGHT)
	for y in range(16):
		px(cv, 0, y, STONE_LIGHT)

	# 中央に十字(回復マーク)
	rect(cv, 7, 3, 8, 12, CROSS_DARK)
	rect(cv, 3, 7, 12, 8, CROSS_DARK)
	rect(cv, 7, 4, 8, 11, CROSS)
	rect(cv, 4, 7, 11, 8, CROSS)
	px(cv, 7, 4, CROSS_LIGHT)
	px(cv, 8, 4, CROSS_LIGHT)
	px(cv, 4, 7, CROSS_LIGHT)
	px(cv, 4, 8, CROSS_LIGHT)

	# 四隅にきらめき
	for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
		px(cv, x, y, GOLD)

	return cv


def main():
	altar = gen_healing_altar()
	write_png(os.path.join(OUT_BLOCK, "healing_altar.png"), 16, 16, altar)
	write_png(os.path.join(ROOT, "icon.png"), 32, 32, scale2x(altar))
	print("generated healing_altar.png and icon.png")


if __name__ == "__main__":
	main()
