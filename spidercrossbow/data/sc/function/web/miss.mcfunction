particle minecraft:smoke ~ ~ ~ 0.1 0.1 0.1 0.01 8 normal @a
playsound minecraft:entity.item.break player @a ~ ~ ~ 0.5 1.4
execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
function sc:web/miss2 with storage sc:v
execute if entity @s run kill @s
