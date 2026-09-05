# ============================================================
#  解除（実行者＝プレイヤー）
# ============================================================
execute unless entity @s[tag=hs.active] run return 0

tag @s remove hs.active
tag @s remove hs.hooking
tag @s remove hs.pulling

particle minecraft:end_rod ~ ~1 ~ 0.3 0.4 0.3 0.03 14 normal @a
particle minecraft:happy_villager ~ ~1 ~ 0.3 0.4 0.3 0 6 normal @a
playsound minecraft:entity.leash_knot.break player @a ~ ~ ~ 0.7 1.4

# 自分の ID のフック・アンカー・ロープを片付ける
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
function hookshot:util/cleanup with storage hookshot:v

scoreboard players set @s hs.spd 0
scoreboard players set @s hs.pt 0
scoreboard players set @s hs.t 0

# 落下ダメージを無くしたい場合は次の行のコメント（#）を外す
# effect give @s minecraft:slow_falling 2 0 true
