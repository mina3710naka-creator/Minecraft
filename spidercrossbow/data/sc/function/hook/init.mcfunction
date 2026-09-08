# ============================================================
#  フック本体の初期化（実行者＝乗っ取った本物の矢）
#  本物の発射で得た向き（＝視点方向）はそのまま活かし、重力・速度だけを
#  止めて、以降は毎tickこちらで真っ直ぐ歩ませる
# ============================================================
data merge entity @s {NoGravity:1b,Invulnerable:1b,Motion:[0.0d,0.0d,0.0d]}
tag @s add sc.hook
tag @s add sc.ent
scoreboard players operation @s sc.id = #cur sc.id
scoreboard players set @s sc.range 0
scoreboard players set @s sc.t 0
