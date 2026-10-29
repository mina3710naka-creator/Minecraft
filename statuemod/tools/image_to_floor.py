#!/usr/bin/env python3
"""画像を、地面に敷くピクセルアートの JSON(1 層)にする。長いほうの辺が 400 ブロックぎりぎりになる大きさに自動で合わせる。

使い方:
  python3 image_to_floor.py 画像.png                  → 画像と同じ場所に 画像名.json を作る(長辺 400 ブロック)
  python3 image_to_floor.py 画像.png -o out.json      → 出力先を指定
  python3 image_to_floor.py 画像.png --size 256       → 長辺を 256 ブロックにする(大きさの上限を変える)
  python3 image_to_floor.py 画像.png --dither         → 色を混ぜて、グラデーションを細かく出す(ざらつく)
  python3 image_to_floor.py 画像.png --preview p.png  → ブロックの色で見た完成予想の画像も作る
必要なもの: Python 3、pip install pillow numpy

ゲーム内: できた json を .minecraft/statues/ に入れて、敷きたい向きを向いて /statue build <json の名前> 0
 - 手前(プレイヤー側)が画像の下、奥が画像の上。プレイヤーから見て画像が正しい向きに見える。
 - 1 層だけなので、/statue build <名前> 0 で一気に建つ。足もとと同じ高さに置く(平らな広い場所で)。
 - 透明な部分(PNG)は、ブロックを置かない。
 - 重力で落ちるブロック(コンクリートパウダー)や、向きのあるブロックは使わない。"""
import sys, json, os
import numpy as np
from PIL import Image

