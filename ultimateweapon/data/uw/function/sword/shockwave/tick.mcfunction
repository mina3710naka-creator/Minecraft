# ============================================================
#  衝撃波の毎ティック処理（実行者＝中心マーカー / 実行位置＝中心）
#  半径は1tickごとに1ブロックずつ拡大し、18tick（18ブロック）で消える。
#  8方向（45度ごと）に固定の三角比を掛けて座標を求め、リング状に
#  はっきり見えるパーティクルを配置する（乱数によるモヤ状の雲ではなく、
#  実際に輪として広がって見えるようにするため）
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 18.. run function uw:util/quiet_kill
execute unless entity @s run return 0

scoreboard players operation #uw_shock_rmax uw.sub = @s uw.t
scoreboard players add #uw_shock_rmax uw.sub 2
execute store result storage uw:v rmin double 1 run scoreboard players get @s uw.t
execute store result storage uw:v rmax double 1 run scoreboard players get #uw_shock_rmax uw.sub

execute store result storage uw:v p1x double 1 run scoreboard players get @s uw.t
execute store result storage uw:v p1z double 0 run scoreboard players get @s uw.t
execute store result storage uw:v p2x double 0.707 run scoreboard players get @s uw.t
execute store result storage uw:v p2z double 0.707 run scoreboard players get @s uw.t
execute store result storage uw:v p3x double 0 run scoreboard players get @s uw.t
execute store result storage uw:v p3z double 1 run scoreboard players get @s uw.t
execute store result storage uw:v p4x double -0.707 run scoreboard players get @s uw.t
execute store result storage uw:v p4z double 0.707 run scoreboard players get @s uw.t
execute store result storage uw:v p5x double -1 run scoreboard players get @s uw.t
execute store result storage uw:v p5z double 0 run scoreboard players get @s uw.t
execute store result storage uw:v p6x double -0.707 run scoreboard players get @s uw.t
execute store result storage uw:v p6z double -0.707 run scoreboard players get @s uw.t
execute store result storage uw:v p7x double 0 run scoreboard players get @s uw.t
execute store result storage uw:v p7z double -1 run scoreboard players get @s uw.t
execute store result storage uw:v p8x double 0.707 run scoreboard players get @s uw.t
execute store result storage uw:v p8z double -0.707 run scoreboard players get @s uw.t

function uw:sword/shockwave/expand with storage uw:v
