# ============================================================
#  レーザービームの1ステップ（3ブロック、実行者＝プレイヤー）
#  以前より粒子を大幅に増やし、命中時にはフラッシュを出して
#  「何も起きていないように見える」ことがないようにしている
# ============================================================
particle minecraft:electric_spark ^ ^ ^1.5 0.12 0.12 0.12 0.02 12 normal @a
particle minecraft:end_rod ^ ^ ^1.5 0.08 0.08 0.08 0.01 6 normal @a
particle minecraft:crit ^ ^ ^1.5 0.1 0.1 0.1 0 4 normal @a

execute if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.2] run particle minecraft:flash ^ ^ ^1.5 0 0 0 0 1 normal @a
damage @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.2] 6 minecraft:generic

execute unless block ^ ^ ^3 #minecraft:replaceable run return 0

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. positioned ^ ^ ^3 run function uw:crossbow/laser/step
