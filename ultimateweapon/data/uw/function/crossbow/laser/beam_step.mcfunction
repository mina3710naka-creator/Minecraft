# ============================================================
#  ビームの1ステップ（2ブロック、実行者＝プレイヤー、視点はこうもり方向に固定）
# ============================================================
particle minecraft:electric_spark ^ ^ ^2 0.12 0.12 0.12 0.02 10 normal @a
particle minecraft:end_rod ^ ^ ^2 0.08 0.08 0.08 0.01 5 normal @a

execute as @e[tag=!uw.ent,tag=!uw.laserhit,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2] at @s run function uw:crossbow/laser/hit_one

execute if entity @e[tag=uw.laserbat,distance=..2.5] run return 0

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. positioned ^ ^ ^2 run function uw:crossbow/laser/beam_step
