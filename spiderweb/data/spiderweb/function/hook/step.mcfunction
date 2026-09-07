# ============================================================
#  フックの1ステップ（0.25ブロック）＋着弾判定
#  ※実行位置は必ずフック自身（at @s）であること
#  自分自身の内部エンティティ・プレイヤー・アイテム・矢などは対象外。
#  見た目の当たり判定ぴったりだと僅かに逸れただけで抜けてしまうため、
#  進行方向の点から3ブロック以内にMOBがいれば自動で吸着させる。
# ============================================================
execute positioned ^ ^ ^0.25 if entity @e[tag=!sw.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,type=!minecraft:end_crystal,distance=..3] run return run function spiderweb:hook/hit

execute unless block ^ ^ ^0.25 #spiderweb:passable run return run function spiderweb:hook/hit

tp @s ^ ^ ^0.25
scoreboard players add @s sw.range 1

# 射程100ブロック（0.25×400）を超えたら失敗
execute if score @s sw.range matches 400.. run return run function spiderweb:hook/miss

scoreboard players remove @s sw.sub 1
execute if score @s sw.sub matches 1.. at @s run function spiderweb:hook/step
