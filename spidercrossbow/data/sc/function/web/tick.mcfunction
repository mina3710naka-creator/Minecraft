scoreboard players set @s sc.sub 20
function sc:web/step

execute if entity @s at @s run particle minecraft:cloud ~ ~ ~ 0.04 0.04 0.04 0.004 2 normal @a
