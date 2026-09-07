# 自分の近くに未処理の雪玉がいないか確認する（実行者＝プレイヤー）
execute unless entity @e[type=minecraft:snowball,tag=!uw.seen,distance=..2] run return 0

tag @e[type=minecraft:snowball,tag=!uw.seen,distance=..2,limit=1,sort=nearest] add uw.seen
kill @e[type=minecraft:snowball,tag=uw.seen,distance=..2,limit=1,sort=nearest]
function uw:snow/blackhole/launch
