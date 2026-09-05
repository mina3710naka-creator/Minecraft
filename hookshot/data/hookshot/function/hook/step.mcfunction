# ============================================================
#  フックの 1 ステップ（0.25 ブロック）＋ 着弾判定
#  ※実行位置は必ずフック自身（at @s）であること
# ============================================================

# 進む先が通り抜けられないブロック＝着弾
execute unless block ^ ^ ^0.25 #hookshot:passable run return run function hookshot:hook/hit

tp @s ^ ^ ^0.25
scoreboard players add @s hs.range 1

# 射程 32 ブロック（0.25 × 128）を超えたら失敗
execute if score @s hs.range matches 128.. run return run function hookshot:hook/miss

# 残りのサブステップ（移動後の位置に実行位置を合わせ直してから再帰）
scoreboard players remove @s hs.sub 1
execute if score @s hs.sub matches 1.. at @s run function hookshot:hook/step