# 地面を上から見たときの、ブロックの色(Minecraft 26.3 の画像の平均色)。
# 落ちるブロック(砂・砂利・コンクリートパウダー)、向きや状態のあるブロック、模様の強いブロック、踏むとダメージのあるブロックは除いてある。
TABLE = [
    ("gray_concrete", (55, 58, 62)),
    ("blue_concrete", (44, 46, 143)),
    ("brown_concrete", (96, 60, 32)),
    ("green_concrete", (73, 91, 36)),
    ("cyan_concrete", (21, 119, 136)),
    ("light_gray_concrete", (125, 125, 115)),
    ("purple_concrete", (100, 32, 156)),
    ("white_concrete", (207, 213, 214)),
    ("black_terracotta", (37, 23, 16)),
    ("light_blue_concrete", (36, 137, 199)),
    ("lime_concrete", (94, 169, 24)),
    ("magenta_concrete", (169, 48, 159)),
    ("orange_concrete", (224, 97, 1)),
    ("pink_concrete", (214, 101, 143)),
    ("red_concrete", (142, 33, 33)),
    ("black_concrete", (8, 10, 15)),
    ("gray_terracotta", (58, 42, 36)),
    ("yellow_concrete", (241, 175, 21)),
    ("blue_terracotta", (74, 60, 91)),
    ("brown_terracotta", (77, 51, 36)),
    ("green_terracotta", (76, 83, 42)),
    ("light_blue_terracotta", (114, 108, 138)),
    ("light_gray_terracotta", (135, 107, 98)),
    ("terracotta", (152, 94, 68)),
    ("cyan_terracotta", (87, 91, 91)),
    ("white_terracotta", (210, 178, 161)),
    ("lime_terracotta", (104, 118, 53)),
    ("magenta_terracotta", (150, 88, 109)),
    ("orange_terracotta", (162, 84, 38)),
    ("red_terracotta", (143, 61, 47)),
    ("yellow_terracotta", (186, 133, 35)),
    ("pink_terracotta", (162, 78, 79)),
    ("purple_terracotta", (118, 70, 86)),
    ("snow_block", (249, 254, 254)),
    ("smooth_quartz", (237, 230, 224)),
    ("stripped_mangrove_wood", (120, 54, 48)),
    ("stripped_poplar_wood", (165, 153, 140)),
    ("stripped_dark_oak_wood", (73, 57, 36)),
    ("cyan_wool", (21, 138, 145)),
    ("red_sandstone", (181, 98, 32)),
    ("stripped_acacia_wood", (175, 93, 60)),
    ("smooth_sandstone", (224, 214, 170)),
    ("gray_wool", (63, 68, 72)),
    ("mud", (60, 58, 61)),
    ("red_wool", (161, 39, 35)),
    ("stripped_dark_oak_log", (66, 44, 23)),
    ("stripped_spruce_wood", (116, 90, 52)),
    ("mushroom_stem", (203, 197, 186)),
    ("stripped_cherry_wood", (215, 145, 149)),
    ("blue_wool", (53, 57, 158)),
    ("black_wool", (21, 21, 26)),
    ("clay", (161, 166, 179)),
    ("brown_wool", (114, 72, 41)),
    ("green_wool", (85, 110, 28)),
    ("stripped_crimson_hyphae", (137, 57, 90)),
    ("poplar_wood", (79, 61, 43)),
    ("lime_wool", (112, 185, 26)),
    ("blue_ice", (116, 168, 253)),
    ("stripped_pale_oak_wood", (246, 238, 237)),
    ("coal_block", (16, 16, 16)),
    ("dark_oak_wood", (60, 47, 26)),
    ("mycelium", (111, 99, 101)),
    ("stripped_acacia_log", (166, 91, 52)),
    ("stripped_mangrove_log", (109, 44, 43)),
    ("spruce_wood", (59, 38, 17)),
    ("mangrove_wood", (84, 67, 41)),
    ("lapis_block", (31, 67, 140)),
    ("nether_bricks", (44, 22, 26)),
    ("yellow_wool", (249, 198, 40)),
    ("dark_oak_planks", (67, 43, 20)),
    ("stripped_warped_hyphae", (58, 151, 148)),
    ("chiseled_quartz_block", (232, 227, 217)),
    ("packed_mud", (142, 107, 80)),
    ("light_gray_wool", (142, 142, 135)),
    ("cherry_wood", (55, 33, 44)),
    ("packed_ice", (142, 180, 250)),
    ("stripped_jungle_wood", (171, 133, 85)),
    ("stripped_birch_wood", (197, 176, 118)),
    ("stripped_crimson_stem", (122, 56, 83)),
    ("stone", (126, 126, 126)),
    ("orange_wool", (241, 118, 20)),
    ("stripped_poplar_log", (152, 139, 129)),
    ("nether_wart_block", (115, 3, 2)),
    ("warped_wart_block", (23, 120, 121)),
    ("purple_wool", (122, 42, 172)),
    ("stripped_jungle_log", (166, 123, 82)),
    ("sulfur", (190, 175, 102)),
    ("jungle_wood", (85, 68, 25)),
    ("podzol", (92, 63, 24)),
    ("stripped_oak_wood", (177, 144, 86)),
    ("cut_red_sandstone", (189, 102, 32)),
    ("mangrove_log", (103, 49, 42)),
    ("red_nether_bricks", (70, 7, 9)),
    ("white_wool", (234, 236, 237)),
    ("magenta_wool", (190, 69, 180)),
    ("soul_soil", (76, 58, 47)),
    ("light_blue_wool", (58, 175, 217)),
    ("polished_tuff", (98, 104, 100)),
    ("spruce_planks", (115, 85, 49)),
    ("netherrack", (98, 38, 38)),
    ("acacia_wood", (103, 97, 87)),
    ("cracked_deepslate_tiles", (53, 52, 53)),
    ("spruce_log", (109, 80, 47)),
    ("chiseled_red_sandstone", (183, 97, 28)),
    ("fire_coral_block", (164, 35, 47)),
    ("cinnabar", (152, 83, 78)),
    ("netherite_block", (66, 62, 64)),
    ("andesite", (136, 136, 137)),
    ("pink_wool", (238, 141, 172)),
    ("blackstone", (42, 36, 42)),
    ("mud_bricks", (137, 104, 79)),
    ("polished_blackstone", (53, 49, 57)),
    ("smooth_basalt", (73, 72, 78)),
    ("stripped_warped_stem", (52, 129, 124)),
    ("cut_sandstone", (218, 206, 160)),
    ("dark_prismarine", (52, 92, 76)),
    ("cherry_planks", (226, 179, 173)),
    ("purpur_pillar", (172, 128, 172)),
    ("stripped_birch_log", (191, 172, 116)),
    ("end_stone", (220, 223, 158)),
    ("pale_oak_wood", (88, 78, 75)),
    ("chiseled_tuff_bricks", (111, 114, 107)),
    ("deepslate", (87, 87, 89)),
    ("exposed_copper", (161, 126, 104)),
    ("crimson_planks", (101, 49, 71)),
    ("polished_cinnabar", (152, 58, 55)),
    ("stripped_pale_oak_log", (235, 227, 226)),
    ("horn_coral_block", (216, 200, 66)),
    ("redstone_block", (176, 25, 5)),
    ("dripstone_block", (134, 108, 92)),
    ("polished_blackstone_bricks", (48, 42, 50)),
    ("iron_block", (220, 220, 220)),
    ("smooth_stone", (159, 159, 159)),
    ("stripped_oak_log", (160, 130, 77)),
    ("obsidian", (15, 11, 25)),
    ("polished_sulfur", (188, 170, 91)),
    ("birch_planks", (192, 175, 121)),
    ("dead_bubble_coral_block", (132, 124, 119)),
    ("copper_block", (192, 108, 80)),
    ("cracked_stone_bricks", (118, 118, 118)),
    ("weathered_copper", (108, 153, 110)),
    ("calcite", (224, 224, 221)),
    ("exposed_cut_copper", (155, 122, 101)),
    ("oxidized_copper", (82, 163, 133)),
    ("stripped_cherry_log", (221, 165, 158)),
    ("acacia_log", (151, 89, 55)),
    ("bubble_coral_block", (165, 26, 162)),
    ("oak_wood", (109, 85, 51)),
    ("polished_deepslate", (72, 73, 73)),
    ("chiseled_tuff", (94, 99, 91)),
    ("cobbled_deepslate", (77, 77, 81)),
    ("crimson_hyphae", (93, 26, 30)),
    ("crimson_stem", (113, 50, 70)),
    ("tuff", (108, 109, 103)),
    ("chiseled_cinnabar", (147, 55, 55)),
    ("tube_coral_block", (49, 87, 207)),
    ("chiseled_copper", (184, 101, 74)),
    ("dead_brain_coral_block", (124, 118, 114)),
    ("bamboo_mosaic", (190, 170, 78)),
    ("granite", (149, 103, 86)),
    ("weathered_cut_copper", (109, 145, 108)),
    ("crimson_nylium", (131, 31, 32)),
    ("end_stone_bricks", (218, 224, 162)),
    ("brain_coral_block", (208, 91, 159)),
    ("oxidized_cut_copper", (80, 154, 126)),
    ("resin_block", (217, 99, 25)),
    ("cracked_deepslate_bricks", (65, 65, 65)),
    ("dirt", (134, 96, 67)),
    ("weathered_chiseled_copper", (105, 151, 111)),
    ("pale_oak_planks", (228, 218, 216)),
    ("polished_basalt", (99, 99, 101)),
    ("jungle_planks", (160, 115, 81)),
    ("prismarine_bricks", (99, 172, 158)),
    ("warped_nylium", (43, 114, 101)),
    ("warped_planks", (43, 105, 99)),
    ("gold_block", (246, 208, 62)),
    ("resin_bricks", (206, 88, 24)),
    ("chiseled_resin_bricks", (201, 84, 25)),
    ("bricks", (151, 98, 83)),
    ("basalt", (81, 82, 86)),
    ("coarse_dirt", (119, 86, 59)),
    ("warped_hyphae", (58, 59, 78)),
    ("warped_stem", (53, 110, 110)),
    ("rooted_dirt", (144, 104, 76)),
    ("diamond_block", (98, 237, 228)),
    ("prismarine", (98, 162, 146)),
    ("oak_log", (151, 122, 73)),
    ("bamboo_block", (139, 142, 62)),
    ("jungle_log", (150, 109, 71)),
    ("raw_copper_block", (154, 106, 79)),
    ("amethyst_block", (134, 98, 191)),
    ("honeycomb_block", (229, 148, 30)),
    ("dried_kelp_block", (50, 59, 39)),
    ("stripped_bamboo_block", (178, 159, 73)),
    ("polished_diorite", (193, 193, 195)),
    ("diorite", (189, 188, 189)),
    ("poplar_log", (136, 122, 110)),
    ("emerald_block", (42, 203, 88)),
    ("raw_gold_block", (222, 169, 47)),
    ("birch_log", (193, 179, 135)),
    ("raw_iron_block", (166, 136, 107)),
]
BLOCKS = {"minecraft:" + n: c for n, c in TABLE}
NAMES = list(BLOCKS)
RGB = np.array([BLOCKS[n] for n in NAMES], float)
CH = [chr(c) for c in range(0x21, 0x7F) if chr(c) not in '"\\'] + [chr(c) for c in range(0x3041, 0x3097)] + [chr(c) for c in range(0x30A1, 0x30FB)]   # 1 文字のキー(英数字・ひらがな・カタカナ)

