# ============================================================
#  着弾地点に 3x3x3 のクモの巣を設置（マクロ / 実行位置＝着弾地点）
# ============================================================
particle minecraft:cloud ~ ~ ~ 0.2 0.2 0.2 0.02 12 normal @a
playsound minecraft:block.wool.place block @a ~ ~ ~ 1 0.7

fill ~-1 ~-1 ~-1 ~1 ~1 ~1 minecraft:cobweb

$execute as @a[tag=sc.webflying,scores={sc.id=$(id)},limit=1] run tag @s remove sc.webflying
