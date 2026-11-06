"""使い魔 10 種の作り込んだモデル(進化段階つき)。gen_models.py から呼ばれる。

stage=(a,b) の部品は進化段階 a〜b のときだけ表示される。m.extra(親, a, b) は親と同じ位置の追加部品。
"""
import math

from gen_models import Model


def orbit(m, name, y, n, r, size, role="glow", lo=1, hi=9, amp=1.0, parent=None, feat="core", tall=None):
    m.part(name, (0, y, 0), parent=parent, anim="spin", amp=amp, stage=(lo, hi))
    for i in range(n):
        a = math.tau * i / n
        m.cube(name, math.cos(a) * r, 0, math.sin(a) * r, size, tall or size, size, role, feat=feat)


def paws(m, names, lh, bw, bl, upper=5, paw=3, w=3):
    for (fz, side, nm, anim) in names:
        m.part(nm, (side * (bw / 2 - 0.5), lh, fz), anim=anim, amp=0.9)
        m.cube(nm, 0, -(upper), 0, w, upper + 1, w, "main")
        m.cube(nm, 0, -lh, 0.5, w, paw, w + 1, "dark")
        for cx in (-1, 1):
            m.cube(nm, cx * 0.8, -lh, w / 2 + 1.2, 1, 1, 1, "bone")


# ---------------------------------------------------------------------------------------------

def wolf(m, ghost=False):
    lh, bl, bw, bh = 8, 14, 6, 7
    m.part("body", (0, lh, 0))
    m.cube("body", 0, 0, 0, bw, bh, bl, "main", feat="spots")
    m.cube("body", 0, bh - 1, -1, 3, 2, bl - 3, "dark")                      # 背のすじ
    m.cube("body", 0, 0, bl / 2 - 0.5, bw - 1, 5, 2, "sub")                   # 胸の白い毛
    m.cube("body", 0, bh - 2, bl / 2 - 3, bw + 2, 4, 5, "light")             # 肩の毛だまり
    m.cube("body", 0, 1, -bl / 2 + 2, bw + 1, 4, 4, "light")                 # 腰
    names = [(bl / 2 - 2, -1, "legFA", "legA"), (bl / 2 - 2, 1, "legFB", "legB"), (-bl / 2 + 2, -1, "legBB", "legB"),
             (-bl / 2 + 2, 1, "legBA", "legA")]
    paws(m, names, lh, bw, bl)
    m.part("head", (0, lh + bh - 1, bl / 2 + 1), anim="head")
    m.cube("head", 0, -3, 1, 7, 7, 6, "light", feat="eyes")                  # 頭
    m.cube("head", 0, -3, 5.5, 4, 3, 5, "sub")                               # マズル
    m.cube("head", 0, -0.5, 8, 2, 2, 1, "black")                             # 鼻
    m.cube("head", 0, -4, 6, 3, 1, 4, "dark")                                # 下あご
    for s in (-1, 1):
        m.cube("head", s * 2.5, 3, 0, 2, 4, 1, "dark")                       # 耳
        m.cube("head", s * 2.5, 3, 0.6, 1, 2, 1, "accent")                   # 耳の内側
        m.cube("head", s * 3.2, -2, 2, 1, 2, 3, "light")                     # ほおの毛
        m.cube("head", s * 1.8, 2.5, 3.6, 2, 1, 1, "dark")                   # まゆ
        m.cube("head", s * 1, -4.5, 7, 1, 2, 1, "bone")                      # 牙
    m.part("tail", (0, lh + bh - 3, -bl / 2), anim="tail", amp=1.0)
    m.cube("tail", 0, -1, -2, 2, 3, 4, "main")
    m.cube("tail", 0, -2, -5, 3, 5, 4, "light")
    m.cube("tail", 0, -1, -8, 2, 4, 3, "sub")
    # 段階2: 首輪・耳飾り
    x = m.extra("head", 2, 9)
    m.cube(x, 0, -5, 0, 8, 2, 7, "accent", feat="runes")
    m.cube(x, 0, -6, 0, 3, 3, 1, "glow", feat="core")
    # 段階3: 背の鎧・肩当て・脚の紋様
    x = m.extra("body", 3, 9)
    m.cube(x, 0, bh, 0, 7, 2, 10, "accent", feat="runes")
    m.cube(x, 0, bh + 1, 1, 5, 1, 6, "glow", feat="core")
    for s in (-1, 1):
        m.cube(x, s * 4, bh - 2, bl / 2 - 3, 2, 3, 4, "accent")
    for nm in ("legFA", "legFB", "legBA", "legBB"):
        x = m.extra(nm, 3, 9)
        m.cube(x, 0, -4, 0, 4, 1, 4, "glow", feat="core")
    # 段階4: 角の冠・光る尾・周回する光
    x = m.extra("head", 4, 9)
    m.cube(x, 0, 4, 1, 2, 5, 2, "glow", feat="core")
    for s in (-1, 1):
        m.cube(x, s * 3, 4, 0, 1, 6, 1, "bone")
        m.cube(x, s * 3.5, 9, 0, 1, 2, 1, "glow", feat="core")
    x = m.extra("tail", 4, 9)
    m.cube(x, 0, -1, -11, 3, 4, 3, "glow", feat="core")
    orbit(m, "orbit", lh + bh + 6, 3, 9, 2, lo=4, amp=1.2)


