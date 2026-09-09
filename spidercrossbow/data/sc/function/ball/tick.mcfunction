# 一定速度（0.25 × 4 = 1.0ブロック/tick）でゆっくり飛ばし、見えるようにする
scoreboard players set @s sc.sub 4
function sc:ball/step

execute if entity @s at @s run particle minecraft:glow ~ ~ ~ 0.03 0.03 0.03 0 1 normal @a
