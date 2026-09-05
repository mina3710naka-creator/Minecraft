# ============================================================
#  マーカーへ向かって 1 ティック分移動する準備（マクロ / 実行者＝プレイヤー）
# ============================================================

# 支点が消えていたら解除
$execute unless entity @e[tag=hs.anchor,scores={hs.id=$(id)}] run function hookshot:release

# 到達したら解除
$execute if entity @s[tag=hs.pulling] if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..1.5] run function hookshot:release

# 着弾点に近づくほど減速する（急停止・めり込みを防ぐイーズアウト）
$execute if entity @s[tag=hs.pulling] if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=1.5..3] if score @s hs.spd matches 31.. run scoreboard players set @s hs.spd 30
$execute if entity @s[tag=hs.pulling] if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=3..5] if score @s hs.spd matches 61.. run scoreboard players set @s hs.spd 60

# 1 ティック分の移動を 4 分割し、フックの飛行と同じように
# 0.25 ステップぶんずつ着弾判定をしながら進む（＝オーバーシュートでの
# めり込みを起こさない）。step = hs.spd（centi）÷ 100 ÷ 4
execute store result storage hookshot:v step double 0.0025 run scoreboard players get @s hs.spd
scoreboard players set @s hs.sub 4
$execute if entity @s[tag=hs.pulling] unless entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..1.5] facing entity @e[tag=hs.anchor,scores={hs.id=$(id)},limit=1] feet run function hookshot:pull/step with storage hookshot:v
