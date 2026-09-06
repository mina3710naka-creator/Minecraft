# ============================================================
#  台車の 1 ステップ移動＋着弾判定（マクロ / 実行者＝台車）
#  hook/step.mcfunction と同じ考え方：進む先を確認してから 1 歩ずつ進む。
# ============================================================

# 足元・頭上のどちらかが塞がっていれば、その場で切り離す（壁に激突）
$execute unless block ^ ^ ^$(step) #hookshot:passable run return run function hookshot:pull/detach
$execute unless block ^ ^1 ^$(step) #hookshot:passable run return run function hookshot:pull/detach

$tp @s ^ ^ ^$(step)

# 十分近づいたら「ジャンプ」演出へ
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..1.3] run return run function hookshot:pull/arrive

# 残りのステップ（移動後の位置に実行位置を合わせ直してから再帰）
scoreboard players remove @s hs.sub 1
execute if score @s hs.sub matches 1.. at @s run function hookshot:pull/step with storage hookshot:v