def slime(m):
    s = 10
    m.part("body", (0, 0, 0), anim="pulse", amp=1.2)
    m.cube("body", 0, 0, 0, s, s - 1, s, "main", feat="eyes")
    m.cube("body", 0, 1, 0, s - 3, s - 4, s - 3, "glow", feat="core", infl=-0.2)       # 核(透けて見える)
    m.cube("body", 0, -1, 0, s + 2, 2, s + 2, "sub")                                     # 裾の広がり
    for sx, sz in ((4, 3), (-4, -3), (3, -4)):
        m.cube("body", sx, -3, sz, 2, 3, 2, "sub")                                       # したたり
    for sd in (-1, 1):
        m.cube("body", sd * 5.5, 2, 1, 2, 4, 2, "main")                                  # 腕のこぶ
    m.cube("body", 0, 3, s / 2 + 0.5, 3, 1, 1, "dark")                                   # 口
    x = m.extra("body", 2, 9)
    for sx in (-3, 0, 3):
        m.cube(x, sx, s - 1, 0, 2, 3 + (1 if sx == 0 else 0), 2, "accent")
    x = m.extra("body", 3, 9)
    for sx, sz in ((5.5, 3), (-5.5, -3), (2, 5.5), (-3, 5.5)):
        m.cube(x, sx, 3, sz, 2, 2, 2, "accent", feat="core")
    for sd in (-1, 1):
        m.cube(x, sd * 5.5, 6, 1, 2, 4, 2, "accent")
    x = m.extra("body", 4, 9)
    m.cube(x, 0, s + 1, 0, 8, 2, 8, "accent", feat="runes")
    for sx in (-3, 0, 3):
        m.cube(x, sx, s + 3, 0, 2, 3, 2, "glow", feat="core")
    orbit(m, "orbit", 6, 4, 9, 2, lo=4, tall=3)


def fairy(m):
    m.part("body", (0, 6, 0))
    m.cube("body", 0, 0, 0, 3, 5, 2, "main", feat="stripes")
    m.cube("body", 0, -1, 0, 5, 3, 3, "sub")                                              # スカート
    m.cube("body", 0, -3, 0, 6, 2, 4, "sub", feat="stripes")
    for s in (-1, 1):
        m.part(f"leg{s}", (s * 0.8, 3, 0), anim="legA" if s < 0 else "legB", amp=0.5)
        m.cube(f"leg{s}", 0, -3, 0, 1, 3, 1, "light")
        m.cube(f"leg{s}", 0, -3, 0.5, 1, 1, 2, "accent")                                  # 靴
    m.part("head", (0, 11, 0), anim="head")
    m.cube("head", 0, 0, 0, 5, 5, 5, "light", feat="eyes")
    m.cube("head", 0, 2, -2.5, 6, 4, 2, "accent")                                          # 髪(後ろ)
    m.cube("head", 0, 4, 0, 6, 2, 6, "accent")                                             # 髪(上)
    m.cube("head", 0, -3, -3, 2, 4, 2, "accent")                                           # 毛先
    for s in (-1, 1):
        m.cube("head", s * 3, 1, 0, 1, 2, 1, "light")                                      # とがった耳
    for s in (-1, 1):
        m.part(f"arm{s}", (s * 2.5, 10.5, 0), anim="armA" if s < 0 else "armB", amp=0.8)
        m.cube(f"arm{s}", 0, -3, 0, 1, 4, 1, "light")
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        m.part(nm, (side * 1.5, 9, -1.5), anim=nm, amp=1.3)
        m.cube(nm, side * 4, -2, 0, 7, 6, 1, "sub", feat="stripes")
        m.cube(nm, side * 3, 3, 0, 5, 3, 1, "sub")
        m.cube(nm, side * 6.5, -2, 0, 1, 6, 1, "accent")
    m.part("wingL2", (-1.5, 8, -1.5), anim="wingL", amp=1.3, phase=0.7, stage=(2, 9))
    m.cube("wingL2", -3, -4, 0, 5, 4, 1, "sub")
    m.part("wingR2", (1.5, 8, -1.5), anim="wingR", amp=1.3, phase=0.7, stage=(2, 9))
    m.cube("wingR2", 3, -4, 0, 5, 4, 1, "sub")
    x = m.extra("arm1", 2, 9)
    m.cube(x, 0, -5, 0, 1, 5, 1, "bone")
    m.cube(x, 0, 0, 0, 3, 3, 3, "glow", feat="core")                                       # 杖の星
    x = m.extra("head", 3, 9)
    m.cube(x, 0, 5, 0, 8, 1, 8, "sub")
    m.cube(x, 0, 6, 0, 5, 3, 5, "sub")
    m.cube(x, 0, 9, 0, 2, 3, 2, "sub")
    m.cube(x, 0, 12, 0, 2, 2, 2, "glow", feat="core")
    x = m.extra("body", 3, 9)
    m.cube(x, 0, 2, 0, 4, 2, 3, "accent", feat="runes")
    m.part("halo4", (0, 18, 0), anim="spin", stage=(4, 9))
    m.cube("halo4", 0, 0, 0, 9, 1, 9, "glow", feat="core")
    orbit(m, "orbit", 9, 4, 7, 2, lo=4, amp=1.5)


