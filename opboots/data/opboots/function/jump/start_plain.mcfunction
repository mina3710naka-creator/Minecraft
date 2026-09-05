# ============================================================
#  通常ジャンプ発射（実行者＝プレイヤー、3秒溜まる前にジャンプ入力があったとき）
#  爆発は起きず、向いている水平方向へ約10ブロック飛ぶだけ
# ============================================================
scoreboard players set @s ob.vv 80

playsound minecraft:entity.arrow.shoot master @s ~ ~ ~ 1 1.2
particle minecraft:cloud ~ ~0.1 ~ 0.2 0.05 0.2 0.02 6 force @s

function opboots:jump/launch
