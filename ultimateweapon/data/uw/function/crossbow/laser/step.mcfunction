# ============================================================
#  レーザービームの1ステップ（3ブロック、実行者＝プレイヤー）
# ============================================================
particle minecraft:end_rod ^ ^ ^1.5 0.15 0.15 0.15 0 2 normal @a
particle minecraft:soul_fire_flame ^ ^ ^1.5 0.1 0.1 0.1 0 1 normal @a

damage @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.2] 6 minecraft:generic

execute unless block ^ ^ ^3 #minecraft:replaceable run return 0

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. positioned ^ ^ ^3 run function uw:crossbow/laser/step
