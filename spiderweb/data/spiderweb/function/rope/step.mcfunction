# ロープを0.6ブロックずつ描画しながら対象まで進む（マクロ再帰）
execute positioned ^ ^ ^0.6 run particle minecraft:end_rod ~ ~ ~ 0 0 0 0 1 normal @a
scoreboard players remove #i sw.t 1
$execute positioned ^ ^ ^0.6 unless entity @e[tag=sw.tip,scores={sw.id=$(id)},distance=..0.8] if score #i sw.t matches 1.. run function spiderweb:rope/step with storage spiderweb:v
