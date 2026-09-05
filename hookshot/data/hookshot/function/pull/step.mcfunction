# ============================================================
#  引き寄せの 1 ステップ（マクロ / 実行者＝プレイヤー）
#  hook/step と同じ考え方で、進む先を確認してから 1 歩ずつ進む。
#  これにより壁の手前でぴったり止まり、めり込まなくなる。
# ============================================================

# 足元・頭上のどちらかが塞がっていれば、その場で解除（壁に激突）
$execute unless block ^ ^ ^$(step) #hookshot:passable run return run function hookshot:release
$execute unless block ^ ^1 ^$(step) #hookshot:passable run return run function hookshot:release

# 視点はそのまま、座標だけ 1 ステップぶん更新（回転を書き換えないのでブレない）
$tp @s ^ ^ ^$(step)

particle minecraft:end_rod ~ ~0.9 ~ 0.1 0.2 0.1 0.005 1 normal @a

# 十分近づいたら解除
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..1.5] run return run function hookshot:release

# 残りのステップ（移動後の位置から再開するよう at @s で位置を更新）
scoreboard players remove @s hs.sub 1
execute if score @s hs.sub matches 1.. at @s run function hookshot:pull/step with storage hookshot:v
