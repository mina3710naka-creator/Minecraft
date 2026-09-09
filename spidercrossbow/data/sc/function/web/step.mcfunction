# 巣づくり弾の1ステップ（0.25ブロック）＋着弾判定（実行者＝雪玉）
execute positioned ^ ^ ^0.25 if entity @e[tag=!sc.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:armor_stand,type=!minecraft:interaction,type=!minecraft:arrow,type=!minecraft:snowball,distance=..3] run return run function sc:web/hit

execute unless block ^ ^ ^0.25 #sc:passable run return run function sc:web/hit

tp @s ^ ^ ^0.25
scoreboard players add @s sc.range 1

# 射程 100 ブロック（0.25 × 400）
execute if score @s sc.range matches 400.. run return run function sc:web/miss

scoreboard players remove @s sc.sub 1
execute if score @s sc.sub matches 1.. at @s run function sc:web/step
