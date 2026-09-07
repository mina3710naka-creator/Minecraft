# ============================================================
#  飛行中のクモの巣除去弾（実行者＝防具立て / 実行位置＝防具立て）
#  1ティックあたり0.25×24＝6ブロック進む
# ============================================================
scoreboard players set @s sw.sub 24
function spiderweb:ball/step

execute if entity @s at @s run particle minecraft:end_rod ~ ~ ~ 0.03 0.03 0.03 0.003 2 normal @a
execute if entity @s at @s run particle minecraft:cloud ~ ~ ~ 0.04 0.04 0.04 0 1 normal @a
