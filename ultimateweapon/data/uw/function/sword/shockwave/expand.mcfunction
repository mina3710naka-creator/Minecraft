# ============================================================
#  マクロ: 現在の半径帯（rmin〜rmax）にいる対象へ演出とダメージを与える
#  （実行者＝中心マーカー）
# ============================================================
$particle minecraft:cloud ~ ~0.2 ~ $(rmax) 0.15 $(rmax) 0 25 normal @a
$particle minecraft:crit ~ ~0.2 ~ $(rmax) 0.3 $(rmax) 0 8 normal @a

$execute as @e[tag=!uw.ent,tag=!uw.shockhit,type=!minecraft:player,type=!minecraft:item,type=!minecraft:experience_orb,type=!minecraft:arrow,distance=$(rmin)..$(rmax)] at @s run function uw:sword/shockwave/hit_one
