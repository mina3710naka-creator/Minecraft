# ============================================================
#  爆発ジャンプ発射（実行者＝プレイヤー）
#  向いている水平方向へ、放物線を描きながら約10ブロック飛ぶ
# ============================================================
scoreboard players add #ob_next ob.id 1
scoreboard players operation @s ob.id = #ob_next ob.id

scoreboard players set @s ob.charge 0
scoreboard players set @s ob.jt 0
scoreboard players set @s ob.vv 80
tag @s add ob.jumping

# 爆発演出（ブロック・敵には影響しない見た目だけの爆発）
playsound minecraft:entity.generic.explode master @a ~ ~ ~ 3 0.8
particle minecraft:explosion_emitter ~ ~0.1 ~ 0 0 0 0 1 force @a

# 発射時の水平方向（ピッチ0）を保持する固定マーカーを設置
summon minecraft:marker ~ ~ ~ {Tags:["ob.jumpdir"]}
execute rotated as @s at @s run tp @e[type=marker,tag=ob.jumpdir,distance=..0.2,sort=nearest,limit=1] ~ ~ ~ ~ 0
execute store result score @e[type=marker,tag=ob.jumpdir,distance=..0.2,sort=nearest,limit=1] ob.id run scoreboard players get @s ob.id
