# ============================================================
#  台車の毎ティック処理（実行者＝台車 / 実行位置＝台車）
# ============================================================
scoreboard players add @s sw.pt 1

# 加速（+0.6/tick）と上限（4.0ブロック/tick）
scoreboard players add @s sw.spd 60
execute if score @s sw.spd matches 400.. run scoreboard players set @s sw.spd 400

execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
function spiderweb:pull/move with storage spiderweb:v

# 3秒（60ティック）引き寄せられたら切り離す（迷子防止）
execute if score @s sw.pt matches 60.. run function spiderweb:pull/detach with storage spiderweb:v
