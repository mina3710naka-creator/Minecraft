# ============================================================
#  花火の毎ティック処理（実行者＝マーカー / 実行位置＝マーカー）
#  最大寿命100tick、1tickに5ブロック進む。発射時に向いた方向へ
#  一直線に飛ぶ（追尾はしない）
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 100.. run function uw:crossbow/zoom/firework_timeout

particle minecraft:firework ~ ~ ~ 0.1 0.1 0.1 0.02 3 normal @a
particle minecraft:end_rod ~ ~ ~ 0.05 0.05 0.05 0 2 normal @a

scoreboard players set @s uw.sub 5
function uw:crossbow/zoom/firework_step
