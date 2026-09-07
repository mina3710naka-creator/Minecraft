# ============================================================
#  飛行中の巣づくり弾（実行者＝防具立て / 実行位置＝防具立て）
#  1ティックあたり 0.25 x 24 = 6ブロック進む
# ============================================================
scoreboard players set @s sw.sub 24
function spiderweb:web/step

execute if entity @s at @s run particle minecraft:end_rod ~ ~ ~ 0.04 0.04 0.04 0.004 3 normal @a
execute if entity @s at @s run particle minecraft:witch ~ ~ ~ 0.05 0.05 0.05 0 1 normal @a
