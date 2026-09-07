# ============================================================
#  モード1: フックショットの発射（実行者＝プレイヤー / 実行位置＝プレイヤー）
# ============================================================
tag @s add sw.active
tag @s add sw.hooking
tag @s add sw.caster

# このショット専用のIDを採番して、プレイヤーと各エンティティに共有させる
scoreboard players add #next sw.id 1
scoreboard players operation @s sw.id = #next sw.id
scoreboard players operation #cur sw.id = @s sw.id

scoreboard players set @s sw.t 0
scoreboard players set @s sw.pt 0
scoreboard players set @s sw.spd 0
scoreboard players set @s sw.len 0

playsound minecraft:entity.arrow.shoot player @a ~ ~ ~ 0.8 0.6
particle minecraft:end_rod ~ ~1 ~ 0.2 0.2 0.2 0.01 6 normal @a

# 透明な防具立て（フック）を視点方向へ射出
execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:armor_stand run function spiderweb:hook/init

# リードを繋ぐための見えないアンカー
execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:bat run function spiderweb:rope/init

tag @s remove sw.caster
