# ============================================================
#  MOBの1ステップ移動（マクロ / 実行者＝MOB）
#  進む先が塞がっていればそこで到達扱いにする（壁にめり込ませない）
# ============================================================
$execute unless block ^ ^ ^$(step) #spiderweb:passable run return run function spiderweb:yank/arrive with storage spiderweb:v
$execute unless block ^ ^1 ^$(step) #spiderweb:passable run return run function spiderweb:yank/arrive with storage spiderweb:v

$tp @s ^ ^ ^$(step)

$execute at @s if entity @a[tag=sw.yanking,scores={sw.id=$(id)},distance=..2] run return run function spiderweb:yank/arrive with storage spiderweb:v

scoreboard players remove @s sw.sub 1
execute if score @s sw.sub matches 1.. at @s run function spiderweb:yank/move with storage spiderweb:v
