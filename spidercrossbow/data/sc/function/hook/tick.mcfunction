# 飛行中のフック（実行者＝防具立て / 実行位置＝防具立て）
# 1 ティックあたり 0.25 × 20 = 5 ブロック進む。白いエフェクトを残す
scoreboard players set @s sc.sub 20
function sc:hook/step

execute if entity @s at @s run particle minecraft:cloud ~ ~ ~ 0.03 0.03 0.03 0.001 2 normal @a
execute if entity @s at @s run particle minecraft:end_rod ~ ~ ~ 0.03 0.03 0.03 0 1 normal @a
