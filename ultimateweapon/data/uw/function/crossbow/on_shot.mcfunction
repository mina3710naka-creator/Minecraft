# ============================================================
#  クロスボウを実際に撃った瞬間の処理（実行者＝プレイヤー）
#  モード1（レーザー）・モード2（ミサイル）は見た目だけの矢を消して
#  自作の効果に置き換える。モード0（マシンガン）は本物の矢を
#  そのまま飛ばし（連射用なので矢は消さない）、代わりに装填済み
#  フラグをリセットして次tickに再装填させる
# ============================================================
execute if score @s uw.mode matches 1 run kill @e[type=minecraft:arrow,tag=uw.seen,distance=..1.5,limit=1,sort=nearest]
execute if score @s uw.mode matches 2 run kill @e[type=minecraft:arrow,tag=uw.seen,distance=..1.5,limit=1,sort=nearest]

execute if score @s uw.mode matches 0 run scoreboard players set @s uw.mgloaded 0
execute if score @s uw.mode matches 0 run scoreboard players set @s uw.mgloadedo 0

execute if score @s uw.mode matches 1 run function uw:crossbow/laser/fire
execute if score @s uw.mode matches 2 run function uw:crossbow/zoom/instant_fire
