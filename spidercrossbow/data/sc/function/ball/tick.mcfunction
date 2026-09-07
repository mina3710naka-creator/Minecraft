scoreboard players set @s sc.sub 20
function sc:ball/step

execute if entity @s at @s run particle minecraft:glow ~ ~ ~ 0.03 0.03 0.03 0 1 normal @a
