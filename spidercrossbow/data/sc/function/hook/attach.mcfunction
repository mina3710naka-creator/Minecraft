# ============================================================
#  ブロックへの着弾を確定し、振り子（フックショット）を開始する
#  （マクロ / 実行者＝プレイヤー / 実行位置＝プレイヤーの現在地）
# ============================================================
tag @s remove sc.hookflying
tag @s add sc.hooked
scoreboard players set @s sc.hookt 0

# 開始時の振り子の角度は、着弾した瞬間のプレイヤーの視点に合わせる
# （唐突に向きが変わらないようにするため）
execute store result storage sc:v theta int 1 run data get entity @s Rotation[0] 1
execute store result storage sc:v phi int 1 run data get entity @s Rotation[1] 1

particle minecraft:end_rod ~ ~1 ~ 0.3 0.4 0.3 0.03 14 normal @a
particle minecraft:happy_villager ~ ~1 ~ 0.3 0.4 0.3 0 6 normal @a
playsound minecraft:item.lead.tied player @a ~ ~ ~ 0.8 1.3

execute summon minecraft:bat run function sc:hook/carrier_init with storage sc:v
