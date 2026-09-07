# ============================================================
#  振り子の位置を実際に反映（マクロ / 実行者＝台車）
#  移動先が壁の中（#sc:passable でない）の場合はその tick は
#  動かさず、めり込みを避ける
# ============================================================
$execute at @e[tag=sc.anchor,scores={sc.id=$(id)},limit=1] rotated $(yaw) $(pitch) positioned ^ ^ ^$(rope) if block ~ ~ ~ #sc:passable run tp @s ~ ~ ~

particle minecraft:glow ~ ~ ~ 0.05 0.05 0.05 0 1 normal @a
