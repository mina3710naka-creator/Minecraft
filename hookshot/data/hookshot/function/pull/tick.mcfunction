# ============================================================
#  台車の毎ティック処理（実行者＝台車 / 実行位置＝台車）
# ============================================================

# 到達後の「ジャンプ」演出中は、そちらの処理に任せる
execute if entity @s[tag=hs.jumping] run return run function hookshot:pull/jump_tick with storage hookshot:v

# 速度（hs.spd）を毎ティック加算 → イーズインで滑らかに加速
scoreboard players add @s hs.pt 1

# 加速（+0.3 / tick）と上限（2.0 ブロック / tick、フックの飛行と同じ速さ）
scoreboard players add @s hs.spd 30
execute if score @s hs.spd matches 200.. run scoreboard players set @s hs.spd 200

execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
function hookshot:pull/move with storage hookshot:v

# 3 秒（60 ティック）引き寄せられたら切り離す
execute if score @s hs.pt matches 60.. run function hookshot:pull/detach
