# ============================================================
#  視点方向のロックオン対象を探す（3ブロックずつ、実行者＝プレイヤー）
# ============================================================
execute if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.5,limit=1,sort=nearest] as @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.5,limit=1,sort=nearest] run tag @s add uw.homingtgt

execute if entity @e[tag=uw.homingtgt] run return 0
execute unless block ^ ^ ^3 #minecraft:replaceable run return 0

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. positioned ^ ^ ^3 run function uw:crossbow/homing/scan_step
