# ============================================================
#  台車の毎ティック処理（実行者＝台車 / 実行位置＝台車）
#  速度（hs.spd）を毎ティック加算 → イーズインで滑らかに加速
# ============================================================
scoreboard players add @s hs.pt 1

# 加速（+0.06 / tick）と上限（0.9 ブロック / tick）
scoreboard players add @s hs.spd 6
execute if score @s hs.spd matches 90.. run scoreboard players set @s hs.spd 90

execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
function hookshot:pull/move with storage hookshot:v

# 3 秒（60 ティック）引き寄せられたら切り離す
execute if score @s hs.pt matches 60.. run function hookshot:pull/detach
