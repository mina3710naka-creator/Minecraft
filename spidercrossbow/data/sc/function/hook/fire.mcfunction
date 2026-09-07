# ============================================================
#  モード1: フックの発射（実行者＝プレイヤー）
# ============================================================
tag @s add sc.hookflying

scoreboard players add #next sc.id 1
scoreboard players operation @s sc.id = #next sc.id
scoreboard players operation #cur sc.id = @s sc.id

playsound minecraft:entity.fishing_bobber.throw player @a ~ ~ ~ 0.8 1.6
particle minecraft:end_rod ~ ~1 ~ 0.2 0.2 0.2 0.01 6 normal @a

execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:armor_stand run function sc:hook/init
