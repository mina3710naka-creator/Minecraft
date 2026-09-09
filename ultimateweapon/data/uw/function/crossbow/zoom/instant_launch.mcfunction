# ミサイル発射（実行者＝プレイヤー）
tag @s remove uw.instant_owner
playsound minecraft:entity.firework_rocket.launch player @a ~ ~ ~ 1 1
title @s actionbar {"text":"発射！","color":"gold","bold":true}

execute if entity @e[tag=uw.locktgt] run function uw:crossbow/zoom/instant_launch_locked
execute unless entity @e[tag=uw.locktgt] run function uw:crossbow/zoom/instant_launch_straight
