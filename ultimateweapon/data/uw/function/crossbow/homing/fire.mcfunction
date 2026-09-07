# ============================================================
#  モード1: 自動追尾弾の発射（実行者＝プレイヤー）
# ============================================================
playsound minecraft:entity.arrow.shoot player @a ~ ~ ~ 1 0.6
particle minecraft:crit ~ ~1 ~ 0.2 0.2 0.2 0.05 8 normal @a

execute anchored eyes positioned ^ ^ ^0.5 summon minecraft:marker run function uw:crossbow/homing/init
