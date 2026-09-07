# ============================================================
#  飛行中のフック（実行者＝防具立て / 実行位置＝防具立て）
#  1 ティックあたり 0.25 × 12 = 3 ブロック進む
#  （矢を全力チャージで撃った時の初速＝3.0 ブロック / tick に合わせている）
# ============================================================
scoreboard players set @s hs.sub 12
function hookshot:hook/step

# キラキラの軌跡
execute if entity @s at @s run particle minecraft:end_rod ~ ~ ~ 0.04 0.04 0.04 0.004 3 normal @a
execute if entity @s at @s run particle minecraft:glow ~ ~ ~ 0.05 0.05 0.05 0 1 normal @a
