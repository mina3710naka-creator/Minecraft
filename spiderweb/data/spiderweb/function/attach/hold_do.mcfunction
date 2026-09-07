# 向きは変えずに座標だけアンカーの真下(lenf ブロック)へ固定する（マクロ）
$execute at @e[tag=sw.tip,scores={sw.id=$(id)},limit=1] positioned ~ ~-$(lenf) ~ run tp @s ~ ~ ~ ~ ~
