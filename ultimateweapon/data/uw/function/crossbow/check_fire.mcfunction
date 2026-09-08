# ============================================================
#  自分の近くに未処理の矢がいないか確認する（実行者＝プレイヤー）
# ============================================================
execute unless entity @e[type=minecraft:arrow,tag=!uw.seen,distance=..4] run return 0

tag @e[type=minecraft:arrow,tag=!uw.seen,distance=..4,limit=1,sort=nearest] add uw.seen
function uw:crossbow/on_shot
