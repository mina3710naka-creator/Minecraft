# 自分の近くに未処理の矢がいないか確認する（実行者＝プレイヤー）
execute unless entity @e[type=minecraft:arrow,tag=!sc.seen,distance=..2] run return 0

tag @e[type=minecraft:arrow,tag=!sc.seen,distance=..2,limit=1,sort=nearest] add sc.seen
function sc:on_shot
