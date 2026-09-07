# 弾の1ステップ（1ブロック、実行者＝マーカー）
execute if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,distance=..1.2] run return run function uw:snow/icicle/hit
execute unless block ^ ^ ^1 #minecraft:replaceable run return run function uw:snow/icicle/hit

tp @s ^ ^ ^1
particle minecraft:item_snowball ~ ~ ~ 0 0 0 0 1 normal @a

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. at @s run function uw:snow/icicle/proj_step
