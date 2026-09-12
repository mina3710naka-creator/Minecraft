# 氷結レーザー、対象1体への着弾処理（実行者＝対象MOB）
damage @s 60 minecraft:generic
particle minecraft:snowflake ~ ~1 ~ 0.3 0.6 0.3 0.03 15 normal @a
particle minecraft:item minecraft:ice ~ ~1 ~ 0.2 0.4 0.2 0.02 6 normal @a
particle minecraft:block minecraft:blue_ice ~ ~1 ~ 0.3 0.3 0.3 0.05 8 normal @a
playsound minecraft:block.glass.break player @a ~ ~ ~ 0.7 1.6
