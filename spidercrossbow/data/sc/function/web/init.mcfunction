# 巣づくり弾の初期化（実行者＝乗っ取った本物の矢）
data merge entity @s {NoGravity:1b,Invulnerable:1b,Motion:[0.0d,0.0d,0.0d]}
tag @s add sc.webproj
tag @s add sc.ent
scoreboard players operation @s sc.id = #cur sc.id
scoreboard players set @s sc.range 0
scoreboard players set @s sc.t 0
