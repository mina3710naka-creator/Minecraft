# ============================================================
#  特別な球の発射（実行者＝プレイヤー）
#  クロスボウを持っていれば左クリックでいつでも撃てる
# ============================================================
playsound minecraft:entity.snowball.throw player @a ~ ~ ~ 1 1.4
particle minecraft:crit ~ ~1 ~ 0.15 0.15 0.15 0.05 6 normal @a

execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:armor_stand run function sc:ball/init
