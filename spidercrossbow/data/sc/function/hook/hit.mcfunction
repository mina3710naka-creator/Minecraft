# ============================================================
#  着弾（実行者＝防具立て / 実行位置＝着弾地点）
#  MOB に刺さった場合はその MOB を持ち主側へ引き寄せる。
#  それ以外（ブロック）はマーカーを固定し、持ち主をそこへ
#  振り子のように吊り下げる（フックショット）
# ============================================================
particle minecraft:crit ~ ~ ~ 0.15 0.15 0.15 0.06 16 normal @a
particle minecraft:end_rod ~ ~ ~ 0.1 0.1 0.1 0.02 12 normal @a
particle minecraft:happy_villager ~ ~ ~ 0.2 0.2 0.2 0 8 normal @a
playsound minecraft:block.chain.place block @a ~ ~ ~ 1 1.5

scoreboard players operation #cur sc.id = @s sc.id
execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
execute store result storage sc:v range int 1 run scoreboard players get @s sc.range

execute as @e[tag=!sc.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:armor_stand,type=!minecraft:interaction,distance=..3,limit=1,sort=nearest] at @s run function sc:hook/hit_mob with storage sc:v
execute unless entity @e[tag=!sc.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:armor_stand,type=!minecraft:interaction,distance=..3] summon minecraft:marker run function sc:hook/hit_block with storage sc:v

execute if entity @s run kill @s
