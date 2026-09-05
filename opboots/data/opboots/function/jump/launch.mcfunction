# ============================================================
#  ジャンプ共通処理：発射方向を保持するマーカーを設置する（実行者＝プレイヤー）
#  呼び出し元で ob.vv（初速）を設定してから呼び出すこと
# ============================================================
scoreboard players add #ob_next ob.id 1
scoreboard players operation @s ob.id = #ob_next ob.id

scoreboard players set @s ob.charge 0
scoreboard players set @s ob.jt 0
tag @s add ob.jumping

# 発射時の水平方向（ピッチ0）を保持する固定マーカーを設置
summon minecraft:marker ~ ~ ~ {Tags:["ob.jumpdir"]}
execute rotated as @s at @s run tp @e[type=marker,tag=ob.jumpdir,distance=..0.2,sort=nearest,limit=1] ~ ~ ~ ~ 0
execute store result score @e[type=marker,tag=ob.jumpdir,distance=..0.2,sort=nearest,limit=1] ob.id run scoreboard players get @s ob.id
