# ============================================================
#  着弾: ブラックホール発生（実行者＝追跡マーカー / 実行位置＝着弾地点）
#  追跡マーカーをそのままブラックホールの中心として使い回す
# ============================================================
tag @s remove uw.sbtrack_blackhole
tag @s add uw.blackhole
scoreboard players set @s uw.t 0

playsound minecraft:entity.enderman.teleport player @a ~ ~ ~ 1 0.4
playsound minecraft:entity.wither.ambient player @a ~ ~ ~ 0.6 0.3
playsound minecraft:entity.generic.explode player @a ~ ~ ~ 0.8 0.3
particle minecraft:portal ~ ~ ~ 0.5 0.5 0.5 0.5 60 normal @a
particle minecraft:explosion_emitter ~ ~ ~ 0 0 0 0 1 normal @a
particle minecraft:reverse_portal ~ ~ ~ 1.5 1.5 1.5 0.3 30 normal @a
particle minecraft:sculk_soul ~ ~ ~ 1 1 1 0.05 10 normal @a
