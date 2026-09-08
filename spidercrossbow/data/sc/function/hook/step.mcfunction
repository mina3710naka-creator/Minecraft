# ============================================================
#  フックの 1 ステップ（0.25 ブロック）＋ 着弾判定
#  ※実行位置は必ずフック自身（at @s）であること
#  進行方向の点から 3 ブロック以内に MOB がいれば自動吸着で刺さる
# ============================================================
execute positioned ^ ^ ^0.25 if entity @e[tag=!sc.ent,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:armor_stand,type=!minecraft:interaction,type=!minecraft:arrow,distance=..3] run return run function sc:hook/hit

execute unless block ^ ^ ^0.25 #sc:passable run return run function sc:hook/hit

tp @s ^ ^ ^0.25
scoreboard players add @s sc.range 1

# 射程 100 ブロック（0.25 × 400）を超えたら失敗
execute if score @s sc.range matches 400.. run return run function sc:hook/miss

scoreboard players remove @s sc.sub 1
execute if score @s sc.sub matches 1.. at @s run function sc:hook/step
