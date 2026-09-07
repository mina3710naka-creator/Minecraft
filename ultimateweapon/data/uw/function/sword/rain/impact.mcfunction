# 剣の着弾（実行者＝トライデント / 実行位置＝着弾地点）60ダメージ
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 0.5 1.8
particle minecraft:explosion ~ ~ ~ 0 0 0 0 1 normal @a
particle minecraft:crit ~ ~ ~ 0.5 0.3 0.5 0 12 normal @a

damage @e[tag=!uw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=..2.5] 60 minecraft:generic

function uw:util/quiet_kill
