# ============================================================
#  フックの 1 ステップ（0.25 ブロック）＋ 着弾判定
#  ※実行位置は必ずフック自身（at @s）であること
# ============================================================

# 進む先に MOB がいれば着弾（自分自身の内部エンティティ・プレイヤー・
# アイテム等は対象外）。プレイヤーを除外しているのは、発射直後は
# 発射者自身がすぐ近くにいるため。
# ※ distance は MOB の「足元の基準点」までのユークリッド距離であり、当たり
# 判定ボックスそのものは見ていない。フックは発射者の目線の高さ（足元より
# 1.4～1.6 ブロック程度上）を飛ぶため、目の前を通り過ぎても基準点までは
# 離れたままで反応しないことが多かった。x/y/z + dx/dy/dz のボリューム
# 判定（＝実際の当たり判定ボックスとの交差を見る）に置き換え、しゃがみや
# 地形の高低差による目線のブレも吸収できるよう、判定箱を進行方向の点を
# 中心に下方向へ広めに取っている
execute positioned ^ ^ ^0.25 if entity @e[tag=!hs.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:fishing_bobber,type=!minecraft:end_crystal,x=~-0.6,y=~-2,z=~-0.6,dx=1.2,dy=3,dz=1.2] run return run function hookshot:hook/hit

# 進む先が通り抜けられないブロック＝着弾
execute unless block ^ ^ ^0.25 #hookshot:passable run return run function hookshot:hook/hit

tp @s ^ ^ ^0.25
scoreboard players add @s hs.range 1

# 射程 100 ブロック（0.25 × 400）を超えたら失敗
execute if score @s hs.range matches 400.. run return run function hookshot:hook/miss

# 残りのサブステップ（移動後の位置に実行位置を合わせ直してから再帰）
scoreboard players remove @s hs.sub 1
execute if score @s hs.sub matches 1.. at @s run function hookshot:hook/step