def bird(m, fire=False):
    y = 7
    m.part("body", (0, y, 0), anim="bob", amp=0.4)
    m.cube("body", 0, 0, 0, 5, 5, 8, "main", feat="belly")
    m.cube("body", 0, 4, 0, 4, 1, 7, "dark")                                               # 背
    m.cube("body", 0, 0, 3.5, 4, 4, 1, "sub")                                              # 胸の羽
    m.part("head", (0, y + 4, 3), anim="head")
    m.cube("head", 0, 0, 0, 4, 4, 4, "light", feat="eyes")
    m.cube("head", 0, 0.5, 3, 2, 2, 2, "accent")                                           # くちばし
    m.cube("head", 0, -0.5, 3.5, 1, 1, 2, "accent")                                        # かぎ爪
    for s in (-1, 1):
        m.cube("head", s * 2.2, 0.5, 0, 1, 3, 3, "sub")                                    # ほおの羽
    m.cube("head", 0, 4, -1, 2, 2, 3, "dark")                                              # 冠羽
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        m.part(nm, (side * 2.5, y + 4, 0), anim=nm, amp=0.5, rest=-1.0)
        m.cube(nm, side * 2, -5, 0, 4, 6, 1, "main")
        m.cube(nm, side * 5, -6, 0, 3, 7, 1, "sub", feat="stripes")
        m.cube(nm, side * 7.5, -7, 0, 2, 8, 1, "accent")
    m.part("tail", (0, y + 1, -4), anim="tail")
    for i, (w, ln) in enumerate(((2, 5), (4, 7), (2, 5))):
        m.cube("tail", (i - 1) * 1.5, 0, -ln / 2, w, 1, ln, "sub" if i != 1 else "accent", feat="stripes")
    for side, nm in ((-1, "legA"), (1, "legB")):
        m.part(nm, (side * 1.3, y, 0), anim=nm, amp=0.5)
        m.cube(nm, 0, -y, 0, 1, y, 1, "accent")
        m.cube(nm, 0, -y, 1, 3, 1, 3, "accent")
    # 段階2: 冠羽が伸びる
    x = m.extra("head", 2, 9)
    for i in range(3):
        m.cube(x, 0, 4 + i, -2 - i, 1, 3, 1, "glow" if fire else "accent", feat="core" if fire else None)
    # 段階3: 大きな風切羽
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        x = m.extra(nm, 3, 9)
        m.cube(x, side * 10, -8, 0, 3, 9, 1, "glow" if fire else "accent", feat="core" if fire else "stripes")
        m.cube(x, side * 12.5, -9, 0, 2, 7, 1, "accent")
    x = m.extra("tail", 3, 9)
    m.cube(x, 0, 0, -9, 3, 1, 5, "accent", feat="stripes")
    # 段階4: 長い尾羽・光輪・周回する炎/光
    x = m.extra("tail", 4, 9)
    for sd in (-3, 0, 3):
        m.cube(x, sd, 0, -11 - abs(sd), 1, 1, 6, "glow", feat="core")
    m.part("halo4", (0, y + 10, 3), anim="spin", stage=(4, 9))
    m.cube("halo4", 0, 0, 0, 8, 1, 8, "glow", feat="core")
    orbit(m, "orbit", y + 3, 3, 9, 2, lo=4, amp=1.3, tall=3)


