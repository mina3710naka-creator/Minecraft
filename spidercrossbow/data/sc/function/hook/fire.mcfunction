# ============================================================
#  モード1: フックの発射（実行者＝プレイヤー）
#  check_fire.mcfunctionが直前に見つけた本物の矢（2ブロック以内、
#  sc.seenタグ付き）を、そのままフック弾として乗っ取る
# ============================================================
tag @s add sc.hookflying

scoreboard players add #next sc.id 1
scoreboard players operation @s sc.id = #next sc.id
scoreboard players operation #cur sc.id = @s sc.id

playsound minecraft:entity.fishing_bobber.throw player @a ~ ~ ~ 0.8 1.6
particle minecraft:end_rod ~ ~1 ~ 0.2 0.2 0.2 0.01 6 normal @a

execute as @e[type=minecraft:arrow,tag=sc.seen,tag=!sc.hook,tag=!sc.webproj,distance=..2,limit=1,sort=nearest] run function sc:hook/init
