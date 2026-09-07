# 斬撃の命中処理（実行者＝命中したMOB、1回の斬撃で1体1回のみ）
tag @s add uw.slashhit
particle minecraft:crit ~ ~1 ~ 0.2 0.3 0.2 0 6 normal @a
damage @s 200 minecraft:generic