def golem(m):
    m.part("legA", (-3, 9, 0), anim="legA", amp=0.8)
    m.cube("legA", 0, -9, 0, 4, 9, 4, "dark")
    m.cube("legA", 0, -9, 0.5, 5, 3, 6, "main")                                            # 足
    m.part("legB", (3, 9, 0), anim="legB", amp=0.8)
    m.cube("legB", 0, -9, 0, 4, 9, 4, "dark")
    m.cube("legB", 0, -9, 0.5, 5, 3, 6, "main")
    m.part("body", (0, 9, 0))
    m.cube("body", 0, 0, 0, 10, 11, 6, "main")
    m.cube("body", 0, 8, 0, 12, 3, 7, "dark")                                              # 肩帯
    m.cube("body", 0, 0, 0, 11, 2, 7, "accent", feat="runes")                              # ベルト
    m.cube("body", 0, 3, -3, 6, 6, 2, "dark")                                              # 背板
    m.cube("body", 0, 3, 3, 4, 5, 1, "sub")                                                # 胸の板
    for sx in (-3, 3):
        m.cube("body", sx, 10, -2, 2, 4, 2, "accent")                                      # 背の結晶
    m.part("head", (0, 20, 0), anim="head")
    m.cube("head", 0, 0, 0, 6, 6, 6, "light", feat="eyes")
    m.cube("head", 0, 5, 0, 8, 1, 7, "dark")                                               # 兜のひさし
    m.cube("head", 0, -1, 2, 2, 3, 2, "dark")                                              # 鼻
    for sd, nm, an in ((-1, "armA", "armA"), (1, "armB", "armB")):
        m.part(nm, (sd * 7, 19, 0), anim=an, amp=0.9)
        m.cube(nm, 0, -4, 0, 4, 9, 4, "main")
        m.cube(nm, 0, 0, 0, 6, 3, 6, "dark", feat="runes")                                 # 肩当て
        m.cube(nm, 0, -9, 0, 5, 5, 5, "dark")                                              # こぶし
        for cx in (-1.5, 0, 1.5):
            m.cube(nm, cx, -10, 2.5, 1, 1, 1, "bone")
    x = m.extra("body", 2, 9)
    m.cube(x, 0, 4, 3.5, 4, 4, 1, "glow", feat="core")                                     # 胸のコア
    x = m.extra("head", 3, 9)
    for sx in (-2, 0, 2):
        m.cube(x, sx, 6, 0, 2, 3 + (2 if sx == 0 else 0), 2, "bone")
    for nm in ("armA", "armB"):
        x = m.extra(nm, 3, 9)
        m.cube(x, 0, -3, 0, 5, 2, 5, "accent", feat="runes")
    x = m.extra("body", 4, 9)
    m.cube(x, 0, 4, 3.5, 6, 6, 1, "glow", feat="core")
    for sx in (-4, 4):
        m.cube(x, sx, 11, -2, 2, 6, 2, "glow", feat="core")
    orbit(m, "orbit", 14, 4, 11, 4, "dark", lo=4, amp=0.8, feat="runes")


