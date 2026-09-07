# ============================================================
#  MOB1体を凍結させる（実行者＝そのMOB）NoAIで行動不可にする
# ============================================================
tag @s add uw.frozen
scoreboard players set @s uw.t 0
data merge entity @s {NoAI:1b}
effect give @s minecraft:slowness 21 250 true
effect give @s minecraft:mining_fatigue 21 250 true
effect give @s minecraft:weakness 21 250 true
particle minecraft:snowflake ~ ~1 ~ 0.4 0.6 0.4 0.02 20 normal @a
