# ============================================================
#  フックの 1 ステップ（0.25 ブロック）＋ 着弾判定
#  ※実行位置は必ずフック自身（at @s）であること
# ============================================================

# 進む先に MOB がいれば着弾（自分自身の内部エンティティ・プレイヤー・
# アイテム等は対象外）。プレイヤーを除外しているのは、発射直後は
# 発射者自身がすぐ近くにいるため。
# ※ 見た目の当たり判定ぴったりだと僅かに逸れただけで抜けてしまうため、
# 自動吸着（オートロック）として進行方向の点から 3 ブロック以内に MOB が
# いれば刺さるようにしている。3 ブロックあれば、目線の高さと MOB の足元の
# 基準点とのズレ（1.5 ブロック程度）も十分に吸収できる
execute positioned ^ ^ ^0.25 if entity @e[tag=!hs.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:fishing_bobber,type=!minecraft:end_crystal,distance=..3] run return run function hookshot:hook/hit

# 進む先が通り抜けられないブロック＝着弾
execute unless block ^ ^ ^0.25 #hookshot:passable run return run function hookshot:hook/hit

tp @s ^ ^ ^0.25
scoreboard players add @s hs.range 1

# 射程 100 ブロック（0.25 × 400）を超えたら失敗
execute if score @s hs.range matches 400.. run return run function hookshot:hook/miss

# 残りのサブステップ（移動後の位置に実行位置を合わせ直してから再帰）
scoreboard players remove @s hs.sub 1
execute if score @s hs.sub matches 1.. at @s run function hookshot:hook/step
