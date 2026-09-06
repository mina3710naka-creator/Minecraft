# ============================================================
#  ジャンプ検知（実行者＝ブーツ装備プレイヤー、ジャンプした直後のtick）
# ============================================================

# [DEBUG] ジャンプ自体は検知できているかの確認用（不要になったら削除可）
tellraw @s [{"text":"[DEBUG] ジャンプ検知 sneak=","color":"gray"},{"score":{"name":"@s","objective":"ob.sneak"}}]

# 演出中（滞空中）の多重発動を防ぐ
execute if entity @s[tag=ob.leaping] run return 0

# シフトを押していない通常のジャンプは何もしない
execute unless predicate opboots:sneaking run return 0

# シフトを3秒（60tick）以上押し続けてからのジャンプ＝爆発10マスジャンプ
# （"return run function"の組み合わせを避け、フラグを使った単純な
#   分岐にして切り分けている）
scoreboard players set @s ob.tmp 0
execute if score @s ob.sneak matches 60.. run scoreboard players set @s ob.tmp 1

# [DEBUG] フラグの値そのものを確認
tellraw @s [{"text":"[DEBUG] ob.tmp=","color":"light_purple"},{"score":{"name":"@s","objective":"ob.tmp"}}]

execute if score @s ob.tmp matches 1 run function opboots:jump/explosive
execute if score @s ob.tmp matches 1 run return 0

# それ以外のシフト+ジャンプ＝通常の10マスジャンプ
function opboots:jump/leap
