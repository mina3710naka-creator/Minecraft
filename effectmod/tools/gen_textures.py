"""付与台のテクスチャ(16x16)を作る。 python3 tools/gen_textures.py"""
import os, random
from PIL import Image

out = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/effectmod/textures/block")
random.seed(7)

def base(c, jitter=10):
    im = Image.new("RGB", (16, 16))
    for x in range(16):
        for y in range(16):
            j = random.randint(-jitter, jitter)
            im.putpixel((x, y), tuple(max(0, min(255, v + j)) for v in c))
    return im

def border(im, c):
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            im.putpixel(p, c)

# 側面: 黒曜石っぽい本体 + 紫の模様
side = base((38, 28, 56))
border(side, (90, 60, 150))
for x in range(4, 12):
    for y in range(4, 12):
        if (x in (4, 11)) or (y in (4, 11)):
            side.putpixel((x, y), (155, 107, 255))
for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
    side.putpixel((x, y), (255, 220, 120))
# 上面: 魔法陣
top = base((30, 22, 46))
border(top, (155, 107, 255))
cx = cy = 7.5
for x in range(16):
    for y in range(16):
        d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if 4.6 < d < 5.6:
            top.putpixel((x, y), (190, 140, 255))
        if abs(x - 7.5) < 0.6 or abs(y - 7.5) < 0.6:
            if d < 5.6:
                top.putpixel((x, y), (255, 220, 120))
bottom = base((22, 16, 34))
border(bottom, (60, 40, 100))
for n, im in (("side", side), ("top", top), ("bottom", bottom)):
    im.save(os.path.join(out, f"effect_bench_{n}.png"))
