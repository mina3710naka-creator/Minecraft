# ============================================================
#  フックの 1 ステップ（0.25 ブロック）＋ 着弾判定
#  ※実行位置は必ずフック自身（at @s）であること
# ============================================================

# 進む先に MOB がいれば着弾（自分自身の内部エンティティ・プレイヤー・
# アイテム等は対象外）。プレイヤーを除外しているのは、発射直後は
# 発射者自身がすぐ近くにいるため。
# ※ distance は MOB の「足元の基準点」からの距離になるため、フックが
# 目線の高さ（＝たいていの MOB の胴体・頭あたり）を通っても基準点までは
# 離れていて反応しないことが多かった。dx/dy/dz で当たり判定の箱そのものと
# 重なっているかを見るようにして、高さのズレを吸収する。
execute positioned ^ ^ ^0.25 if entity @e[tag=!hs.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:fishing_bobber,type=!minecraft:end_crystal,x=~-0.75,y=~-1.5,z=~-0.75,dx=1.5,dy=3,dz=1.5] run return run function hookshot:hook/hit

# 進む先が通り抜けられないブロック＝着弾
execute unless block ^ ^ ^0.25 #hookshot:passable run return run function hookshot:hook/hit

tp @s ^ ^ ^0.25
scoreboard players add @s hs.range 1

# 射程 100 ブロック（0.25 × 400）を超えたら失敗
execute if score @s hs.range matches 400.. run return run function hookshot:hook/miss

# 残りのサブステップ（移動後の位置に実行位置を合わせ直してから再帰）
scoreboard players remove @s hs.sub 1
execute if score @s hs.sub matches 1.. at @s run function hookshot:hook/step
