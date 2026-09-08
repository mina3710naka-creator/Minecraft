# ============================================================
#  衝撃波の毎ティック処理（実行者＝中心マーカー / 実行位置＝中心）
#  半径は1tickごとに1ブロックずつ拡大し、18tick（18ブロック）で消える
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 18.. run function uw:util/quiet_kill
execute unless entity @s run return 0

scoreboard players operation #uw_shock_rmax uw.sub = @s uw.t
scoreboard players add #uw_shock_rmax uw.sub 2

execute store result storage uw:v rmin double 1 run scoreboard players get @s uw.t
execute store result storage uw:v rmax double 1 run scoreboard players get #uw_shock_rmax uw.sub
function uw:sword/shockwave/expand with storage uw:v
