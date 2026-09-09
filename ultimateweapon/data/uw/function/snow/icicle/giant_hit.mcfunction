# 巨大な氷塊の着弾（実行者＝falling_block / 実行位置＝着弾地点）100ダメージ
playsound minecraft:block.glass.break player @a ~ ~ ~ 1 0.4
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 0.8 0.6
particle minecraft:block minecraft:blue_ice ~ ~ ~ 1.5 1.5 1.5 0.2 60 normal @a
particle minecraft:explosion ~ ~ ~ 0 0 0 0 1 normal @a

damage @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..3] 100 minecraft:generic

kill @s
