# ============================================================
#  左クリック: クモの巣除去弾の発射（実行者＝プレイヤー / 実行位置＝プレイヤー）
#  発射・引き寄せ中かどうかに関わらず、いつでも使える独立した弾。
# ============================================================
playsound minecraft:entity.arrow.shoot player @a ~ ~ ~ 0.8 1.9
particle minecraft:cloud ~ ~1 ~ 0.15 0.15 0.15 0.01 6 normal @a

execute anchored eyes positioned ^0.3 ^-0.2 ^0.5 summon minecraft:armor_stand run function spiderweb:ball/init
