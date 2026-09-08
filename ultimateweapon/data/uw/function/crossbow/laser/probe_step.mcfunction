# レーザー終点の1ステップ（1ブロック、実行者＝こうもり）壁で止まる
execute unless block ^ ^ ^1 #minecraft:replaceable run return 0

tp @s ^ ^ ^1
scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. at @s run function uw:crossbow/laser/probe_step
