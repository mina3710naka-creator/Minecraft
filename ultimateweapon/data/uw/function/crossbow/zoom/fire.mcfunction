# ============================================================
#  ロックオン花火を発射（実行者＝プレイヤー）
# ============================================================
playsound minecraft:entity.firework_rocket.launch player @a ~ ~ ~ 1 1
title @s actionbar {"text":"発射！","color":"gold","bold":true}

execute anchored eyes positioned ^ ^ ^0.6 summon minecraft:marker run function uw:crossbow/zoom/firework_init
