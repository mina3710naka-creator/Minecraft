# ============================================================
#  花火の1ステップ（1ブロック、実行者＝マーカー）
# ============================================================
execute if entity @e[tag=uw.locktgt,distance=..1.5] run return run function uw:crossbow/zoom/firework_hit
execute if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..1.5] unless entity @e[tag=uw.locktgt] run return run function uw:crossbow/zoom/firework_hit

execute unless block ^ ^ ^1 #minecraft:replaceable run return run function uw:crossbow/zoom/firework_hit

tp @s ^ ^ ^1

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. at @s run function uw:crossbow/zoom/firework_step