def elemental(m):
    m.part("root", (0, 12, 0), anim="bob", amp=1.0)
    m.part("body", (0, 0, 0), parent="root", anim="head")
    m.cube("body", 0, -4, 0, 7, 7, 7, "main", feat="eyes")
    m.cube("body", 0, -3, 0, 9, 5, 5, "sub", feat="runes")
    m.cube("body", 0, -6, 0, 5, 3, 5, "accent")                                            # 下の炎の根元
    for i in range(3):                                                                      # 頭の炎
        m.cube("body", (i - 1) * 2, 3, 0, 2, 3 + (2 if i == 1 else 0), 2, "glow", feat="core")
    for s in (-1, 1):                                                                       # 炎の腕
        m.part(f"flame{s}", (s * 5, -1, 0), parent="root", anim="armA" if s < 0 else "armB", amp=0.5)
        m.cube(f"flame{s}", 0, -3, 0, 2, 5, 2, "sub")
        m.cube(f"flame{s}", 0, -5, 0, 3, 2, 3, "glow", feat="core")
    for i in range(3):                                                                      # 炎の尾
        m.part(f"fl{i}", (0, -6 - i * 2, 0), parent="root", anim="tail", amp=0.5, phase=i)
        m.cube(f"fl{i}", 0, -2, 0, 5 - i, 2, 5 - i, "sub" if i % 2 else "accent")
    x = m.extra("body", 2, 9)
    for s in (-1, 1):
        m.cube(x, s * 3, 3, 0, 2, 5, 2, "bone")
    for i in range(3):
        a = math.tau * i / 3
        nm = f"orb{i}"
        m.part(nm, (0, 0, 0), parent="root", anim="spin", phase=a, stage=(2 if i == 0 else 3, 9))
        m.cube(nm, 8, -2, 0, 4, 4, 4, "glow", feat="core")
    m.part("ring4", (0, -2, 0), parent="root", anim="spin", amp=-0.6, stage=(4, 9))
    m.cube("ring4", 0, 0, 0, 16, 1, 16, "accent", feat="runes")
    x = m.extra("body", 4, 9)
    m.cube(x, 0, 4, 0, 6, 2, 6, "glow", feat="core")
    for s in (-1, 1):
        m.cube(x, s * 4, 4, 0, 1, 5, 1, "glow", feat="core")


def guardian(m):
    m.part("root", (0, 10, 0), anim="bob", amp=1.0)
    m.part("body", (0, 0, 0), parent="root", anim="head")
    m.cube("body", 0, -4, 0, 8, 8, 8, "main", feat="eye1")
    m.cube("body", 0, -5, 0, 10, 6, 6, "sub", feat="runes")
    for sz in (-1, 1):
        m.cube("body", 0, -3, sz * 5, 6, 6, 2, "accent")                                   # 前後の甲板
    for s in (-1, 1):
        m.cube("body", s * 5.5, -4, 0, 2, 8, 6, "accent")                                  # 側板
        m.cube("body", s * 6.5, -1, 0, 1, 3, 3, "sub")
    for sx in (-2, 2):
        m.cube("body", sx, 4, 0, 2, 3, 2, "sub")                                           # 背のとげ
    x = m.extra("body", 2, 9)
    m.cube(x, 0, 4, 0, 4, 3, 4, "glow", feat="core")
    m.part("ring2", (0, 0, 0), parent="root", anim="spin", amp=-0.7, stage=(2, 9))
    m.cube("ring2", 0, -1, 0, 16, 1, 16, "accent", feat="runes")
    x = m.extra("body", 3, 9)
    for s in (-1, 1):
        m.cube(x, s * 7, -5, 0, 2, 10, 8, "accent", feat="runes")
    for sz in (-1, 1):
        m.cube(x, 0, -2, sz * 6, 8, 5, 2, "accent")
    m.part("halo4", (0, 8, 0), parent="root", anim="spin", stage=(4, 9))
    m.cube("halo4", 0, 0, 0, 10, 1, 10, "glow", feat="core")
    for i in range(4):
        nm = f"orb{i}"
        m.part(nm, (0, 0, 0), parent="root", anim="spin", amp=1.1, phase=math.tau * i / 4, stage=(4, 9))
        m.cube(nm, 10, -2, 0, 3, 3, 3, "glow", feat="core")


