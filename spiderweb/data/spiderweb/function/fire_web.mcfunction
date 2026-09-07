# ============================================================
#  モード2（巣づくり）の発射（実行者＝プレイヤー / 実行位置＝プレイヤー）
# ============================================================
tag @s add sw.active
tag @s add sw.webbing
tag @s add sw.caster

scoreboard players add #next sw.id 1
scoreboard players operation @s sw.id = #next sw.id
scoreboard players operation #cur sw.id = @s sw.id

scoreboard players set @s sw.t 0

playsound minecraft:entity.arrow.shoot player @a ~ ~ ~ 0.8 1.4
particle minecraft:end_rod ~ ~1 ~ 0.2 0.2 0.2 0.01 6 normal @a

# 見た目上の巣づくり弾（透明な防具立て）を視点方向へ射出
execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:armor_stand run function spiderweb:web/init

# リードとロープ用の見えないアンカー（演出用）
execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:bat run function spiderweb:rope/init

tag @s remove sw.caster
