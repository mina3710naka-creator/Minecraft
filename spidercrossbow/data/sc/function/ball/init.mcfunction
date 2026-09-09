# 特別な球の初期化（実行者＝生成された雪玉、視点方向をそのまま継承）
data merge entity @s {NoGravity:1b,Invulnerable:1b,Silent:1b}
tag @s add sc.ball
tag @s add sc.ent
scoreboard players set @s sc.range 0
