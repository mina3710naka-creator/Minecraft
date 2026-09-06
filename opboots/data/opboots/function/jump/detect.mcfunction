# ============================================================
#  ジャンプ検知（実行者＝ブーツ装備プレイヤー、ジャンプした直後のtick）
# ============================================================

# 演出中（滞空中）の多重発動を防ぐ
execute if entity @s[tag=ob.leaping] run return 0

# シフトを押していない通常のジャンプは何もしない
execute unless predicate opboots:sneaking run return 0

# シフトを3秒（60tick）以上押し続けてからのジャンプ＝爆発10マスジャンプ
execute if score @s ob.sneak matches 60.. run return run function opboots:jump/explosive

# それ以外のシフト+ジャンプ＝通常の10マスジャンプ
function opboots:jump/leap
