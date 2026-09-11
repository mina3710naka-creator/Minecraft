# クモの巣に命中：繋がっている塊ごと除去する（実行者＝雪玉 / 実行位置＝命中したクモの巣）
particle minecraft:sweep_attack ~ ~ ~ 0 0 0 0 1 normal @a
playsound minecraft:block.wool.break player @a ~ ~ ~ 1 1.2

scoreboard players set #ball sc.ballc 0
execute if block ~ ~ ~ minecraft:cobweb run function sc:ball/flood_step

execute if entity @s run kill @s
