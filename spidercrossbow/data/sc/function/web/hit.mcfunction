# 着弾（実行者＝雪玉 / 実行位置＝着弾地点）
scoreboard players operation #cur sc.id = @s sc.id
execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
function sc:web/hit2 with storage sc:v

execute if entity @s run kill @s
