# ロープを 0.6 ブロックずつ描画しながらフックまで進む（マクロ再帰）
execute positioned ^ ^ ^0.6 run particle minecraft:end_rod ~ ~ ~ 0 0 0 0 1 normal @a
scoreboard players remove #i hs.t 1
$execute positioned ^ ^ ^0.6 unless entity @e[tag=hs.tip,scores={hs.id=$(id)},distance=..0.8] if score #i hs.t matches 1.. run function hookshot:rope/step with storage hookshot:v
