# ============================================================
#  フックの発射（実行者＝プレイヤー / 実行位置＝プレイヤー）
# ============================================================
tag @s add hs.active
tag @s add hs.hooking
tag @s add hs.caster

# このショット専用の ID を採番して、プレイヤーと各エンティティに共有させる
scoreboard players add #next hs.id 1
scoreboard players operation @s hs.id = #next hs.id
scoreboard players operation #cur hs.id = @s hs.id

scoreboard players set @s hs.t 0
scoreboard players set @s hs.pt 0
scoreboard players set @s hs.spd 0

playsound minecraft:entity.fishing_bobber.throw player @a ~ ~ ~ 0.8 1.6
particle minecraft:end_rod ~ ~1 ~ 0.2 0.2 0.2 0.01 6 normal @a

# 透明な防具立て（フック）を視点方向へ射出
execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:armor_stand run function hookshot:hook/init

# リードを繋ぐための見えないアンカー
execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:bat run function hookshot:rope/init

tag @s remove hs.caster
