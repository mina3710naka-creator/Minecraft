# ============================================================
#  マーカーの向きへ移動（マクロ / 実行者＝プレイヤー）
# ============================================================

# 対応するマーカーが無ければ解除
$execute unless entity @e[type=marker,tag=ob.jumpdir,scores={ob.id=$(id)}] run function opboots:jump/stop

# マーカーの水平方向へ 0.5 ブロック進め、垂直方向へ ob.vv ぶん移動
$execute rotated as @e[type=marker,tag=ob.jumpdir,scores={ob.id=$(id)},limit=1] positioned ^ ^ ^0.5 run tp @s ~ ~$(vv) ~
