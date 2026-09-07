# 壁など、クモの巣以外に着弾（実行者＝防具立て）
particle minecraft:crit ~ ~ ~ 0.05 0.05 0.05 0.01 4 normal @a
playsound minecraft:block.stone.hit player @a ~ ~ ~ 0.6 1.2
execute if entity @s run kill @s
