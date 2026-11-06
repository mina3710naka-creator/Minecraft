#!/usr/bin/env python3
"""敵・使い魔の独自 3D モデルとテクスチャを生成する。

出力:
  assets/magicmod/models/custom/<id>.json   部品と箱の定義(Java が起動時に読む)
  assets/magicmod/textures/entity/<id>.png      本体テクスチャ
  assets/magicmod/textures/entity/<id>_glow.png 発光テクスチャ(目・核・紋様)

座標は「x=右, y=地面から上, z=前」で書き、出力時に Minecraft のモデル座標へ変換する。
"""
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets", "magicmod")


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def shade(c, f):
    r, g, b = rgb(c) if isinstance(c, int) else c
    if f >= 1:
        k = f - 1
        return (min(255, int(r + (255 - r) * k)), min(255, int(g + (255 - g) * k)), min(255, int(b + (255 - b) * k)))
    return (int(r * f), int(g * f), int(b * f))


class Model:
    def __init__(self, c1, c2, cg, accent=None, style=None):
        self.style = style
        self.parts = []
        self.cubes = []   # (part, dict)
        self.pal = {"main": c1, "dark": shade(c1, 0.55), "light": shade(c1, 1.35), "sub": c2, "subdark": shade(c2, 0.6),
                    "accent": accent if accent is not None else shade(c2, 1.4), "glow": cg, "bone": 0xE8E0CC, "black": 0x18141C}

    def part(self, name, pivot=(0, 0, 0), parent=None, anim=None, amp=1.0, phase=0.0, stage=None, rest=None):
        x, y, z = pivot
        if parent is None:
            piv = [x, 24 - y, -z]
        else:
            piv = [x, -y, -z]
        self.parts.append({"name": name, "parent": parent, "pivot": piv, "anim": anim, "amp": amp, "phase": phase,
                           "stage": list(stage) if stage else [1, 9], "rest": rest})
        return name

    def extra(self, parent, lo, hi, name=None):
        """親の部品と同じ位置・向きで、進化段階 lo〜hi のときだけ表示される追加部品。"""
        nm = name or f"{parent}_x{len([p for p in self.parts if p['name'].startswith(parent + '_x')])}"
        self.part(nm, (0, 0, 0), parent=parent, stage=(lo, hi))
        return nm

    def cube(self, part, cx, by, cz, w, h, d, role="main", infl=0.0, feat=None, mirror=False, style=None):
        w, h, d = int(round(w)), int(round(h)), int(round(d))
        w, h, d = max(1, w), max(1, h), max(1, d)
        self.cubes.append((part, {"box": [cx - w / 2, -(by + h), -(cz + d / 2), w, h, d], "role": role, "infl": infl, "feat": feat,
                                  "mirror": mirror, "dim": (w, h, d), "style": style or (self.style if role in ("main", "light", "dark", "sub", "subdark") else None)}))


def pack(m):
    """各箱の展開図を並べて貼り付け位置を決める。"""
    items = []
    for i, (p, c) in enumerate(m.cubes):
        w, h, d = c["dim"]
        items.append((i, 2 * (w + d), h + d))
    maxw = max(t[1] for t in items)
    for size in (32, 48, 64, 96, 128, 192, 256, 384):
        if size < maxw:
            continue
        x = y = rowh = 0
        pos = {}
        ok = True
        for i, fw, fh in sorted(items, key=lambda t: -t[2]):
            if x + fw > size:
                x = 0
                y += rowh
                rowh = 0
            if y + fh > size:
                ok = False
                break
            pos[i] = (x, y)
            x += fw
            rowh = max(rowh, fh)
        if ok:
            return size, pos
    raise RuntimeError("テクスチャが大きすぎる")


