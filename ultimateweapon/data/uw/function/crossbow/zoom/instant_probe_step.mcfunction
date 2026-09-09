# ============================================================
#  即時レイキャストの1ステップ（1ブロック、実行者＝こうもり）
#  同tick内で最大100ブロックまで再帰的に呼び出され、途中でMOBが
#  見つかるか壁に当たると探索を終える
# ============================================================
execute if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.5,limit=1,sort=nearest] as @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.5,limit=1,sort=nearest] run tag @s add uw.locktgt

execute if entity @e[tag=uw.locktgt] run return run function uw:crossbow/zoom/instant_probe_end
execute unless block ^ ^ ^1 #minecraft:replaceable run return run function uw:crossbow/zoom/instant_probe_end

tp @s ^ ^ ^1
scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. at @s run function uw:crossbow/zoom/instant_probe_step
execute unless score @s uw.sub matches 1.. run function uw:crossbow/zoom/instant_probe_end
