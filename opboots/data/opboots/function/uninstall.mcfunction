# ============================================================
#  アンインストール
# ============================================================

effect clear @a[tag=ob.wearing] minecraft:resistance
effect clear @a[tag=ob.wearing] minecraft:jump_boost
effect clear @a[tag=ob.wearing] minecraft:speed
effect clear @a[tag=ob.wearing] minecraft:fire_resistance
effect clear @a[tag=ob.leaping] minecraft:levitation

tag @a remove ob.wearing
tag @a remove ob.leaping

scoreboard objectives remove ob.hp
scoreboard objectives remove ob.sneak
scoreboard objectives remove ob.jump
scoreboard objectives remove ob.leapt

tellraw @a {"text":"[最強のブーツ] アンインストールしました","color":"gray"}
