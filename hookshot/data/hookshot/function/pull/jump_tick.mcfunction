# ============================================================
#  到達後の「ジャンプ」演出（実行者＝台車 / 実行位置＝台車）
#  減速しながら上昇し、合計でちょうど 5 ブロック分（500 centi-block）
#  上がったところでプレイヤーを解放する。行き過ぎないよう、残りの
#  上昇量でこのティックの移動量をクランプしている。
# ============================================================
scoreboard players set #rem hs.jh 500
scoreboard players operation #rem hs.jh -= @s hs.jh
scoreboard players operation @s hs.spd < #rem hs.jh

# 上昇し尽くしたら、ここでプレイヤーを解放する
execute if score @s hs.spd matches ..0 run return run function hookshot:pull/detach

execute store result storage hookshot:v step double 0.01 run scoreboard players get @s hs.spd
$tp @s ~ ~$(step) ~
scoreboard players operation @s hs.jh += @s hs.spd

# 減速（イーズアウト）
scoreboard players remove @s hs.spd 10
