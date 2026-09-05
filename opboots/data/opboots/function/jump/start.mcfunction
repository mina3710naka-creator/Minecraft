# ============================================================
#  爆発ジャンプ発射（実行者＝プレイヤー、しゃがみ3秒溜め切ったとき）
#  向いている水平方向へ、爆発演出とともに放物線を描いて約10ブロック飛ぶ
# ============================================================
scoreboard players set @s ob.vv 80

# 爆発演出（ブロック・敵には影響しない見た目だけの爆発）
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 3 0.8
particle minecraft:explosion_emitter ~ ~0.1 ~ 0 0 0 0 1 force @a

function opboots:jump/launch
