# ============================================================
#  10マスジャンプ：1tick分の弧の移動（実行者＝滞空中のプレイヤー）
#  ※前方 dz=1.0ブロック固定 / 上下 dy は山なりに増減して合計0
#    （開始位置と同じ高さに着地する。壁にぶつかったら即着地）
#    rotated ~ 0 でピッチを0に固定し、見ている上下角度に関係なく
#    水平方向へ真っすぐ進ませている
# ============================================================

execute if score @s ob.leapt matches 0 rotated ~ 0 unless block ^ ^1.35 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 0 rotated ~ 0 run tp @s ^ ^1.35 ^1.0

execute if score @s ob.leapt matches 1 rotated ~ 0 unless block ^ ^1.05 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 1 rotated ~ 0 run tp @s ^ ^1.05 ^1.0

execute if score @s ob.leapt matches 2 rotated ~ 0 unless block ^ ^0.75 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 2 rotated ~ 0 run tp @s ^ ^0.75 ^1.0

execute if score @s ob.leapt matches 3 rotated ~ 0 unless block ^ ^0.45 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 3 rotated ~ 0 run tp @s ^ ^0.45 ^1.0

execute if score @s ob.leapt matches 4 rotated ~ 0 unless block ^ ^0.15 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 4 rotated ~ 0 run tp @s ^ ^0.15 ^1.0

execute if score @s ob.leapt matches 5 rotated ~ 0 unless block ^ ^-0.15 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 5 rotated ~ 0 run tp @s ^ ^-0.15 ^1.0

execute if score @s ob.leapt matches 6 rotated ~ 0 unless block ^ ^-0.45 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 6 rotated ~ 0 run tp @s ^ ^-0.45 ^1.0

execute if score @s ob.leapt matches 7 rotated ~ 0 unless block ^ ^-0.75 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 7 rotated ~ 0 run tp @s ^ ^-0.75 ^1.0

execute if score @s ob.leapt matches 8 rotated ~ 0 unless block ^ ^-1.05 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 8 rotated ~ 0 run tp @s ^ ^-1.05 ^1.0

execute if score @s ob.leapt matches 9 rotated ~ 0 unless block ^ ^-1.35 ^1.0 #opboots:passable run return run function opboots:jump/land
execute if score @s ob.leapt matches 9 rotated ~ 0 run tp @s ^ ^-1.35 ^1.0
execute if score @s ob.leapt matches 9 run return run function opboots:jump/land

execute if score @s ob.leapt matches 0..8 run scoreboard players add @s ob.leapt 1
