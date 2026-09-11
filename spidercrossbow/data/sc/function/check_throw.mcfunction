# ============================================================
#  自分の近くに未処理の雪玉がいないか確認する（実行者＝プレイヤー）
#  見つけた本物の雪玉はその場でキルし、位置・向きには頼らず、
#  自分の視点方向から新しく特別な球を生成する（ball/fire.mcfunction）
# ============================================================
execute unless entity @e[type=minecraft:snowball,tag=!sc.seen,distance=..2] run return 0

tag @e[type=minecraft:snowball,tag=!sc.seen,distance=..2,limit=1,sort=nearest] add sc.seen
kill @e[type=minecraft:snowball,tag=sc.seen,distance=..2,limit=1,sort=nearest]
function sc:ball/fire
