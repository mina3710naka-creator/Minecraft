# クモの巣以外の障害物に当たった、または射程切れ（実行者＝防具立て）
particle minecraft:smoke ~ ~ ~ 0.08 0.08 0.08 0.01 5 normal @a
execute if entity @s run kill @s
