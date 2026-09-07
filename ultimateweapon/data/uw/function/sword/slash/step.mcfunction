# 斬撃の1ステップ（4ブロック、実行者＝プレイヤー）
particle minecraft:crit ^ ^ ^2 0.3 0.3 0.3 0 3 normal @a
particle minecraft:sweep_attack ^ ^ ^2 0 0 0 0 1 normal @a

execute as @e[tag=!uw.ent,tag=!uw.slashhit,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2] at @s run function uw:sword/slash/hit_one

execute unless block ^ ^ ^4 #minecraft:replaceable run return 0

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. positioned ^ ^ ^4 run function uw:sword/slash/step
