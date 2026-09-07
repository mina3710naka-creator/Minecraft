# ============================================================
#  クロスボウを実際に撃った瞬間の処理（実行者＝プレイヤー）
#  見た目だけの矢（本物の弾は自作する）を消す
# ============================================================
kill @e[type=minecraft:arrow,tag=uw.seen,distance=..1.5,limit=1,sort=nearest]

execute if score @s uw.mode matches 0 run function uw:crossbow/homing/fire
execute if score @s uw.mode matches 1 run function uw:crossbow/laser/fire
execute if score @s uw.mode matches 2 run function uw:crossbow/zoom/begin
