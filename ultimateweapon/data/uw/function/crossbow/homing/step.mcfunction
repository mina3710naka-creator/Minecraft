# ============================================================
#  自動追尾弾の1ステップ（1ブロック）＋着弾判定（実行者＝マーカー）
# ============================================================
execute positioned ^ ^ ^1 if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..1.2] run return run function uw:crossbow/homing/hit

execute unless block ^ ^ ^1 #minecraft:replaceable run return run function uw:util/quiet_kill

tp @s ^ ^ ^1
particle minecraft:crit ~ ~ ~ 0 0 0 0 1 normal @a
particle minecraft:end_rod ~ ~ ~ 0 0 0 0 1 normal @a

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. at @s run function uw:crossbow/homing/step
