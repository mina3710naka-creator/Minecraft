# ============================================================
#  巣づくり弾の1ステップ（0.25ブロック）＋着弾判定
# ============================================================
execute positioned ^ ^ ^0.25 if entity @e[tag=!sw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:end_crystal,type=!minecraft:interaction,distance=..3] run return run function spiderweb:web/hit

execute unless block ^ ^ ^0.25 #spiderweb:passable run return run function spiderweb:web/hit

tp @s ^ ^ ^0.25
scoreboard players add @s sw.range 1

# 射程80ブロック（0.25 x 320）を超えたら失敗
execute if score @s sw.range matches 320.. run return run function spiderweb:web/miss

scoreboard players remove @s sw.sub 1
execute if score @s sw.sub matches 1.. at @s run function spiderweb:web/step