def to_lab(rgb):
    c = rgb / 255.0
    c = np.where(c > 0.04045, ((c + 0.055) / 1.055) ** 2.4, c / 12.92)
    M = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = c @ M.T / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], -1)

LAB = to_lab(RGB)

def convert(img, size, dither):
    """長辺が size ブロックになるように縮め(拡大もする)、各画素を近いブロックにする。-1 は透明(置かない)"""
    img = img.convert("RGBA")
    scale = size / max(img.width, img.height)
    w = max(1, round(img.width * scale)); h = max(1, round(img.height * scale))
    small = img.resize((w, h), Image.LANCZOS)
    arr = np.array(small).astype(float)
    alpha = arr[:, :, 3] >= 128
    rgb = arr[:, :, :3]
    idx = np.zeros((h, w), int)
    if not dither:
        lab = to_lab(rgb)
        for y in range(h):                                       # 1 行ずつ(メモリを使いすぎない)
            idx[y] = ((lab[y][:, None, :] - LAB[None, :, :]) ** 2).sum(-1).argmin(-1)
    else:                                   # Floyd–Steinberg(RGB 上で誤差を拡散)
        buf = rgb.copy()
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
    idx[~alpha] = -1
    return idx

def write_json(idx, out, name):
    used = sorted(set(idx.ravel().tolist()) - {-1})
    chars = {i: CH[k] for k, i in enumerate(used)}
    h, w = idx.shape
    rows = ["".join(chars[i] if i >= 0 else " " for i in idx[y]) for y in range(h - 1, -1, -1)]      # 先頭の行 = 手前 = 画像の下
    text = "{\n"
    text += '  "name": %s,\n' % json.dumps(name, ensure_ascii=False)
    text += '  "comment": "地面に敷く 1 層の画像。先頭の行が手前(プレイヤー側)=画像の下。/statue build 名前 0 で建つ。",\n'
    text += '  "size": [%d, %d, 1],\n' % (w, h)
    text += '  "palette": {\n' + ",\n".join("    %s: %s" % (json.dumps(chars[i]), json.dumps(NAMES[i])) for i in used) + "\n  },\n"
    text += '  "layers": [\n    [\n' + ",\n".join("      " + json.dumps(r) for r in rows) + "\n    ]\n  ]\n}\n"
    open(out, "w", encoding="utf-8").write(text)
    return len(used), w, h