def dragon(m):
    y = 26
    k = 1.1
    m.part("root", (0, y, 0), anim="bob", amp=0.8)
    m.part("body", (0, 0, 0), parent="root")
    m.cube("body", 0, 0, 0, 11, 10, 20, "main", feat="stripes")
    m.cube("body", 0, 0, 0, 9, 3, 18, "sub", feat="belly")                                  # 腹板
    for i in range(5):
        m.cube("body", 0, 10, 8 - i * 4, 2, 3, 2, "accent")                                 # 背びれ
    prev = "body"
    for i in range(3):                                                                       # 首
        nm = f"neck{i}"
        m.part(nm, (0, 5 + i, 10 if i == 0 else 5), parent=prev, anim="tail", amp=0.3, phase=i)
        m.cube(nm, 0, 0, 2, 6 - i, 6 - i, 6, "main")
        m.cube(nm, 0, 5 - i, 2, 2, 2, 3, "accent")
        prev = nm
    m.part("head", (0, 1, 4), parent=prev, anim="head")
    m.cube("head", 0, 0, 2, 8, 6, 7, "light", feat="eyes")
    m.cube("head", 0, 0, 7, 5, 3, 6, "sub")                                                  # 鼻づら
    m.cube("head", 0, -1.5, 7, 4, 2, 5, "dark")                                              # 下あご
    for sd in (-1, 1):
        m.cube("head", sd * 1.5, 1, 10.5, 1, 1, 1, "black")                                  # 鼻の穴
        m.cube("head", sd * 2, -0.5, 9, 1, 2, 1, "bone")                                     # 牙
        m.cube("head", sd * 3.5, 4, -1, 2, 3, 3, "dark")                                     # 耳びれ
    prev = "body"
    for i in range(5):                                                                       # 尾
        nm = f"tl{i}"
        m.part(nm, (0, 4 if i == 0 else 0, -10 if i == 0 else -5), parent=prev, anim="tail", amp=0.6, phase=i * 0.7)
        w = max(2, 8 - i * 1.4)
        m.cube(nm, 0, -w / 2, -2.5, w, w, 6, "main" if i % 2 == 0 else "sub")
        if i < 4:
            m.cube(nm, 0, w / 2, -2.5, 2, 2, 3, "accent")
        prev = nm
    m.cube("tl4", 0, -3, -7, 1, 6, 4, "bone")                                                # 尾の刃
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        m.part(nm, (side * 5.5, 8, 0), parent="body", anim=nm, amp=0.9)
        m.cube(nm, side * 3, 0, 0, 6, 3, 3, "main")                                          # 翼の腕
        m.cube(nm, side * 12, 0, 1, 14, 1, 14, "sub", feat="stripes")                         # 膜
        m.cube(nm, side * 12, 0, -5, 20, 2, 2, "accent")                                     # 前縁の骨
        for i in range(3):
            m.cube(nm, side * (8 + i * 6), 0, 3 - i * 3, 2, 1, 12 - i * 2, "bone")             # 指の骨
    for fz, tag in ((6, "F"), (-6, "B")):
        for side, nm in ((-1, "A"), (1, "B")):
            part = f"leg{tag}{nm}"
            m.part(part, (side * 4.5, 0, fz), parent="body", anim="legA" if nm == "A" else "legB", amp=0.4)
            m.cube(part, 0, -5, 0, 3, 5, 3, "dark")
            for cx in (-1, 0, 1):
                m.cube(part, cx, -6, 2, 1, 1, 2, "bone")
    x = m.extra("head", 2, 9)
    for sd in (-1, 1):
        m.cube(x, sd * 2, 6, -1, 2, 5, 2, "bone")
    x = m.extra("body", 3, 9)
    for i in range(4):
        m.cube(x, 0, 13, 6 - i * 4, 3, 4, 2, "accent")
    for sd in (-1, 1):
        m.cube(x, sd * 5, 8, 6, 3, 3, 5, "accent", feat="runes")
    x = m.extra("head", 3, 9)
    for sd in (-1, 1):
        m.cube(x, sd * 3, 6, -3, 2, 3, 3, "bone")
    x = m.extra("body", 4, 9)
    m.cube(x, 0, 10, 0, 6, 2, 14, "glow", feat="core")
    x = m.extra("head", 4, 9)
    m.cube(x, 0, 6, 0, 7, 2, 7, "accent", feat="runes")
    for sx in (-2, 0, 2):
        m.cube(x, sx, 8, 0, 2, 4, 2, "glow", feat="core")
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        x = m.extra(nm, 3, 9)
        m.cube(x, side * 20, 0, 3, 10, 1, 8, "accent", feat="stripes")
    orbit(m, "orbit", 6, 4, 14, 3, lo=4, parent="root", amp=0.9)



# ---------------------------------------------------------------------------------------------
# 細部の追加(大きくした分、小さな部品で密度を上げる)

def fine_wolf(m):
    bl, bh, bw = 14, 7, 6
    for i in range(6):                                           # 背のとげ
        m.cube("body", 0, bh + 1, bl / 2 - 3 - i * 2, 1, 2, 1, "dark")
    for i in range(4):                                           # わき腹の毛房
        for s in (-1, 1):
            m.cube("body", s * (bw / 2 + 0.5), 1 + (i % 2), bl / 2 - 3 - i * 3, 1, 3, 2, "light")
    for i in range(3):                                           # 胸の毛房
        m.cube("body", (i - 1) * 1.5, -1, bl / 2 + 0.5, 1, 3, 1, "sub")
    for nm in ("legFA", "legFB", "legBA", "legBB"):               # 脚の毛の輪
        m.cube(nm, 0, -4, 0, 4, 1, 4, "light")
        m.cube(nm, 0, -2, 0, 4, 1, 4, "light")
    for s in (-1, 1):                                            # ひげ
        for k in range(2):
            m.cube("head", s * 4, -2.5 - k, 5, 2, 1, 1, "bone")
        m.cube("head", s * 2, -1.5, 8, 1, 1, 1, "dark")           # 鼻すじ
    for k in range(4):
        m.cube("tail", (k % 2 - 0.5) * 2, 1 + (k % 3), -3 - k * 1.5, 1, 2, 1, "light")


