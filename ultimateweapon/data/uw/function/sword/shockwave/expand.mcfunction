# ============================================================
#  マクロ: 8方向のリング座標にパーティクルを描画し、現在の半径帯
#  （rmin〜rmax）にいる対象へダメージを与える（実行者＝中心マーカー）
# ============================================================
$particle minecraft:sweep_attack ~$(p1x) ~0.1 ~$(p1z) 0.4 0.1 0.4 0 3 normal @a
$particle minecraft:crit ~$(p1x) ~0.3 ~$(p1z) 0.3 0.2 0.3 0 4 normal @a
$particle minecraft:sweep_attack ~$(p2x) ~0.1 ~$(p2z) 0.4 0.1 0.4 0 3 normal @a
$particle minecraft:crit ~$(p2x) ~0.3 ~$(p2z) 0.3 0.2 0.3 0 4 normal @a
$particle minecraft:sweep_attack ~$(p3x) ~0.1 ~$(p3z) 0.4 0.1 0.4 0 3 normal @a
$particle minecraft:crit ~$(p3x) ~0.3 ~$(p3z) 0.3 0.2 0.3 0 4 normal @a
$particle minecraft:sweep_attack ~$(p4x) ~0.1 ~$(p4z) 0.4 0.1 0.4 0 3 normal @a
$particle minecraft:crit ~$(p4x) ~0.3 ~$(p4z) 0.3 0.2 0.3 0 4 normal @a
$particle minecraft:sweep_attack ~$(p5x) ~0.1 ~$(p5z) 0.4 0.1 0.4 0 3 normal @a
$particle minecraft:crit ~$(p5x) ~0.3 ~$(p5z) 0.3 0.2 0.3 0 4 normal @a
$particle minecraft:sweep_attack ~$(p6x) ~0.1 ~$(p6z) 0.4 0.1 0.4 0 3 normal @a
$particle minecraft:crit ~$(p6x) ~0.3 ~$(p6z) 0.3 0.2 0.3 0 4 normal @a
$particle minecraft:sweep_attack ~$(p7x) ~0.1 ~$(p7z) 0.4 0.1 0.4 0 3 normal @a
$particle minecraft:crit ~$(p7x) ~0.3 ~$(p7z) 0.3 0.2 0.3 0 4 normal @a
$particle minecraft:sweep_attack ~$(p8x) ~0.1 ~$(p8z) 0.4 0.1 0.4 0 3 normal @a
$particle minecraft:crit ~$(p8x) ~0.3 ~$(p8z) 0.3 0.2 0.3 0 4 normal @a

$execute as @e[tag=!uw.ent,tag=!uw.shockhit,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=$(rmin)..$(rmax)] at @s run function uw:sword/shockwave/hit_one
