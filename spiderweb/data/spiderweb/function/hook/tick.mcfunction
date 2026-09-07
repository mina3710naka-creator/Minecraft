# ============================================================
#  飛行中のフック（実行者＝防具立て / 実行位置＝防具立て）
#  1ティックあたり0.25×20＝5ブロック進む
# ============================================================
scoreboard players set @s sw.sub 20
function spiderweb:hook/step

execute if entity @s at @s run particle minecraft:end_rod ~ ~ ~ 0.04 0.04 0.04 0.004 3 normal @a
execute if entity @s at @s run particle minecraft:glow ~ ~ ~ 0.05 0.05 0.05 0 1 normal @a