def fine_slime(m):
    for sx, sy, sz in ((2, 4, 2), (-3, 5, -1), (1, 2, -3), (-1, 3, 3)):
        m.cube("body", sx, sy, sz, 2, 2, 2, "light")             # 中の泡
    for sd in (-1, 1):
        m.cube("body", sd * 2, 6, 5.2, 2, 1, 1, "dark")           # まゆ
        m.cube("body", sd * 3, 7, 5.2, 1, 1, 1, "light")          # ハイライト
    for i in range(3):
        m.cube("body", -1 + i, 2, 5.2, 1, 1, 1, "bone")           # 歯
    for sx, sz in ((-5, 2), (5, -2), (-2, -5), (3, 5)):
        m.cube("body", sx, 0, sz, 2, 2, 2, "sub")                 # 裾のしずく


def fine_fairy(m):
    for i in range(6):                                           # スカートのひだ
        a = math.tau * i / 6
        m.cube("body", math.cos(a) * 3, -4, math.sin(a) * 2.4, 1, 2, 1, "accent")
    for k in range(3):
        m.cube("head", -2 + k * 2, 0, -3.5, 1, 4 - k, 1, "accent")  # 髪すじ
    for nm, sd in (("wingL", -1), ("wingR", 1)):
        for k in range(3):
            m.cube(nm, sd * (2 + k * 2), -2 - k, 0.8, 1, 5 - k, 1, "accent")  # 翅の脈
    m.cube("head", 0, 3, 3, 2, 1, 1, "glow", feat="core")        # おでこの宝石


def fine_bird(m, fire):
    for r in range(3):                                           # 胸の羽の段
        for k in range(3):
            m.cube("body", (k - 1) * 1.6, 3 - r * 1.2, 3.8 + r * 0.2, 2, 1, 1, "sub" if r % 2 == 0 else "light")
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        for k in range(4):
            m.cube(nm, side * (3 + k * 2), -9 - k, 0.6, 1, 4, 1, "accent" if k % 2 else "sub")  # 風切羽
    for k in range(5):
        a = (k - 2) * 0.5
        m.cube("tail", math.sin(a) * 4, 0, -7 - math.cos(a) * 2, 1, 1, 4, "glow" if fire else "accent", feat="core" if fire else None)
    for nm in ("legA", "legB"):
        m.cube(nm, 0, -4, 0, 2, 1, 2, "accent")
        m.cube(nm, 0, -2, 0, 2, 1, 2, "accent")
    for k in range(5):
        a = (k - 2) * 0.45
        m.cube("head", math.sin(a) * 3, 4 + abs(k - 2) * -0.3, -1 - math.cos(a), 1, 3, 1, "glow" if fire else "dark",
               feat="core" if fire else None)


def fine_golem(m):
    for i in range(3):
        for s in (-1, 1):
            m.cube("body", s * 4, 1 + i * 3, 3.2, 1, 1, 1, "bone")      # リベット
    for s in (-1, 1):
        for k in range(3):
            m.cube("armA" if s < 0 else "armB", (k - 1) * 1.5, 1, 0, 1, 3, 1, "accent")  # 肩の結晶
        m.cube("legA" if s < 0 else "legB", 0, -6, 0.5, 5, 1, 5, "accent")             # すね当て
        m.cube("legA" if s < 0 else "legB", 0, -3, 0.5, 5, 1, 5, "accent")
    for k in range(3):
        m.cube("head", (k - 1) * 2, 6, 0, 1, 2, 1, "bone")
    for i in range(4):
        m.cube("body", -4.5 + i * 3, 11, -3.5, 2, 1, 1, "glow", feat="core")        # 背の通気口
    for sd in (-1, 1):
        m.cube("head", sd * 3.5, -1, 0, 1, 4, 3, "dark")                              # 頬当て


