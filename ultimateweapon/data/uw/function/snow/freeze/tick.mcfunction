# ============================================================
#  凍結中の毎ティック処理（実行者＝凍結中のMOB / 実行位置＝そのMOB）
#  20秒（400tick）でNoAIを解除する
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 400.. run function uw:snow/freeze/release
execute unless entity @s[tag=uw.frozen] run return 0

data merge entity @s {Motion:[0.0d,0.0d,0.0d]}
particle minecraft:snowflake ~ ~1 ~ 0.3 0.5 0.3 0.01 2 normal @a
