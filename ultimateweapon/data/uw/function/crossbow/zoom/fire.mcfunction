# ============================================================
#  ロックオン花火を発射（実行者＝プレイヤー）
#  ※ ロックオン対象が無い状態で facing entity @e[tag=uw.locktgt] を
#    実行すると、対象セレクターが誰にもマッチせずコマンド全体が
#    失敗し、花火が全く発射されない不具合があった（アクションバーの
#    「発射！」表示だけが出て何も飛ばない、という症状の原因）。
#    ロックオンの有無で分岐し、無い場合は現在の視線方向へ直進させる
# ============================================================
playsound minecraft:entity.firework_rocket.launch player @a ~ ~ ~ 1 1
title @s actionbar {"text":"発射！","color":"gold","bold":true}

execute if entity @e[tag=uw.locktgt] run function uw:crossbow/zoom/fire_at_target
execute unless entity @e[tag=uw.locktgt] run function uw:crossbow/zoom/fire_straight
