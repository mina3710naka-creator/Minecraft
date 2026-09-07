# ============================================================
#  着弾: つららの雨を開始する（実行者＝マーカー / 実行位置＝着弾地点）
#  弾のマーカーをそのままつらら雨の中心として使い回す
# ============================================================
tag @s remove uw.icproj
tag @s add uw.icicle
scoreboard players set @s uw.t 0

playsound minecraft:block.pointed_dripstone.land player @a ~ ~ ~ 1 0.8
particle minecraft:crit ~ ~ ~ 1 0.2 1 0 10 normal @a
