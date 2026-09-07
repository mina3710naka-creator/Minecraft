# 凍結解除（実行者＝MOB）
tag @s remove uw.frozen
data merge entity @s {NoAI:0b}
playsound minecraft:block.glass.break player @a ~ ~ ~ 0.6 1.4
particle minecraft:item_slime ~ ~1 ~ 0.3 0.3 0.3 0 8 normal @a
