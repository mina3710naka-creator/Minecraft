# 巣づくり弾の初期化（実行者＝生成された雪玉、視点方向をそのまま継承）
data merge entity @s {NoGravity:1b,Invulnerable:1b,Silent:1b}
tag @s add sc.webproj
tag @s add sc.ent
scoreboard players operation @s sc.id = #cur sc.id
scoreboard players set @s sc.range 0
scoreboard players set @s sc.t 0
