# ============================================================
#  着弾: ブラックホール発生（実行者＝マーカー / 実行位置＝発生地点）
#  弾のマーカーをそのままブラックホールの中心として使い回す
# ============================================================
tag @s remove uw.bhproj
tag @s add uw.blackhole
scoreboard players set @s uw.t 0

playsound minecraft:entity.enderman.teleport player @a ~ ~ ~ 1 0.4
playsound minecraft:entity.wither.ambient player @a ~ ~ ~ 0.6 0.3
particle minecraft:portal ~ ~ ~ 0.5 0.5 0.5 0.5 40 normal @a
