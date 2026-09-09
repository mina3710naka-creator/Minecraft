# 特別な球の1ステップ（0.25ブロック）＋着弾判定（実行者＝雪玉）
execute positioned ^ ^ ^0.25 if block ~ ~ ~ minecraft:cobweb run return run function sc:ball/hit

execute unless block ^ ^ ^0.25 #sc:passable run return run function sc:ball/blocked

tp @s ^ ^ ^0.25
scoreboard players add @s sc.range 1

# 射程 60 ブロック（0.25 × 240）
execute if score @s sc.range matches 240.. run return run function sc:ball/miss

scoreboard players remove @s sc.sub 1
execute if score @s sc.sub matches 1.. at @s run function sc:ball/step
