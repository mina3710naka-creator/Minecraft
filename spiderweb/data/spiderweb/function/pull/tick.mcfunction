# ============================================================
#  台車の毎ティック処理（実行者＝台車 / 実行位置＝台車）
#  速度（sw.spd）を毎ティック加算してイーズインで滑らかに加速する
# ============================================================
scoreboard players add @s sw.pt 1

scoreboard players add @s sw.spd 60
execute if score @s sw.spd matches 400.. run scoreboard players set @s sw.spd 400

execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
function spiderweb:pull/move with storage spiderweb:v

# 3秒（60tick）以上引き寄せられたら、迷子防止のため切り離す
execute if score @s sw.pt matches 60.. run function spiderweb:pull/detach with storage spiderweb:v