def fine_dragon(m):
    for i in range(6):                                           # 牙の列
        for s in (-1, 1):
            m.cube("head", s * 2.2, -1.6, 4 + i * 1.2, 1, 1, 1, "bone")
    for i in range(5):                                           # 腹板の線
        m.cube("body", 0, -0.5, 8 - i * 4, 8, 1, 1, "accent")
    for i in range(5):
        nm = f"tl{i}"
        for s in (-1, 1):
            m.cube(nm, s * (3.5 - i * 0.5), -1, -2.5, 1, 3, 2, "accent")                # 尾のとげ
    for side, nm in ((-1, "wingL"), (1, "wingR")):
        for k in range(3):
            m.cube(nm, side * (8 + k * 6), 1, 3 - k * 3 + 6 - 1 + (k - 1) * 0, 1, 2, 2, "bone")  # 指先の爪
        m.cube(nm, side * 4, 1, 0, 2, 2, 4, "dark")
    for fz, tag in ((6, "F"), (-6, "B")):
        for nm in ("A", "B"):
            m.cube(f"leg{tag}{nm}", 0, -2, 0, 4, 1, 4, "accent")                      # 足首の鱗
    for s in (-1, 1):
        m.cube("head", s * 3, 3, 3, 1, 1, 3, "accent")                                  # まゆの隆起
    for i in range(3):
        m.cube(f"neck{i}", 0, 0, 2, 7 - i, 1, 7, "accent")                              # 首の鱗の輪


def fine_guardian(m):
    for i in range(8):
        a = math.tau * i / 8
        m.cube("body", math.cos(a) * 5.2, -1 + (i % 2) * 2, math.sin(a) * 5.2, 1, 3, 1, "bone")  # とげ
    for sz in (-1, 1):
        for k in range(3):
            m.cube("body", (k - 1) * 2, -2, sz * 6.2, 1, 1, 1, "glow", feat="core")      # リベット
    for s in (-1, 1):
        m.cube("body", s * 5.8, -1, 0, 1, 1, 6, "glow", feat="core")


def fine_elemental(m):
    for i in range(8):
        a = math.tau * i / 8
        m.cube("body", math.cos(a) * 4.5, -1 + (i % 3) * 1.5, math.sin(a) * 4.5, 1, 3, 1, "glow", feat="core")  # 炎の舌
    for s in (-1, 1):
        m.cube("body", s * 1.8, -1, 3.8, 2, 1, 1, "black")                                # まゆ
    m.cube("body", 0, -4, 3.6, 2, 1, 1, "dark")


def fine_spirit(m):
    for i in range(5):
        m.cube("body", 0, 8 + (i % 2), 6 - i * 3, 1, 2, 1, "glow", feat="core")             # 背の光る毛


def build(R):
    def reg(name, c1, c2, cg, fn, accent, style, **kw):
        m = Model(c1, c2, cg, accent, style)
        fn(m, **kw)
        extra = {wolf: fine_wolf, slime: fine_slime, fairy: fine_fairy, golem: fine_golem, dragon: fine_dragon,
                 guardian: fine_guardian, elemental: fine_elemental}
        if fn in extra:
            extra[fn](m)
        elif fn is bird:
            fine_bird(m, kw.get("fire", False))
        if name == "familiar_spirit_wolf":
            fine_spirit(m)
        R[name] = m
    reg("familiar_wolf", 0x8C93A6, 0xE8EAF2, 0x60E0FF, wolf, 0x3A4A70, "fur")
    reg("familiar_spirit_wolf", 0x6AA8E8, 0xDDF2FF, 0xA0FFFF, wolf, 0x2A5AA0, "fur", ghost=True)
    reg("familiar_mud_slime", 0x7A5A2A, 0xC49A52, 0xFFE070, slime, 0xE0B840, "crystal")
    reg("familiar_fairy", 0xF0F4FF, 0x9CC8FF, 0xFFF080, fairy, 0xFFD060, "feather")
    reg("familiar_bird", 0xB89050, 0xFFE9A0, 0x70FF90, bird, 0x40A060, "feather")
    reg("familiar_phoenix", 0xD03A18, 0xFFB030, 0xFFE070, bird, 0xFF8A20, "feather", fire=True)
    reg("familiar_golem", 0x6C7284, 0x3A4254, 0x70F0FF, golem, 0xB0A060, "stone")
    reg("familiar_elemental", 0xD84A20, 0xFFB030, 0xFFE070, elemental, 0x701808, "crystal")
    reg("familiar_guardian", 0x40C8B8, 0xE8FFF8, 0x80FFF0, guardian, 0x1A6A70, "plate")
    reg("familiar_ancient_dragon", 0x3A9060, 0xE8E0A0, 0xFFE060, dragon, 0x8A3A20, "scale")
