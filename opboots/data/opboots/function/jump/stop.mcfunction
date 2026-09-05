# ============================================================
#  爆発ジャンプの解除（実行者＝プレイヤー）
# ============================================================
tag @s remove ob.jumping
scoreboard players set @s ob.jt 0
scoreboard players set @s ob.vv 0

execute store result storage opboots:v id int 1 run scoreboard players get @s ob.id
function opboots:jump/cleanup with storage opboots:v
