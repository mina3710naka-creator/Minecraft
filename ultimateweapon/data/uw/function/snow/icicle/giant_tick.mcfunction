# ============================================================
#  巨大な氷塊の落下（実行者＝ブロックディスプレイ / 実行位置＝その場）
#  1tickに1ブロック落下し、MOBに当たるか地面に着くと大ダメージを与えて消える
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 40.. run function uw:util/quiet_kill
execute unless entity @s run return 0

execute if entity @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,distance=..3] run function uw:snow/icicle/giant_hit
execute unless entity @s run return 0

execute unless block ~ ~-1 ~ #minecraft:replaceable run function uw:snow/icicle/giant_hit
execute unless entity @s run return 0

tp @s ~ ~-1 ~
particle minecraft:snowflake ~ ~ ~ 0.6 0.6 0.6 0 3 normal @a
particle minecraft:cloud ~ ~ ~ 0.6 0.6 0.6 0 2 normal @a