if __name__ == "__main__":
    import argparse, re
    ap = argparse.ArgumentParser(description="画像を地面に敷くピクセルアートの JSON にする(長辺 400 ブロックぎりぎり)")
    ap.add_argument("image")
    ap.add_argument("-o", "--out", help="出力の json(省略すると画像と同じ場所に 画像名.json)")
    ap.add_argument("--size", type=int, default=400, help="長いほうの辺のブロック数の上限(既定 400)")
    ap.add_argument("--dither", action="store_true", help="色を混ぜて細かい階調を出す")
    ap.add_argument("--preview", help="完成予想の画像(png)")
    a = ap.parse_args()
    out = a.out
    if not out:
        stem = re.sub(r"[^A-Za-z0-9_.+-]", "_", os.path.splitext(os.path.basename(a.image))[0]).strip("_") or "image"
        out = os.path.join(os.path.dirname(os.path.abspath(a.image)), stem + ".json")
    img = Image.open(a.image)
    idx = convert(img, a.size, a.dither)
    nuse, w, h = write_json(idx, out, os.path.splitext(os.path.basename(out))[0])
    print("%s: %d x %d ブロック(使ったブロック %d 種、合計 %d ブロック)" % (out, w, h, nuse, int((idx >= 0).sum())))
    print("ゲーム内: .minecraft/statues/ に入れて /statue build %s 0" % os.path.splitext(os.path.basename(out))[0])
    if a.preview:
        prev = np.where((idx >= 0)[..., None], RGB[np.maximum(idx, 0)], 255).astype(np.uint8)
        k = max(1, 900 // max(w, h))
        Image.fromarray(prev).resize((w * k, h * k), Image.NEAREST).save(a.preview)
