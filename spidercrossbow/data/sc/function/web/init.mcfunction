# 巣づくり弾の初期化（実行者＝生成された防具立て、矢の位置・向きを継承）
data merge entity @s {Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,NoBasePlate:1b,Small:1b}
tag @s add sc.webproj
tag @s add sc.ent
scoreboard players operation @s sc.id = #cur sc.id
scoreboard players set @s sc.range 0
scoreboard players set @s sc.t 0
