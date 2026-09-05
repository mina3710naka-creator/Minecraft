# ============================================================
#  引き寄せ中（実行者＝プレイヤー / 実行位置＝プレイヤー）
#  速度（hs.spd）を毎ティック加算 → イーズインで滑らかに加速
# ============================================================
scoreboard players add @s hs.pt 1

# 重力とのせめぎ合いによるガクつきを抑える（毎ティック更新）
effect give @s minecraft:slow_falling 2 0 true

# 加速（+0.06 / tick）と上限（0.9 ブロック / tick）
# ※ 大きすぎる速度は、着弾点付近での急停止やめり込みの原因になるため抑えめに
scoreboard players add @s hs.spd 6
execute if score @s hs.spd matches 90.. run scoreboard players set @s hs.spd 90

# マクロ用に ID を storage へ
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
function hookshot:pull/move with storage hookshot:v

# 3 秒（60 ティック）引き寄せられたら解除
execute if entity @s[tag=hs.pulling] if score @s hs.pt matches 60.. run function hookshot:release
