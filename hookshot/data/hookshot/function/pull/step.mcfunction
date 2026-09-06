# ============================================================
#  台車の 1 ステップ移動＋着弾判定（マクロ / 実行者＝台車）
#  hook/step.mcfunction と同じ考え方：進む先を確認してから 1 歩ずつ進む。
#
#  台車（コウモリ）は実体として約 0.5 ブロックの幅を持つため、進行方向
#  の中心線 1 点だけを確認していると、斜めの壁やコーナーに体の端が
#  当たって実際には進めないのに「まだ通れる」と誤判定し、本物の衝突
#  判定とズレて詰まってしまうことがあった（見た目は静止しているのに
#  到達判定も詰まり判定も出ず、3 秒タイムアウトまで動かなくなる）。
#  そのため、中心・左右・頭上を含めた複数点で確認する。
# ============================================================

scoreboard players set @s hs.blk 0
$execute unless block ^ ^ ^$(step) #hookshot:passable run scoreboard players set @s hs.blk 1
$execute unless block ^-0.3 ^ ^$(step) #hookshot:passable run scoreboard players set @s hs.blk 1
$execute unless block ^0.3 ^ ^$(step) #hookshot:passable run scoreboard players set @s hs.blk 1
$execute unless block ^ ^1 ^$(step) #hookshot:passable run scoreboard players set @s hs.blk 1
$execute unless block ^-0.3 ^1 ^$(step) #hookshot:passable run scoreboard players set @s hs.blk 1
$execute unless block ^0.3 ^1 ^$(step) #hookshot:passable run scoreboard players set @s hs.blk 1

# 塞がっていた場合：マーカーにある程度近ければ「到達」扱いで打ち上げへ、
# 遠く離れた場所での予期しない衝突ならそのまま切り離す
$execute if score @s hs.blk matches 1 if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..3] run return run function hookshot:pull/arrive with storage hookshot:v
execute if score @s hs.blk matches 1 run return run function hookshot:pull/detach

$tp @s ^ ^ ^$(step)

# 十分近づいたら打ち上げ処理へ
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..1.6] run return run function hookshot:pull/arrive with storage hookshot:v

# 残りのステップ（移動後の位置に実行位置を合わせ直してから再帰）
scoreboard players remove @s hs.sub 1
execute if score @s hs.sub matches 1.. at @s run function hookshot:pull/step with storage hookshot:v
