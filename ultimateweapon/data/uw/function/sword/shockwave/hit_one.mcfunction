# 衝撃波の命中処理（実行者＝命中した対象、1回の衝撃波で1体1回のみ）
tag @s add uw.shockhit
particle minecraft:sweep_attack ~ ~1 ~ 0.2 0.3 0.2 0 4 normal @a
damage @s 50 minecraft:generic
