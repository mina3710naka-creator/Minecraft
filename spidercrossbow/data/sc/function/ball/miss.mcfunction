# 何にも当たらず射程切れ（実行者＝防具立て）
particle minecraft:smoke ~ ~ ~ 0.05 0.05 0.05 0.01 4 normal @a
execute if entity @s run kill @s
