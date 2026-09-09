# ============================================================
#  ブラックホールの毎ティック処理（実行者＝中心マーカー / 実行位置＝中心）
#  半径10ブロック以内を吸い込み、5秒（100tick）で崩壊する
#  ※プレイヤーは吸い込まない（MOB・アイテム等のみ引き寄せる）
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 100.. run function uw:snow/blackhole/collapse
execute unless entity @s run return 0

particle minecraft:portal ~ ~ ~ 0.6 0.6 0.6 0.6 30 normal @a
particle minecraft:squid_ink ~ ~ ~ 0.5 0.5 0.5 0.3 4 normal @a
particle minecraft:smoke ~ ~ ~ 0.4 0.4 0.4 0.03 8 normal @a
particle minecraft:reverse_portal ~ ~ ~ 1.2 1.2 1.2 0.3 6 normal @a
playsound minecraft:block.portal.ambient player @a ~ ~ ~ 0.4 0.6

execute as @e[tag=!uw.ent,type=!minecraft:player,distance=..10] at @s run function uw:snow/blackhole/pull_one
