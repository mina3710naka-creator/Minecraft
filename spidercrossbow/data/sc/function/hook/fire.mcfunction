# ============================================================
#  モード1: フックの発射（実行者＝プレイヤー）
#  本物の矢の位置・向きには頼らず、自分の視点方向から新しく
#  雪玉ベースのフック弾を生成する（ball/fire.mcfunctionと同じ、
#  実機で確実に動くことを確認済みの方式）
# ============================================================
tag @s add sc.hookflying

scoreboard players add #next sc.id 1
scoreboard players operation @s sc.id = #next sc.id
scoreboard players operation #cur sc.id = @s sc.id

playsound minecraft:entity.fishing_bobber.throw player @a ~ ~ ~ 0.8 1.6
particle minecraft:end_rod ~ ~1 ~ 0.2 0.2 0.2 0.01 6 normal @a

execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:snowball run function sc:hook/init