def paint(m, size, pos, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()
    for i, (part, c) in enumerate(m.cubes):
        w, h, d = c["dim"]
        u, v = pos[i]
        base = m.pal.get(c["role"], c["role"]) if isinstance(c["role"], str) else c["role"]
        if isinstance(base, int):
            base = rgb(base)
        faces = {"right": (u, v + d, d, h, 0.8), "front": (u + d, v + d, w, h, 1.0), "left": (u + d + w, v + d, d, h, 0.8),
                 "back": (u + 2 * d + w, v + d, w, h, 0.7), "top": (u + d, v, w, d, 1.2), "bottom": (u + d + w, v, w, d, 0.5)}
        for name, (fx, fy, fw, fh, f) in faces.items():
            for yy in range(fh):
                for xx in range(fw):
                    n = 1 + (rnd.random() - 0.5) * 0.16
                    g = f * n * (1.06 - 0.12 * yy / max(1, fh)) if name not in ("top", "bottom") else f * n
                    col = shade(base, g) if g < 1 else shade(base, g)
                    edge = xx == 0 or yy == 0 or xx == fw - 1 or yy == fh - 1
                    if edge:
                        col = shade(col, 0.78)
                    px[fx + xx, fy + yy] = col + (255,)
        st = c.get("style")
        if st:
            for name, (fx, fy, fw, fh, f) in faces.items():
                for yy in range(fh):
                    for xx in range(fw):
                        r0, g0, b0, a0 = px[fx + xx, fy + yy]
                        k = 1.0
                        if st == "fur":
                            k = 1 + 0.16 * (((xx * 7 + (yy // 2) * 13) % 5) - 2) / 2 - 0.05 * (yy % 2)
                        elif st == "scale":
                            k = 0.82 if (xx + (yy // 2 % 2) * 2) % 4 == 0 else (1.12 if yy % 2 == 0 else 1.0)
                        elif st == "plate":
                            k = 0.7 if (xx % 5 == 0 or yy % 5 == 0) else (1.18 if (xx % 5 == 1 and yy % 5 == 1) else 1.0)
                        elif st == "feather":
                            k = 1.2 if (xx + yy) % 4 == 0 else (0.85 if (xx + yy) % 4 == 2 else 1.0)
                        elif st == "crystal":
                            k = 1.25 if (xx - yy) % 5 == 0 else (0.8 if (xx - yy) % 5 == 2 else 1.0)
                        elif st == "stone":
                            h = (xx * 31 + yy * 17 + fw * 7) % 11
                            k = 0.7 if h == 0 else (1.15 if h == 5 else 1.0)
                        px[fx + xx, fy + yy] = (min(255, int(r0 * k)), min(255, int(g0 * k)), min(255, int(b0 * k)), a0)
        feat = c["feat"]
        if feat:
            fx, fy, fw, fh, _ = faces["front"]
            gc = rgb(m.pal["glow"])
            if feat == "eyes":
                ey = fy + max(1, fh // 3)
                for ex in (fx + max(1, fw // 4) - (1 if fw < 5 else 0), fx + fw - max(1, fw // 4) - (1 if fw >= 5 else 0)):
                    for dx in range(2 if fw >= 5 else 1):
                        for dy in range(1 if fh < 6 else 2):
                            if ex + dx < fx + fw and ey + dy < fy + fh:
                                px[ex + dx, ey + dy] = gc + (255,)
                                gx[ex + dx, ey + dy] = gc + (255,)
                if fh >= 5 and fw >= 4:
                    my = fy + fh - 2
                    for xx in range(fx + 1, fx + fw - 1):
                        px[xx, my] = shade(base, 0.35) + (255,)
            elif feat == "eye1":
                cx, cy = fx + fw // 2, fy + fh // 2
                for dx in range(-1, 2):
                    for dy in range(-1, 2):
                        if 0 <= cx + dx < size and 0 <= cy + dy < size:
                            px[cx + dx, cy + dy] = gc + (255,)
                            gx[cx + dx, cy + dy] = gc + (255,)
            elif feat == "core":
                for name, (ax, ay, aw, ah, _) in faces.items():
                    for yy in range(ah):
                        for xx in range(aw):
                            edge = xx == 0 or yy == 0 or xx == aw - 1 or yy == ah - 1
                            k = 0.75 if edge else 1.0
                            col = shade(gc, k + 0.15 * math.sin((xx + yy) * 0.9))
                            px[ax + xx, ay + yy] = col + (255,)
                            gx[ax + xx, ay + yy] = col + (255,)
            elif feat in ("runes", "stripes"):
                for name in ("front", "left", "right", "back"):
                    ax, ay, aw, ah, _ = faces[name]
                    if feat == "stripes":
                        for yy in range(1, ah, 3):
                            for xx in range(aw):
                                px[ax + xx, ay + yy] = shade(m.pal["accent"], 1.0) + (255,)
                    else:
                        for _ in range(max(2, aw * ah // 14)):
                            xx, yy = rnd.randrange(aw), rnd.randrange(ah)
                            px[ax + xx, ay + yy] = gc + (255,)
                            gx[ax + xx, ay + yy] = gc + (255,)
            elif feat == "belly":
                for yy in range(fh // 3, fh):
                    for xx in range(1, fw - 1):
                        px[fx + xx, fy + yy] = shade(m.pal["sub"], 1.0 - 0.1 * ((xx + yy) % 2)) + (255,)
            elif feat == "spots":
                for _ in range(max(2, w * h // 6)):
                    for name in ("front", "left", "right", "top"):
                        ax, ay, aw, ah, _ = faces[name]
                        xx, yy = rnd.randrange(aw), rnd.randrange(ah)
                        px[ax + xx, ay + yy] = shade(m.pal["sub"], 1.0) + (255,)
    return img, glow


def hitbox(m):
    """モデルの見た目に合わせた当たり判定 [幅, 高さ](ブロック単位)。輪・周回物は含めず、翼は半分だけ数える。"""
    info = {p["name"]: p for p in m.parts}

    def origin(name):
        x = y = z = 0.0
        while name:
            p = info[name]
            x += p["pivot"][0]
            y += p["pivot"][1]
            z += p["pivot"][2]
            name = p["parent"]
        return x, y, z

    def top(name):
        while name:
            if info[name]["parent"] is None:
                return name
            name = info[name]["parent"]
        return name

    skip = ("orbit", "halo", "ring", "orb", "rock")
    ymin, ymax, bw, ww, zw = 99.0, -99.0, 0.0, 0.0, 0.0
    for part, c in m.cubes:
        t = top(part)
        if t.startswith(skip) or part.startswith(skip):
            continue
        ox, oy, oz = origin(part)
        b = c["box"]
        y0, y1 = oy + b[1], oy + b[1] + b[4]
        ymin, ymax = min(ymin, y0), max(ymax, y1)
        xs = max(abs(ox + b[0]), abs(ox + b[0] + b[3])) / 16
        zs = max(abs(oz + b[2]), abs(oz + b[2] + b[5])) / 16
        if "wing" in part or part[:2] == "fl":
            ww = max(ww, xs)
        elif part.startswith(("tail", "tl", "sg", "tn")):
            continue
        else:
            bw = max(bw, xs)
            zw = max(zw, zs)
    h = max(0.4, (24 - ymin) / 16)
    w = max(0.5, 2 * max(bw, ww * 0.4, zw * 0.4))
    return [round(min(w, 3.0), 2), round(min(h, 6.0), 2)]


def export(name, m, seed):
    size, pos = pack(m)
    img, glow = paint(m, size, pos, seed)
    os.makedirs(os.path.join(RES, "models", "custom"), exist_ok=True)
    os.makedirs(os.path.join(RES, "textures", "entity"), exist_ok=True)
    img.save(os.path.join(RES, "textures", "entity", name + ".png"))
    glow.save(os.path.join(RES, "textures", "entity", name + "_glow.png"))
    out = {"tex": size, "parts": m.parts, "cubes": [], "hitbox": hitbox(m)}
    for i, (part, c) in enumerate(m.cubes):
        out["cubes"].append({"part": part, "uv": list(pos[i]), "box": [round(v, 3) for v in c["box"]], "infl": c["infl"],
                             "mirror": c["mirror"]})
    with open(os.path.join(RES, "models", "custom", name + ".json"), "w") as f:
        json.dump(out, f)


# ---------------------------------------------------------------------------------------------
# 型紙

def biped(m, legh=10, tw=8, th=12, td=5, hs=8, aw=3, ah=11, robe=False, skirt=0, eyes=True, longarm=0, horns=0, hat=0, crown=False, halo=False,
          wings=0, wingh=10, cape=False, tailn=0, sword=False, hornrole="bone", shoulder=0, glowchest=False, mane=False, snake=0):
    # 脚
    if snake:
        prev = None
        for i in range(snake):
            seg = f"tl{i}"
            ww = max(2, tw - 1 - i)
            if prev is None:
                m.part(seg, (0, 3, 0), anim="tail", amp=0.4, phase=0)
                m.cube(seg, 0, -3, 0, ww, 6, td + 1, "sub")
            else:
                m.part(seg, (0, 0, -4), parent=prev, anim="tail", amp=0.4, phase=i * 0.8)
                m.cube(seg, 0, -3, 0, ww, max(3, 6 - i // 2), 5, "sub" if i % 2 else "main")
            prev = seg
    elif robe or skirt:
        m.part("skirt", (0, legh, 0))
        m.cube("skirt", 0, -legh, 0, tw + 2, legh, td + 2, "sub", feat="stripes")
    else:
        for side, nm in ((-1, "legA"), (1, "legB")):
            m.part(nm, (side * tw / 4, legh, 0), anim=nm, amp=1.0)
            m.cube(nm, 0, -legh, 0, tw / 2, legh, td - 1 if td > 3 else td, "dark")
    m.part("body", (0, legh, 0))
    m.cube("body", 0, 0, 0, tw, th, td, "main", feat="core" if glowchest else None)
    if glowchest:
        m.cube("body", 0, th / 2 - 1, -(td / 2 + 0.5), 4, 4, 1, "main", feat="core")
    if cape:
        m.part("cape", (0, legh + th - 1, -td / 2), anim="tail", amp=0.2)
        m.cube("cape", 0, -(th + 1), -1, tw + 1, th + legh // 2, 1, "sub", feat="stripes")
    m.part("head", (0, legh + th, 0), anim="head")
    m.cube("head", 0, 0, 0, hs, hs, hs, "light", feat="eyes" if eyes else None)
    if horns:
        for s in (-1, 1):
            m.cube("head", s * (hs / 2 - 1), hs - 1, 0, 2, horns, 2, hornrole)
            m.cube("head", s * (hs / 2 + 0.5), hs - 1 + horns - 1, 0, 2, 2, 2, hornrole)
    if hat:
        m.cube("head", 0, hs - 1, 0, hs + 3, 2, hs + 3, "sub")
        m.cube("head", 0, hs + 1, 0, hs - 1, hat, hs - 1, "sub")
        m.cube("head", 0, hs + 1 + hat, 0, hs - 4, 3, hs - 4, "subdark")
    if crown:
        m.cube("head", 0, hs, 0, hs + 1, 2, hs + 1, "accent", feat="runes")
        for sx in (-1, 0, 1):
            m.cube("head", sx * 3, hs + 2, 0, 2, 2, 2, "accent")
    if halo:
        m.part("halo", (0, legh + th + hs + 4, 0), anim="spin", amp=1.0)
        m.cube("halo", 0, 0, 0, 10, 1, 10, "glow", feat="core")
    if mane:
        m.cube("head", 0, -2, -hs / 2, hs + 2, hs + 2, 3, "dark")
    # 腕
    for side, nm in ((-1, "armA"), (1, "armB")):
        m.part(nm, (side * (tw / 2 + aw / 2), legh + th - 1.5, 0), anim=nm, amp=1.0)
        m.cube(nm, 0, -(ah - 1.5) - longarm, 0, aw, ah + longarm, aw, "main")
        if shoulder:
            m.cube(nm, 0, -1, 0, aw + 2, shoulder, aw + 2, "dark", feat="runes")
        if sword and side == 1:
            m.cube(nm, 0, -(ah - 1.5) - longarm - 1, 3, 2, 3, 3, "dark")
            m.cube(nm, 0, -(ah - 1.5) - longarm - 1, 3.5, 1, 1, 18, "bone")
    if wings:
        for side, nm in ((-1, "wingL"), (1, "wingR")):
            m.part(nm, (side * 2, legh + th - 2, -td / 2), anim=nm, amp=1.0)
            m.cube(nm, side * wings / 2, -wingh // 2, -0.5, wings, wingh, 1, "sub")
            m.cube(nm, side * (wings / 2 + 1), 0, -0.5, wings - 3, 3, 1, "accent")
    if tailn:
        m.part("tail", (0, legh + 1, -td / 2), anim="tail", amp=1.0)
        m.cube("tail", 0, 0, -tailn / 2, 2, 2, tailn, "sub")


def quad(m, bl=14, bw=7, bh=7, lh=8, hs=6, snout=3, tail=8, horns=0, mane=False, spikes=0, eyes=True, wings=0, hsize=None):
    m.part("body", (0, lh, 0))
    m.cube("body", 0, 0, 0, bw, bh, bl, "main", feat="spots")
    if mane:
        m.cube("body", 0, bh - 1, bl / 2 - 3, bw + 2, 3, 7, "dark")
    for i in range(spikes):
        m.cube("body", 0, bh, bl / 2 - 2 - i * (bl / max(1, spikes)), 2, 3, 2, "accent")
    for fz, tag in ((bl / 2 - 2, "F"), (-bl / 2 + 2, "B")):
        for side, nm in ((-1, "A"), (1, "B")):
            anim = "legA" if (nm == "A") == (tag == "F") else "legB"
            part = "leg" + tag + nm
            m.part(part, (side * (bw / 2 - 1), lh, fz), anim=anim, amp=0.9)
            m.cube(part, 0, -lh, 0, 3, lh, 3, "dark")
    m.part("head", (0, lh + bh - 1, bl / 2), anim="head")
    m.cube("head", 0, -2, 1, hs, hs, hs, "light", feat="eyes" if eyes else None)
    m.cube("head", 0, -2, hs / 2 + snout / 2 + 0.5, hs - 2, 3, snout, "sub")
    for s in (-1, 1):
        m.cube("head", s * (hs / 2 - 1), hs - 3, 0, 2, 3, 2, "dark")
    if horns:
        for s in (-1, 1):
            m.cube("head", s * 2, hs - 2, 1, 1, horns, 1, "bone")
    m.part("tail", (0, lh + bh - 2, -bl / 2), anim="tail", amp=1.0)
    m.cube("tail", 0, -1, -tail / 2, 2, 3, tail, "sub")
    if wings:
        for side, nm in ((-1, "wingL"), (1, "wingR")):
            m.part(nm, (side * bw / 2, lh + bh, 0), anim=nm, amp=0.8)
            m.cube(nm, side * wings / 2, 0, 0, wings, 1, 8, "sub")


def flyer(m, bw=6, bh=4, bl=9, wing=14, wd=7, tail=7, eyes=True, fang=False, hs=5, wingrole="sub", y=10):
    m.part("root", (0, y, 0), anim="bob", amp=1.0)
    m.part("body", (0, 0, 0), parent="root")
    m.cube("body", 0, 0, 0, bw, bh, bl, "main", feat="spots")
    m.part("head", (0, 1, bl / 2), parent="root", anim="head")
    m.cube("head", 0, 0, 1, hs, hs - 1, hs, "light", feat="eyes" if eyes else None)
    for s in (-1, 1):
        m.cube("head", s * (hs / 2 - 1), hs - 1, 1, 2, 2, 2, "dark")
        if fang:
            m.cube("head", s, -1, hs / 2 + 1, 1, 2, 1, "bone")
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        m.part(nm, (side * bw / 2, bh - 1, 0), parent="root", anim=nm, amp=1.0)
        m.cube(nm, side * wing / 2, 0, 0, wing, 1, wd, wingrole, feat="stripes")
        m.cube(nm, side * (wing - 2), 0, -wd / 2 - 1, 4, 1, 3, "accent")
    m.part("tail", (0, 0, -bl / 2), parent="root", anim="tail", amp=0.8)
    m.cube("tail", 0, 0, -tail / 2, 2, 2, tail, "sub")


def blob(m, s=12, eyes=True, core=True, spikes=0, crown=False, drip=False, y=0):
    m.part("body", (0, y, 0), anim="pulse", amp=1.0)
    m.cube("body", 0, 0, 0, s, s, s, "main", feat="eyes" if eyes else None)
    if core:
        m.cube("body", 0, s / 2 - s // 4, 0, max(2, s // 2), max(2, s // 2), max(2, s // 2), "glow", feat="core", infl=-0.2)
    for i in range(spikes):
        a = i * math.tau / max(1, spikes)
        m.cube("body", math.cos(a) * s / 2, s / 2 - 1, math.sin(a) * s / 2, 2, max(3, s // 3), 2, "accent")
    if crown:
        for sx in (-2, 0, 2):
            m.cube("body", sx * (s / 6), s, 0, 2, 3, 2, "accent")
    if drip:
        m.cube("body", 0, -2, 0, s - 3, 2, s - 3, "sub")


def floater(m, core=8, y=14, eyes=True, orbit=0, orbitr=9, orbitn=3, wings=0, wingh=6, tent=0, tentl=8, ring=False, halo=False, horns=0, shell=False):
    m.part("root", (0, y, 0), anim="bob", amp=1.0)
    m.part("body", (0, 0, 0), parent="root", anim="head")
    m.cube("body", 0, -core / 2, 0, core, core, core, "main", feat="eyes" if eyes else None)
    m.cube("body", 0, -core / 2 + 1, 0, core + 2, core - 2, core - 2, "sub", infl=0, feat="runes")
    if shell:
        for s in (-1, 1):
            m.cube("body", s * (core / 2 + 1), -core / 2 + 1, 0, 2, core - 2, core - 2, "accent")
    if horns:
        for s in (-1, 1):
            m.cube("body", s * core / 3, core / 2 - 1, 0, 2, horns, 2, "bone")
    if halo:
        m.part("halo", (0, core / 2 + 4, 0), parent="root", anim="spin")
        m.cube("halo", 0, 0, 0, core + 2, 1, core + 2, "glow", feat="core")
    for i in range(orbit):
        ang = math.tau * i / orbit
        nm = f"orb{i}"
        m.part(nm, (0, 0, 0), parent="root", anim="spin", amp=1.0, phase=ang)
        m.cube(nm, orbitr, -2, 0, 4, 4, 4, "accent" if ring else "glow", feat="core")
    if ring:
        m.part("ring", (0, 0, 0), parent="root", anim="spin", amp=-0.6)
        m.cube("ring", 0, -1, 0, orbitr * 2 + 4, 1, orbitr * 2 + 4, "accent", feat="runes")
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        if wings:
            m.part(nm, (side * core / 2, -core / 2 + 2, -1), parent="root", anim=nm, amp=1.2)
            m.cube(nm, side * wings / 2, 0, 0, wings, wingh, 1, "sub", feat="stripes")
    for i in range(tent):
        ang = math.tau * i / tent
        nm = f"tn{i}"
        m.part(nm, (math.cos(ang) * core / 3, -core + 1, math.sin(ang) * core / 3), parent="root", anim="tail", amp=0.6, phase=i)
        m.cube(nm, 0, -tentl, 0, 2, tentl, 2, "sub")


def golem(m, s=1.0, orbit=0, eyes=True, glow=True):
    k = s
    legh, tw, th, hs = int(12 * k), int(14 * k), int(14 * k), int(8 * k)
    biped(m, legh=legh, tw=tw, th=th, td=int(9 * k), hs=hs, aw=int(5 * k), ah=int(16 * k), longarm=int(4 * k), shoulder=int(4 * k),
          eyes=eyes, glowchest=glow, horns=int(3 * k) if orbit else 0)
    for i in range(orbit):
        nm = f"rock{i}"
        m.part(nm, (0, legh + th * 0.7, 0), anim="spin", amp=0.7, phase=math.tau * i / orbit)
        m.cube(nm, 11 * k, -2, 0, 5, 5, 5, "dark", feat="runes")


def dragon(m, s=1.0, wing=22, neck=3, tail=5, horns=3, eyes=True, flame=False, y=14):
    k = s
    m.part("root", (0, y, 0), anim="bob", amp=0.8)
    m.part("body", (0, 0, 0), parent="root")
    m.cube("body", 0, 0, 0, int(10 * k), int(9 * k), int(18 * k), "main", feat="stripes")
    m.cube("body", 0, 0, 0, int(8 * k), int(3 * k), int(16 * k), "sub", feat="belly", infl=0)
    prev = "body"
    for i in range(neck):
        nm = f"neck{i}"
        m.part(nm, (0, 5 * k, 9 * k if i == 0 else 4), parent=prev, anim="tail", amp=0.3, phase=i)
        m.cube(nm, 0, 0, 2, int(5 * k), int(5 * k), 5, "main")
        prev = nm
    m.part("head", (0, 1, 4), parent=prev, anim="head")
    m.cube("head", 0, 0, 2, int(7 * k), int(6 * k), int(7 * k), "light", feat="eyes" if eyes else None)
    m.cube("head", 0, 0, 7 * k / 2 + 2, int(5 * k), int(3 * k), int(5 * k), "sub")
    for sd in (-1, 1):
        for h in range(horns):
            m.cube("head", sd * (2 + h * 0.5), 5 * k + h * 1.5, -1 - h * 1.5, 2, 3, 2, "bone")
    prev = "body"
    for i in range(tail):
        nm = f"tl{i}"
        m.part(nm, (0, 4 if i == 0 else 0, -9 * k if i == 0 else -5), parent=prev, anim="tail", amp=0.6, phase=i * 0.7)
        w = max(2, int((8 - i * 1.4) * k))
        m.cube(nm, 0, -w / 2, -2.5, w, w, 6, "main" if i % 2 == 0 else "sub")
        prev = nm
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        m.part(nm, (side * 5 * k, 8 * k, 0), parent="body", anim=nm, amp=0.9)
        m.cube(nm, side * wing * k / 2, 0, 0, int(wing * k), 1, int(14 * k), "sub", feat="stripes")
        m.cube(nm, side * wing * k / 2, 0, -6 * k, int(wing * k), 2, 2, "accent")
    for fz in (5 * k, -5 * k):
        for side, nm in ((-1, "A"), (1, "B")):
            part = f"leg{'F' if fz > 0 else 'B'}{nm}"
            m.part(part, (side * 4 * k, 0, fz), parent="body", anim="legA" if nm == "A" else "legB", amp=0.4)
            m.cube(part, 0, -5, 0, 3, 5, 3, "dark")


def bird(m, y=6):
    m.part("body", (0, y, 0), anim="bob", amp=0.4)
    m.cube("body", 0, 0, 0, 5, 5, 7, "main", feat="belly")
    m.part("head", (0, y + 4, 2), anim="head")
    m.cube("head", 0, 0, 0, 4, 4, 4, "light", feat="eyes")
    m.cube("head", 0, 1, 3, 2, 2, 2, "accent")
    m.cube("head", 0, 4, -1, 2, 2, 3, "sub")
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        m.part(nm, (side * 2.5, y + 4, 0), anim=nm, amp=0.5, rest=-1.0)
        m.cube(nm, side * 3, -4, 0, 6, 5, 1, "sub", feat="stripes")
    m.part("tail", (0, y + 1, -3), anim="tail")
    m.cube("tail", 0, 0, -3, 3, 1, 6, "sub", feat="stripes")
    for side, nm in ((-1, "legA"), (1, "legB")):
        m.part(nm, (side * 1.2, y, 0), anim=nm, amp=0.5)
        m.cube(nm, 0, -y, 0, 1, y, 1, "accent")


def serpent(m, n=7, w=8, y=3, head=7, fins=False, crest=0):
    prev = None
    for i in range(n):
        nm = f"sg{i}"
        if prev is None:
            m.part(nm, (0, y, -n * 2), anim="tail", amp=0.5, phase=0)
            m.cube(nm, 0, 0, 0, w, w, 6, "main")
        else:
            m.part(nm, (0, 0, 5), parent=prev, anim="tail", amp=0.5, phase=i * 0.6)
            ww = max(3, w - (n - i) // 3 if False else w - abs(n - 1 - i) * 0)
            m.cube(nm, 0, 0, 0, ww, ww, 6, "main" if i % 2 == 0 else "sub", feat="stripes")
            if fins and i % 2 == 0:
                for sd in (-1, 1):
                    m.cube(nm, sd * (ww / 2 + 2), 1, 0, 4, 1, 5, "accent")
        prev = nm
    m.part("head", (0, 0, 5), parent=prev, anim="head")
    m.cube("head", 0, -1, 3, head, head, head + 2, "light", feat="eyes")
    for c in range(crest):
        m.cube("head", 0, head - 1, 1 - c * 2, 2, 3, 2, "accent")


# ---------------------------------------------------------------------------------------------
# 一覧

E = {"wind": 0x9BE8B4, "thunder": 0xF5E050, "fire": 0xFF6A2A, "water": 0x3E8BFF, "gravity": 0x8A5CD6, "dark": 0x3B2F4F, "light": 0xFFF6C8,
     "curse": 0x7A1E5A}


# ---------------------------------------------------------------------------------------------
# 使い魔: 進化段階(1〜4)ごとに形が変わる。stage=(a,b) の部品は、その段階のときだけ表示される。

def fam_wolf(m, ghost=False):
    quad(m, bl=13, bw=6, bh=6, lh=7, hs=6, snout=3, tail=8, mane=not ghost)
    # 段階2: 首輪と耳の房
    x = m.extra("head", 2, 9)
    m.cube(x, 0, -3, 1, 7, 2, 7, "accent", feat="runes")
    for sd in (-1, 1):
        m.cube(x, sd * 2, 4, 1, 1, 3, 1, "bone")
    # 段階3: 背の鎧板と牙、脚の紋様
    x = m.extra("body", 3, 9)
    m.cube(x, 0, 6, 0, 7, 2, 9, "accent", feat="runes")
    m.cube(x, 0, 7, 0, 5, 1, 5, "glow", feat="core")
    x = m.extra("head", 3, 9)
    for sd in (-1, 1):
        m.cube(x, sd * 1, -3, 5, 1, 2, 1, "bone")
    # 段階4: 角の冠・光る尻尾・周回する光
    x = m.extra("head", 4, 9)
    m.cube(x, 0, 5, 1, 2, 5, 2, "glow", feat="core")
    for sd in (-1, 1):
        m.cube(x, sd * 3, 4, 1, 1, 5, 1, "bone")
    x = m.extra("tail", 4, 9)
    m.cube(x, 0, -1, -9, 3, 3, 3, "glow", feat="core")
    m.part("orbit", (0, 14, 0), anim="spin", amp=1.2, stage=(4, 9))
    for i in range(3):
        a = math.tau * i / 3
        m.cube("orbit", math.cos(a) * 9, 0, math.sin(a) * 9, 2, 2, 2, "glow", feat="core")


def fam_slime(m):
    blob(m, s=10, spikes=0, drip=True, core=True)
    x = m.extra("body", 2, 9)
    for sx in (-3, 0, 3):
        m.cube(x, sx, 10, 0, 2, 3, 2, "accent")
    x = m.extra("body", 3, 9)
    for sx, sz in ((5, 1), (-5, -1), (1, 5)):
        m.cube(x, sx, 4, sz, 2, 2, 2, "accent", feat="core")
    for sx in (-4, 4):
        m.cube(x, sx, 10, 0, 2, 4, 2, "accent")
    x = m.extra("body", 4, 9)
    m.cube(x, 0, 12, 0, 8, 2, 8, "accent", feat="runes")
    for sx in (-3, 0, 3):
        m.cube(x, sx, 14, 0, 2, 3, 2, "glow", feat="core")
    m.part("orbit", (0, 5, 0), anim="spin", amp=1.0, stage=(4, 9))
    for i in range(4):
        a = math.tau * i / 4
        m.cube("orbit", math.cos(a) * 9, 0, math.sin(a) * 9, 2, 3, 2, "glow", feat="core")


def fam_fairy(m):
    biped(m, legh=3, tw=3, th=5, td=2, hs=5, aw=1, ah=4, wings=7, wingh=8, halo=False, eyes=True)
    m.part("wingL2", (-1.5, 6, -1), anim="wingL", amp=1.0, phase=0.6, stage=(2, 9))
    m.cube("wingL2", -3, -3, -0.5, 5, 4, 1, "sub")
    m.part("wingR2", (1.5, 6, -1), anim="wingR", amp=1.0, phase=0.6, stage=(2, 9))
    m.cube("wingR2", 3, -3, -0.5, 5, 4, 1, "sub")
    x = m.extra("body", 3, 9)
    m.cube(x, 0, -4, 0, 6, 4, 4, "accent", feat="stripes")
    x = m.extra("head", 3, 9)
    m.cube(x, 0, 5, 0, 7, 1, 7, "sub")
    m.cube(x, 0, 6, 0, 4, 4, 4, "sub")
    m.cube(x, 0, 10, 0, 1, 2, 1, "glow", feat="core")
    m.part("halo4", (0, 3 + 5 + 5 + 3, 0), anim="spin", stage=(4, 9))
    m.cube("halo4", 0, 0, 0, 8, 1, 8, "glow", feat="core")
    m.part("orbit", (0, 7, 0), anim="spin", amp=1.4, stage=(4, 9))
    for i in range(3):
        a = math.tau * i / 3
        m.cube("orbit", math.cos(a) * 6, 0, math.sin(a) * 6, 2, 2, 2, "glow", feat="core")


def fam_bird(m, fire=False):
    bird(m)
    x = m.extra("head", 2, 9)
    m.cube(x, 0, 4, -1, 1, 3, 2, "glow" if fire else "accent", feat="core" if fire else None)
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        x = m.extra(nm, 3, 9)
        m.cube(x, side * 8, -5, 0, 5, 4, 1, "glow" if fire else "accent", feat="core" if fire else "stripes")
        m.cube(x, side * 11, -3, 0, 3, 3, 1, "accent")
    x = m.extra("tail", 3, 9)
    m.cube(x, 0, 0, -8, 3, 1, 5, "accent", feat="stripes")
    x = m.extra("tail", 4, 9)
    for sd in (-2, 0, 2):
        m.cube(x, sd, 0, -9 - abs(sd), 1, 1, 5, "glow", feat="core")
    m.part("halo4", (0, 6 + 4 + 4 + 2, 2), anim="spin", stage=(4, 9))
    m.cube("halo4", 0, 0, 0, 7, 1, 7, "glow", feat="core")
    if fire:
        m.part("orbit", (0, 8, 0), anim="spin", amp=1.3, stage=(4, 9))
        for i in range(3):
            a = math.tau * i / 3
            m.cube("orbit", math.cos(a) * 8, 0, math.sin(a) * 8, 2, 3, 2, "glow", feat="core")


def fam_golem(m):
    golem(m, s=0.8, orbit=0, glow=False)
    x = m.extra("body", 2, 9)
    m.cube(x, 0, 4, -3, 4, 4, 1, "glow", feat="core")
    for side, nm in ((-1, "armA"), (1, "armB")):
        x = m.extra(nm, 2, 9)
        m.cube(x, 0, -1, 0, 7, 3, 7, "dark", feat="runes")
    x = m.extra("head", 3, 9)
    for sx in (-2, 0, 2):
        m.cube(x, sx, 6, 0, 2, 4, 2, "bone")
    x = m.extra("body", 3, 9)
    m.cube(x, 0, 11, 0, 11, 2, 7, "accent", feat="runes")
    m.part("orbit", (0, 9, 0), anim="spin", amp=0.8, stage=(4, 9))
    for i in range(4):
        a = math.tau * i / 4
        m.cube("orbit", math.cos(a) * 11, 0, math.sin(a) * 11, 4, 4, 4, "dark", feat="runes")
    x = m.extra("body", 4, 9)
    m.cube(x, 0, 8, -3, 6, 6, 1, "glow", feat="core")


def fam_elemental(m):
    floater(m, core=8, y=12, orbit=0, horns=0, eyes=True)
    x = m.extra("body", 2, 9)
    for s in (-1, 1):
        m.cube(x, s * 2, 4, 0, 2, 4, 2, "bone")
    for i in range(3):
        a = math.tau * i / 3
        nm = f"orb{i}"
        m.part(nm, (0, 0, 0), parent="root", anim="spin", phase=a, stage=(2 if i == 0 else 3, 9))
        m.cube(nm, 8, -2, 0, 4, 4, 4, "glow", feat="core")
    m.part("ring4", (0, 0, 0), parent="root", anim="spin", amp=-0.6, stage=(4, 9))
    m.cube("ring4", 0, -1, 0, 16, 1, 16, "accent", feat="runes")
    x = m.extra("body", 4, 9)
    m.cube(x, 0, 4, 0, 6, 2, 6, "glow", feat="core")


def fam_guardian(m):
    floater(m, core=8, y=10, shell=False, eyes=True)
    x = m.extra("body", 1, 2)
    for s in (-1, 1):
        m.cube(x, s * 5, -3, 0, 2, 6, 6, "accent")
    x = m.extra("body", 3, 9)
    for s in (-1, 1):
        m.cube(x, s * 5, -4, 0, 3, 8, 8, "accent", feat="runes")
    for sz in (-1, 1):
        m.cube(x, 0, -2, sz * 5, 6, 4, 2, "accent")
    x = m.extra("body", 2, 9)
    m.cube(x, 0, 4, 0, 4, 3, 4, "glow", feat="core")
    m.part("ring2", (0, 0, 0), parent="root", anim="spin", amp=-0.7, stage=(2, 9))
    m.cube("ring2", 0, -1, 0, 18, 1, 18, "accent", feat="runes")
    m.part("halo4", (0, 8, 0), parent="root", anim="spin", stage=(4, 9))
    m.cube("halo4", 0, 0, 0, 10, 1, 10, "glow", feat="core")
    for i in range(4):
        nm = f"orb{i}"
        m.part(nm, (0, 0, 0), parent="root", anim="spin", amp=1.1, phase=math.tau * i / 4, stage=(4, 9))
        m.cube(nm, 9, -2, 0, 3, 3, 3, "glow", feat="core")


def fam_dragon(m):
    dragon(m, s=1.1, wing=24, neck=3, tail=5, horns=0, y=26)
    x = m.extra("head", 2, 9)
    for sd in (-1, 1):
        m.cube(x, sd * 2, 6, -1, 2, 4, 2, "bone")
    x = m.extra("body", 3, 9)
    for i in range(4):
        m.cube(x, 0, 9, 6 - i * 4, 2, 4, 2, "accent")
    x = m.extra("head", 3, 9)
    for sd in (-1, 1):
        m.cube(x, sd * 3, 4, -3, 2, 3, 2, "bone")
    x = m.extra("body", 4, 9)
    m.cube(x, 0, 10, 0, 6, 2, 12, "glow", feat="core")
    x = m.extra("head", 4, 9)
    m.cube(x, 0, 6, 0, 6, 2, 6, "accent", feat="runes")
    for sx in (-2, 0, 2):
        m.cube(x, sx, 8, 0, 2, 4, 2, "glow", feat="core")
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        x = m.extra(nm, 3, 9)
        m.cube(x, side * 18, 0, 3, 14, 1, 8, "accent", feat="stripes")


def build_familiars(R):
    sys.path.insert(0, HERE)
    import gen_familiars
    gen_familiars.build(R)


def build_all():
    R = {}

    def reg(name, c1, c2, cg, builder, accent=None, **kw):
        m = Model(c1, c2, cg, accent)
        builder(m, **kw)
        R[name] = m

    # --- 使い魔 ---

    # --- 雑魚 ---
    reg("wind_sprite", E["wind"], 0xEFFFF4, 0x80FFC0, floater, core=6, y=12, wings=10, wingh=6, tent=0, eyes=True)
    reg("tornado_bat", 0x58A878, 0xB8F0D0, 0xA0FFC8, flyer, wing=16, wd=7, y=12)
    reg("spark_jelly", 0xF2D640, 0xFFF8B0, 0xFFFFA0, blob, s=10, spikes=6)
    reg("volt_rat", 0x706040, 0xF5E050, 0xFFFF60, quad, bl=10, bw=4, bh=4, lh=3, hs=4, snout=3, tail=10)
    reg("flame_imp", 0xC83A1E, 0xFFB030, 0xFFE070, biped, legh=8, tw=6, th=8, td=4, hs=7, aw=2, ah=8, horns=3, tailn=8, wings=7, wingh=6)
    reg("magma_hound", 0x5A2A22, 0xFF6A2A, 0xFFB030, quad, bl=20, bw=10, bh=10, lh=11, hs=9, snout=5, tail=10, spikes=4, horns=3)
    reg("tideling", 0x2E7FB8, 0x9CE0F0, 0x80F0FF, biped, legh=10, tw=7, th=11, td=4, hs=8, aw=2, ah=10, wings=0, tailn=8, crown=False, horns=0, mane=True)
    reg("kraken_spawn", 0x2A5CC8, 0xB0E0FF, 0xC0F0FF, floater, core=10, y=10, tent=6, tentl=10, eyes=True)
    reg("gravity_mimic", 0x6A4AB0, 0xC8A8FF, 0xE0C0FF, floater, core=10, y=5, orbit=3, orbitr=9, shell=True)
    reg("void_bat", 0x3A2C58, 0x8A6AD0, 0xD0A0FF, flyer, wing=16, wd=8, fang=True, y=12)
    reg("shadow_stalker", 0x1A1426, 0x4A3A70, 0xE070FF, biped, legh=18, tw=6, th=10, td=4, hs=7, aw=2, ah=14, longarm=8, horns=0)
    reg("nightmare_bat", 0x2A1A38, 0x7A3A8A, 0xFF70D0, flyer, wing=18, wd=8, fang=True, y=12)
    reg("lumin_sprite", 0xFFF0B8, 0xFFFFFF, 0xFFE070, floater, core=6, y=12, wings=12, wingh=7, halo=True)
    reg("holy_guardian", 0xE8DCA8, 0xB89850, 0xFFF080, golem, s=0.75, orbit=0)
    reg("cursed_puppet", 0x6A3A58, 0xB8A070, 0xFF50B0, biped, legh=12, tw=6, th=10, td=3, hs=7, aw=2, ah=11, longarm=3, eyes=True, hat=0)
    reg("jinx_moth", 0x7A5A8A, 0xD8B8E8, 0xFF80C0, floater, core=5, y=11, wings=14, wingh=9, tent=0, horns=3)

    # --- 中ボス ---
    reg("gale_harpy", 0x58B890, 0xEFFFF4, 0x80FFC0, biped, legh=10, tw=6, th=10, td=4, hs=7, aw=2, ah=10, wings=20, wingh=14, tailn=6)
    reg("thunder_wolf_alpha", 0x3C3C58, 0xF5E050, 0xFFFF60, quad, bl=22, bw=12, bh=12, lh=14, hs=10, snout=6, tail=12, mane=True, spikes=5, horns=4)
    reg("salamander_king", 0xC03A1E, 0xFFB030, 0xFFE070, biped, legh=6, tw=12, th=12, td=8, hs=10, aw=4, ah=10, crown=True, tailn=12, shoulder=3)
    reg("leviathan_juvenile", 0x2A5AA8, 0xB0E0FF, 0xC0F0FF, serpent, n=7, w=12, y=6, head=11, fins=True, crest=3)
    reg("orbit_golem", 0x6A4AB0, 0x40306A, 0xE0C0FF, golem, s=1.1, orbit=4)
    reg("abyss_executor", 0x1E1830, 0x5A4A8A, 0xE070FF, biped, legh=17, tw=8, th=12, td=5, hs=8, aw=3, ah=14, sword=True, cape=True, horns=3, hornrole="subdark")
    reg("holy_knight_remnant", 0xE8DCA8, 0xB89850, 0xFFF080, biped, legh=12, tw=8, th=12, td=5, hs=8, aw=3, ah=12, sword=True, halo=True, cape=True, shoulder=3)
    reg("curse_witch", 0x5A2A6A, 0xB05AB0, 0xFF50B0, biped, legh=14, tw=7, th=11, td=5, hs=8, aw=2, ah=10, robe=True, hat=8, eyes=True)

    # --- 属性超越ボス ---
    reg("zepharion", 0x58B890, 0xEFFFF4, 0x80FFC0, dragon, s=1.2, wing=30, neck=2, tail=6, horns=2, y=22)
    reg("gardonius", 0x3A3A5A, 0xF5E050, 0xFFFF60, biped, legh=14, tw=12, th=16, td=8, hs=10, aw=5, ah=14, longarm=3, shoulder=4, crown=True,
        glowchest=True, cape=True)
    reg("igdrum", 0xA02A18, 0xFFB030, 0xFFE070, dragon, s=1.7, wing=34, neck=3, tail=6, horns=3, flame=True, y=34)
    reg("melusine", 0x2A6ACC, 0xB0F0FF, 0xC0F0FF, biped, legh=12, tw=7, th=10, td=4, hs=8, aw=2, ah=10, snake=6, crown=True, mane=True)
    reg("singularis", 0x2A1A4A, 0x8A5CD6, 0xE0C0FF, floater, core=12, y=18, orbit=5, orbitr=14, ring=True, shell=True)
    reg("necrovant", 0x2A1A3A, 0x7A1E5A, 0xFF50B0, biped, legh=12, tw=8, th=13, td=5, hs=8, aw=3, ah=11, robe=True, horns=4, cape=True, hornrole="bone")
    reg("seraphiel", 0xFFF6C8, 0xFFFFFF, 0xFFE070, biped, legh=12, tw=7, th=11, td=4, hs=8, aw=2, ah=11, wings=24, wingh=18, halo=True, crown=False, sword=True)
    reg("omnis", 0x2A2036, 0x7A1E5A, 0xFF40A0, golem, s=1.5, orbit=8)
    reg("morodis", 0x3A1030, 0xA02060, 0xFF5090, quad, bl=26, bw=16, bh=16, lh=14, hs=12, snout=6, tail=12, spikes=6, horns=6, mane=True)
    build_familiars(R)
    return R


def main():
    R = build_all()
    for i, (name, m) in enumerate(sorted(R.items())):
        export(name, m, 100 + i)
    print("models:", len(R))


if __name__ == "__main__":
    main()
