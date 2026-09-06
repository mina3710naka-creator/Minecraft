# ============================================================
#  ジャンプ検知（実行者＝ブーツ装備プレイヤー、ジャンプした直後のtick）
# ============================================================

# [DEBUG] ジャンプ自体は検知できているかの確認用（不要になったら削除可）
tellraw @s [{"text":"[DEBUG] ジャンプ検知 sneak=","color":"gray"},{"score":{"name":"@s","objective":"ob.sneak"}}]

# 演出中（滞空中）の多重発動を防ぐ
execute if entity @s[tag=ob.leaping] run return 0

# [DEBUG] ob.leaping判定を通過したことの確認用
tellraw @s {"text":"[DEBUG] leaping判定OK（滞空中ではない）","color":"gray"}

# シフトを押していない通常のジャンプは何もしない
execute unless predicate opboots:sneaking run return 0

# [DEBUG] sneaking判定を通過したことの確認用
tellraw @s {"text":"[DEBUG] sneaking判定OK（シフト中）","color":"gray"}

# [DEBUG] 60以上の条件そのものがtrue/falseどちらと判定されているか
execute if score @s ob.sneak matches 60.. run tellraw @s {"text":"[DEBUG] 60+判定=true","color":"light_purple","bold":true}
execute unless score @s ob.sneak matches 60.. run tellraw @s {"text":"[DEBUG] 60+判定=false","color":"light_purple","bold":true}

# シフトを3秒（60tick）以上押し続けてからのジャンプ＝爆発10マスジャンプ
execute if score @s ob.sneak matches 60.. run return run function opboots:jump/explosive

# それ以外のシフト+ジャンプ＝通常の10マスジャンプ
function opboots:jump/leap
