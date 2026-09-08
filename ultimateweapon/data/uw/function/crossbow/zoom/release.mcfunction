# ============================================================
#  ズームを離した瞬間（実行者＝プレイヤー）
#  Shiftを押していればキャンセル、それ以外なら花火を発射
# ============================================================
tag @s remove uw.aiming
tag @s remove uw.zoomactive
tag @s remove uw.hadlock
kill @e[tag=uw.aimprobe,distance=..150]
loot replace entity @s weapon.mainhand loot uw:give/crossbow

execute if predicate uw:sneaking run function uw:crossbow/zoom/cancel
execute unless predicate uw:sneaking run function uw:crossbow/zoom/fire
