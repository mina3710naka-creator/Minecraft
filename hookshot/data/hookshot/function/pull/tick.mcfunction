# ============================================================
#  引き寄せ中（実行者＝プレイヤー / 実行位置＝プレイヤー）
#  速度ベクトル（hs.spd）を毎ティック加算 → イーズインで滑らかに加速
# ============================================================
scoreboard players add @s hs.pt 1

# 加速（+0.08 / tick）と上限（1.35 ブロック / tick）
scoreboard players add @s hs.spd 8
execute if score @s hs.spd matches 135.. run scoreboard players set @s hs.spd 135

# マクロ用に ID と速度（1/100 スケール → 小数）を storage へ
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
execute store result storage hookshot:v spd double 0.01 run scoreboard players get @s hs.spd
function hookshot:pull/move with storage hookshot:v

# 3 秒（60 ティック）引き寄せられたら解除
execute if entity @s[tag=hs.pulling] if score @s hs.pt matches 60.. run function hookshot:release
