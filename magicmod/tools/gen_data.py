#!/usr/bin/env python3
"""数値バランス表(xlsx)から、MOD が読み込む balance.json を作る。

使い方:
    python3 -I tools/gen_data.py design/魔法MOD_数値バランス_v3.xlsx src/main/resources/magicmod/balance.json

xlsx の数値を変えたら、これを実行し直して jar をビルドし直す。
(数式セルは Excel/LibreOffice で保存したときの計算結果を読むので、保存し直してから実行すること)
"""
import json
import re
import sys

import openpyxl

ELEMENTS = {"風": "wind", "電気": "thunder", "炎": "fire", "水": "water",
            "重力": "gravity", "闇": "dark", "光": "light", "呪い": "curse"}
CATEGORIES = {"直剣/斧/鈍器": "blade", "長柄武器": "polearm", "双数武器": "dual",
              "振り回し武器": "swing", "投擲武器": "throw", "弓・弩系": "bow", "大型武器": "heavy"}
RANKS = {"基礎": "basic", "中位": "middle", "上位": "upper", "最大": "max",
         "近接": "melee", "連打": "rapid", "投擲": "throw"}
ARCH = {"物理型": "physical", "魔法型": "magic", "均衡型": "balanced"}
TIERS = {"雑魚敵": "minion", "中ボス": "midboss", "属性超越ボス": "boss"}
CIRCLED = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬"


def num(no):
    return CIRCLED.index(no[0]) + 1


def rows(ws):
    for r in ws.iter_rows(values_only=True):
        if any(c is not None for c in r):
            yield r


def section(ws, title):
    """基準値テーブルの、見出し行 title の次の行(列名)から、空行または次の見出しまで。"""
    out, on, header = [], False, None
    for r in rows(ws):
        if r[0] == title:
            on = True
            continue
        if on:
            if header is None:
                header = r
                continue
            if r[0] is None or (r[1] is None and r[0] and not str(r[0]).startswith("※") and len(out) > 0 and r[2] is None):
                break
            if str(r[0]).startswith("※"):
                break
            out.append(r)
    return out


def main(src, dst):
    wb = openpyxl.load_workbook(src, data_only=True)
    base = wb["基準値テーブル"]
    data = {"source": src.split("/")[-1]}

    data["elements"] = {ELEMENTS[r[0]]: {"base": r[1], "note": r[2]} for r in section(base, "属性基準値(魔法用)")}
    data["spell_ranks"] = {RANKS[r[0]]: {"name": r[1], "coef": r[2], "mp": r[3], "ct": r[4]}
                           for r in section(base, "魔法ランク係数テーブル")}
    data["categories"] = {CATEGORIES[r[0]]: {"base": r[1], "name": r[0], "note": r[2]}
                          for r in section(base, "物理カテゴリ基準値")}
    data["tech_ranks"] = {RANKS[r[0]]: {"name": r[1], "coef": r[2], "stamina": r[3], "ct": r[4]}
                          for r in section(base, "物理わざランク係数テーブル")}
    data["archetypes"] = {ARCH[r[0]]: {"tech_power": r[1], "tech_ct": r[2], "stamina_max": r[3],
                                       "spell_power": r[4], "spell_ct": r[5], "mp_max": r[6]}
                          for r in section(base, "アーキタイプ補正テーブル")}
    data["enemy_scale"] = {TIERS[r[0]]: {"hp_mult": r[1], "defense": r[2]}
                           for r in section(base, "敵HPスケール係数テーブル")}
    res = {}
    keys = {"MP上限(基礎・均衡型)": "mp_max", "MP自然回復(/秒)": "mp_regen", "近接ヒット時MP回復(/ヒット)": "mp_on_hit",
            "魔封じの型ヒット時MP回復比": "seal_mp_ratio", "スタミナ上限(基礎・均衡型)": "stamina_max",
            "スタミナ自然回復(/秒)": "stamina_regen", "弱攻撃ダメージ係数(物理・スタミナ0)": "light_attack_coef",
            "弱攻撃クールタイム(秒)": "light_attack_ct", "弱点属性ダメージ倍率(魔法のみ)": "weak_mult",
            "耐性属性ダメージ倍率(魔法のみ)": "resist_mult"}
    for r in section(base, "リソース・戦闘基準値(新規追加)"):
        if r[0] in keys:
            res[keys[r[0]]] = r[1]
    data["resources"] = res

    spells = []
    for r in list(rows(wb["魔法一覧"]))[1:]:
        if r[0] not in ELEMENTS:
            continue
        name = re.sub(r"[((](最大|最上位)[))]", "", r[2])
        tag = ""
        m = re.search(r"【(.+?)】", name)
        if m:
            tag = m.group(1)
            name = name.replace(m.group(0), "")
        spells.append({"element": ELEMENTS[r[0]], "no": num(r[1]), "name": name, "tag": tag,
                       "rank": RANKS[r[3]], "status": r[4], "special": r[5],
                       "mp": r[11], "ct": r[12], "damage": r[14] == "Y"})
    data["spells"] = spells

    techs = []
    for r in list(rows(wb["物理わざ一覧"]))[1:]:
        if r[0] not in CATEGORIES:
            continue
        techs.append({"category": CATEGORIES[r[0]], "no": num(r[1]), "name": re.sub(r"[((](最大|最上位)[))]", "", r[2]),
                      "rank": RANKS[r[3]], "status": r[4], "special": r[5],
                      "stamina": r[11], "ct": r[12], "damage": r[14] == "Y"})
    data["techniques"] = techs

    fams = []
    for r in list(rows(wb["使い魔一覧"]))[1:]:
        if not isinstance(r[0], (int, float)):
            continue
        fams.append({"no": int(r[0]), "name": re.sub(r"[((](最大|最上位)[))]", "", r[1]), "role": r[2], "special": r[3],
                     "hp": r[4], "attack": r[5], "upkeep": r[6]})
    data["familiars"] = fams

    runes = []
    for r in list(rows(wb["秘術刻印一覧"]))[1:]:
        if not isinstance(r[0], (int, float)):
            continue
        runes.append({"no": int(r[0]), "name": re.sub(r"[((](最大|最上位)[))]", "", r[1]), "effect": r[2],
                      "value": r[3], "value_physical": r[4], "value_magic": r[5], "kind": r[6]})
    data["runes"] = runes

    enemies = []
    for r in list(rows(wb["敵一覧"]))[1:]:
        if r[0] not in TIERS:
            continue
        weak = r[8]
        m = re.search(r"、(\S+?)弱点", r[3] or "")
        if m and m.group(1) in ELEMENTS:
            weak = m.group(1)  # 設計書の個別設定(例:トルネードバット=電気弱点)を優先
        enemies.append({"tier": TIERS[r[0]], "element": ELEMENTS[r[1]], "name": r[2], "note": r[3],
                        "hp": r[6], "defense": r[7], "weak": ELEMENTS.get(weak, ""), "weak_mult": r[10]})
    data["enemies"] = enemies

    with open(dst, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print(f"spells={len(spells)} techniques={len(techs)} familiars={len(fams)} runes={len(runes)} enemies={len(enemies)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
