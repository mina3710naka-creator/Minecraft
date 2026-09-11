# 爆発の発生地点での処理（実行者＝プレイヤー / 実行位置＝目の前4ブロック先）
particle minecraft:explosion_emitter ~ ~ ~ 0 0 0 0 1 normal @a
particle minecraft:explosion ~ ~ ~ 1.5 1.5 1.5 0 20 normal @a
particle minecraft:flash ~ ~ ~ 0 0 0 0 1 normal @a
particle minecraft:large_smoke ~ ~ ~ 2 2 2 0.05 40 normal @a
particle minecraft:lava ~ ~ ~ 1.5 1 1.5 0 15 normal @a

damage @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..6] 80 minecraft:generic
