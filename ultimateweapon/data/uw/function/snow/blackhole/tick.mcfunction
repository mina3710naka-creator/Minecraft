# ============================================================
#  ブラックホールの毎ティック処理（実行者＝中心マーカー / 実行位置＝中心）
#  半径10ブロック以内を吸い込み、5秒（100tick）で崩壊する
#  ※ 存在している間ずっとエフェクトが途切れず再生され続けるよう、
#    毎tick確実にパーティクル・環境音を鳴らす（間引かない）
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 100.. run function uw:snow/blackhole/collapse
execute unless entity @s run return 0

particle minecraft:portal ~ ~ ~ 0.4 0.4 0.4 0.5 30 normal @a
particle minecraft:reverse_portal ~ ~ ~ 1.4 1.4 1.4 0.2 15 normal @a
particle minecraft:smoke ~ ~ ~ 0.3 0.3 0.3 0.02 6 normal @a
particle minecraft:sculk_soul ~ ~ ~ 0.9 0.9 0.9 0.03 5 normal @a
particle minecraft:soul_fire_flame ~ ~ ~ 0.6 1 0.6 0.02 4 normal @a
particle minecraft:dust 0.4 0 0.7 1 ~ ~ ~ 0.6 0.6 0.6 0 10 normal @a
playsound minecraft:block.portal.ambient player @a ~ ~ ~ 0.5 0.5

execute as @e[tag=!uw.ent,type=!minecraft:player,distance=..10] at @s run function uw:snow/blackhole/pull_one
