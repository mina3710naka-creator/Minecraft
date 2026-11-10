"""変身した姿(8 種)の専用モデル。gen_models.py から呼ばれる。プレイヤーに重ねて描く『変身体』用で、人型(身長 約 1.9 ブロック)。"""
import math

from gen_models import Model, biped


def reg(R, name, c1, c2, cg, accent, style, fn):
    m = Model(c1, c2, cg, accent, style)
    fn(m)
    R[name] = m


def vampire(m):
    biped(m, legh=11, tw=8, th=12, td=5, hs=8, aw=3, ah=12, cape=True, wings=14, wingh=12, eyes=True, hornrole="bone")
    m.cube("head", 0, 1, 4, 6, 1, 1, "black")                       # 口
    for s in (-1, 1):
        m.cube("head", s * 1.5, 0, 4.2, 1, 2, 1, "bone")             # 牙
        m.cube("body", s * 2.5, 11, 0, 3, 3, 6, "sub")               # 立った襟
    m.cube("body", 0, 11, -2.5, 8, 4, 2, "sub")


def wolf(m):
    biped(m, legh=11, tw=9, th=12, td=6, hs=8, aw=4, ah=13, tailn=12, eyes=True, longarm=2)
    m.cube("head", 0, -1, 5, 4, 3, 4, "sub")                         # マズル
    m.cube("head", 0, 0, 7, 2, 1, 1, "black")
    for s in (-1, 1):
        m.cube("head", s * 3, 8, 0, 2, 4, 2, "dark")                # 耳
        m.cube("head", s * 1.5, -1, 6.5, 1, 2, 1, "bone")           # 牙
        m.cube("armA" if s < 0 else "armB", 0, -13, 2, 1, 3, 1, "bone")   # 爪
        m.cube("armA" if s < 0 else "armB", 1, -13, 2, 1, 3, 1, "bone")
    for i in range(5):
        m.cube("body", 0, 12, -3.5 + i * 0.1, 7 - i, 1, 1, "light")  # 背中の毛


def kong(m):
    biped(m, legh=9, tw=14, th=15, td=9, hs=9, aw=6, ah=14, longarm=6, shoulder=4, eyes=True, glowchest=False)
    m.cube("body", 0, 6, 4.5, 8, 7, 1, "sub")                       # 胸の毛
    m.cube("head", 0, 4, 4.5, 9, 2, 2, "dark")                      # 太いまゆ
    m.cube("head", 0, -1, 4.5, 6, 3, 2, "sub")                      # マズル
    for s in (-1, 1):
        m.cube("head", s * 4.8, 3, 0, 1, 3, 2, "sub")
    m.cube("armA", 0, -20, 0, 7, 3, 7, "dark")                      # 大きなこぶし
    m.cube("armB", 0, -20, 0, 7, 3, 7, "dark")


def dragonoid(m):
    biped(m, legh=11, tw=10, th=13, td=6, hs=8, aw=4, ah=13, wings=24, wingh=16, tailn=14, horns=5, shoulder=3, eyes=True, hornrole="bone")
    m.cube("head", 0, 0, 4.5, 5, 3, 4, "light")                      # 鼻づら
    for s in (-1, 1):
        m.cube("head", s * 4.5, 2, 0, 1, 4, 3, "accent")             # 耳のひれ
        m.cube("head", s * 1.5, -1, 6, 1, 2, 1, "bone")
    for i in range(5):
        m.cube("body", 0, 13, -2 - i * 0.2, 2, 2 + (i % 2), 1, "accent")   # 背びれ


def angel(m):
    biped(m, legh=3, tw=8, th=12, td=5, hs=8, aw=3, ah=12, robe=True, wings=22, wingh=18, halo=True, eyes=True, skirt=1)
    m.part("wing2L", (-2, 12, -3), anim="wingL", amp=1.0, phase=0.8)
    m.cube("wing2L", -9, -6, 0, 16, 10, 1, "sub", feat="stripes")
    m.part("wing2R", (2, 12, -3), anim="wingR", amp=1.0, phase=0.8)
    m.cube("wing2R", 9, -6, 0, 16, 10, 1, "sub", feat="stripes")
    m.cube("body", 0, 8, 2.7, 8, 2, 1, "accent")                     # 金の帯


def shadow(m):
    biped(m, legh=13, tw=7, th=12, td=4, hs=8, aw=3, ah=13, longarm=5, cape=True, eyes=True)
    m.cube("head", 0, 7, 0, 10, 3, 10, "black")                      # フード
    m.cube("head", 0, 2, -4.5, 10, 8, 2, "black")
    for i in range(4):
        m.cube("body", (i - 1.5) * 2, -3 - i, -2.5, 2, 6, 1, "black")  # すそのゆらめき


def golem(m):
    biped(m, legh=12, tw=13, th=14, td=8, aw=6, ah=15, longarm=4, shoulder=5, glowchest=True, eyes=True)
    m.cube("head", 0, 8, 0, 9, 2, 9, "dark")
    for s in (-1, 1):
        m.cube("armA" if s < 0 else "armB", 0, -18, 0, 8, 4, 8, "dark", feat="runes")


def thunder(m):
    biped(m, legh=11, tw=8, th=12, td=5, hs=8, aw=3, ah=12, horns=4, shoulder=2, eyes=True, hornrole="accent", glowchest=True)
    for i in range(5):
        m.cube("head", (i - 2) * 2, 8, 0, 1, 3 + (i % 2) * 2, 1, "glow", feat="core")   # 雷の冠
    m.part("orbit", (0, 12, 0), anim="spin", amp=2.0)
    for i in range(3):
        a = math.tau * i / 3
        m.cube("orbit", math.cos(a) * 9, 0, math.sin(a) * 9, 2, 6, 2, "glow", feat="core")


def build(R):
    reg(R, "form_vampire", 0xCFC8D6, 0x1C1024, 0xFF3030, 0x8A1018, None, vampire)
    reg(R, "form_wolf", 0x7A7F8C, 0xC8CCD8, 0xFFD040, 0x3A4A70, "fur", wolf)
    reg(R, "form_kong", 0x3A2A22, 0x8A6A52, 0xFF7030, 0x2A1810, "fur", kong)
    reg(R, "form_dragonoid", 0x7A1E18, 0xE0B050, 0xFFB030, 0x3A1010, "scale", dragonoid)
    reg(R, "form_angel", 0xF4F0E8, 0xFFFFFF, 0xFFE070, 0xE0B050, "feather", angel)
    reg(R, "form_shadow", 0x15101C, 0x2A1A38, 0xFF40FF, 0x602080, None, shadow)
    reg(R, "form_golem", 0x6C7284, 0x3A4254, 0x70F0FF, 0xB0A060, "stone", golem)
    reg(R, "form_thunder", 0xE8D840, 0x2A3A8A, 0x80F0FF, 0xFFFFFF, None, thunder)
