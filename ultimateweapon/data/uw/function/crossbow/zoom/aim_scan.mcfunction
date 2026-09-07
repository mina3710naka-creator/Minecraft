# ============================================================
#  照準スキャン: 視線方向へ3ブロックずつ進み、近くのMOBを探す
#  （実行者＝プレイヤー、実行位置は視線上を移動していく）
# ============================================================
execute if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.5,limit=1,sort=nearest] as @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.5,limit=1,sort=nearest] run function uw:crossbow/zoom/aim_lock

execute if entity @e[tag=uw.locktgt] run return 0
execute unless block ^ ^ ^3 #minecraft:replaceable run return 0

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. positioned ^ ^ ^3 run function uw:crossbow/zoom/aim_scan
