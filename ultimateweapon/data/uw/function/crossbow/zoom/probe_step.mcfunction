# 照準レイキャストの1ステップ（1ブロック、実行者＝こうもり）
execute if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.5,limit=1,sort=nearest] as @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.5,limit=1,sort=nearest] run function uw:crossbow/zoom/aim_lock

execute if entity @e[tag=uw.locktgt] run return 0
execute unless block ^ ^ ^1 #minecraft:replaceable run return 0

tp @s ^ ^ ^1
scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. at @s run function uw:crossbow/zoom/probe_step
