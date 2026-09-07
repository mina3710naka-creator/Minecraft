# ============================================================
#  つららの雨（実行者＝中心マーカー / 実行位置＝中心）
#  半径10ブロックに5秒間（100tick）、本物の鍾乳石（つらら）を
#  降らせる。バニラの落下する鍾乳石の仕様でMOBに着地ダメージが入る
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 100.. run function uw:util/quiet_kill
execute unless entity @s run return 0

particle minecraft:dripping_water ~ ~6 ~ 4 2 4 0 6 normal @a
playsound minecraft:block.pointed_dripstone.drip_water player @a ~ ~ ~ 0.4 1

execute store result score #uw_r uw.sub run random value 1..3
execute if score #uw_r uw.sub matches 1 run function uw:snow/icicle/spawn_drip
