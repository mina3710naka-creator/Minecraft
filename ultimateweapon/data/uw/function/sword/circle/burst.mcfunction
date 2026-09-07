# ============================================================
#  周囲斬撃1発分（実行位置＝斬撃の中心）半径5ブロック、120ダメージ
# ============================================================
playsound minecraft:item.trident.thunder player @a ~ ~ ~ 0.8 1.3
particle minecraft:sweep_attack ~ ~1 ~ 0.5 0.5 0.5 0 8 normal @a
particle minecraft:crit ~ ~1 ~ 1.5 1 1.5 0 20 normal @a

damage @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..5] 120 minecraft:generic
