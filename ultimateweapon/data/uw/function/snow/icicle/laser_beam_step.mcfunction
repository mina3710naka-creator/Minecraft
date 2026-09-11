# ビームの1ステップ（1ブロック、実行者＝プレイヤー）
particle minecraft:snowflake ^ ^ ^1 0.05 0.05 0.05 0 2 normal @a
particle minecraft:end_rod ^ ^ ^1 0.03 0.03 0.03 0 1 normal @a
particle minecraft:item minecraft:ice ^ ^ ^1 0.04 0.04 0.04 0 1 normal @a

execute if entity @e[tag=uw.icetgt,distance=..1.5] run return run function uw:snow/icicle/laser_beam_next
execute unless block ^ ^ ^1 #minecraft:replaceable run return run function uw:snow/icicle/laser_beam_next

scoreboard players remove @s uw.sub 1
execute if score @s uw.sub matches 1.. positioned ^ ^ ^1 run function uw:snow/icicle/laser_beam_step
execute unless score @s uw.sub matches 1.. run function uw:snow/icicle/laser_beam_next
