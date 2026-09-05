# ============================================================
#  爆発ジャンプの放物線移動（実行者＝プレイヤー）
#  水平 0.5 ブロック/tick 一定 + 垂直速度（ob.vv）を毎ティック重力で減衰
# ============================================================
scoreboard players add @s ob.jt 1
scoreboard players remove @s ob.vv 8

# マクロ用に ID と垂直速度（1/100 スケール → 小数）を storage へ
execute store result storage opboots:v id int 1 run scoreboard players get @s ob.id
execute store result storage opboots:v vv double 0.01 run scoreboard players get @s ob.vv
function opboots:jump/move with storage opboots:v

# 1秒（20 tick）経過、または上昇が終わって着地したら解除
execute if score @s ob.jt matches 20.. run function opboots:jump/stop
execute unless entity @s[tag=ob.jumping] run return 0
execute if score @s ob.jt matches 4.. if entity @s[nbt={OnGround:1b}] run function opboots:jump/stop
