#!/usr/bin/env python3
"""画像を、地面に敷くピクセルアートの JSON(1 層)にする。

使い方: python3 -I image_to_floor.py <画像> <出力.json> [幅(ブロック)=192] [ディザ 0/1=0] [プレビュー.png]
 - 建てる向き: プレイヤーが向いている方向の先へ、手前が画像の「下」、奥が画像の「上」。
   プレイヤーから見て、画像が正しい向き(上が奥)に見える。
 - 1 層だけなので、/statue build <名前> 0 で一気に建つ。
 - 重力で落ちるブロック(コンクリートパウダー)や、向きのあるブロックは使わない。"""
import sys, json, os
import numpy as np
from PIL import Image

COLORS = {
 "white":(207,213,214),"orange":(224,97,0),"magenta":(169,48,159),"light_blue":(36,137,199),"yellow":(240,175,21),
 "lime":(94,168,24),"pink":(213,101,142),"gray":(55,58,62),"light_gray":(125,125,115),"cyan":(21,119,136),
 "purple":(100,31,156),"blue":(45,47,143),"brown":(96,60,32),"green":(73,91,36),"red":(142,33,33),"black":(8,10,15)}
WOOL = {
 "white":(233,236,236),"orange":(240,118,19),"magenta":(189,68,179),"light_blue":(58,175,217),"yellow":(248,197,39),
 "lime":(112,185,25),"pink":(237,141,172),"gray":(62,68,71),"light_gray":(142,142,134),"cyan":(21,137,145),
 "purple":(121,42,172),"blue":(53,57,157),"brown":(114,71,40),"green":(84,109,27),"red":(160,39,34),"black":(20,21,25)}
TERRA = {
 "":(152,94,67),"white":(209,178,161),"orange":(161,83,37),"magenta":(149,88,108),"light_blue":(113,108,137),
 "yellow":(186,133,35),"lime":(103,117,52),"pink":(161,78,78),"gray":(57,42,35),"light_gray":(135,106,97),
 "cyan":(86,91,91),"purple":(118,70,86),"blue":(74,59,91),"brown":(77,51,36),"green":(76,83,42),
 "red":(143,61,46),"black":(37,22,16)}
EXTRA = {
 "snow_block":(249,254,254),"blue_ice":(116,167,253),"packed_ice":(141,180,250),"gold_block":(246,208,61),
 "sand":(219,207,163),"sandstone":(224,214,170),"stone":(125,125,125),"deepslate":(80,80,82),
 "nether_bricks":(44,22,26),"purpur_block":(169,125,169),"amethyst_block":(133,97,191),"calcite":(223,224,220),
 "end_stone":(219,222,158),"dark_oak_planks":(66,43,20),"spruce_planks":(114,84,48),"birch_planks":(192,175,121),
 "iron_block":(220,220,220),"coal_block":(16,16,16),"lapis_block":(31,67,140),"emerald_block":(42,203,88),
 "redstone_block":(175,24,5),"copper_block":(192,107,79),"oxidized_copper":(82,162,132),"netherrack":(97,38,38)}
BLOCKS = {}
for k, v in COLORS.items(): BLOCKS["minecraft:%s_concrete" % k] = v
for k, v in WOOL.items(): BLOCKS["minecraft:%s_wool" % k] = v
for k, v in TERRA.items(): BLOCKS["minecraft:%sterracotta" % (k + "_" if k else "")] = v
for k, v in EXTRA.items(): BLOCKS["minecraft:" + k] = v
NAMES = list(BLOCKS)
RGB = np.array([BLOCKS[n] for n in NAMES], float)
CH = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&()*+,-./:;<=>?@[]^_`{|}~"

def to_lab(rgb):
    c = rgb / 255.0
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    M = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = c @ M.T / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)

LAB = to_lab(RGB)

def convert(img, width, dither):
    w = width; h = max(1, round(img.height * w / img.width))
    small = np.array(img.convert("RGB").resize((w, h), Image.LANCZOS)).astype(float)
    idx = np.zeros((h, w), int)
    if not dither:
        lab = to_lab(small)
        idx = ((lab[:, :, None, :] - LAB[None, None, :, :]) ** 2).sum(-1).argmin(-1)
    else:                                   # Floyd–Steinberg(RGB 上で誤差を拡散)
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

def write_json(idx, out, name):
    used = sorted(set(idx.ravel().tolist()))
    chars = {i: CH[k] for k, i in enumerate(used)}
    h, w = idx.shape
    rows = ["".join(chars[i] for i in idx[y]) for y in range(h - 1, -1, -1)]      # 先頭の行 = 手前 = 画像の下
    text = "{\n"
    text += '  "name": %s,\n' % json.dumps(name, ensure_ascii=False)
    text += '  "comment": "地面に敷く 1 層の画像。先頭の行が手前(プレイヤー側)=画像の下。/statue build 名前 0 で建つ。",\n'
    text += '  "size": [%d, %d, 1],\n' % (w, h)
    text += '  "palette": {\n' + ",\n".join("    %s: %s" % (json.dumps(chars[i]), json.dumps(NAMES[i])) for i in used) + "\n  },\n"
    text += '  "layers": [\n    [\n' + ",\n".join("      " + json.dumps(r) for r in rows) + "\n    ]\n  ]\n}\n"
    open(out, "w", encoding="utf-8").write(text)
    return len(used), w, h

if __name__ == "__main__":
    src, out = sys.argv[1], sys.argv[2]
    width = int(sys.argv[3]) if len(sys.argv) > 3 else 192
    dither = int(sys.argv[4]) if len(sys.argv) > 4 else 0
    img = Image.open(src)
    idx = convert(img, width, dither)
    nuse, w, h = write_json(idx, out, os.path.splitext(os.path.basename(out))[0])
    print("%d x %d ブロック、使ったブロック %d 種、合計 %d ブロック" % (w, h, nuse, w * h))
    if len(sys.argv) > 5:
        prev = RGB[idx].astype(np.uint8)
        Image.fromarray(prev).resize((w * max(1, 900 // w), h * max(1, 900 // w)), Image.NEAREST).save(sys.argv[5])
