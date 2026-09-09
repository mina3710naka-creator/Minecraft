# ============================================================
#  特別な球の発射（実行者＝プレイヤー）
#  クモの巣クリアボール（専用の雪玉）を投げると発動する
# ============================================================
playsound minecraft:entity.snowball.throw player @a ~ ~ ~ 1 1.4
particle minecraft:crit ~ ~1 ~ 0.15 0.15 0.15 0.05 6 normal @a

execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:snowball run function sc:ball/init
