# ============================================================
#  フック本体の初期化（実行者＝生成された防具立て）
#  実行時の回転はプレイヤーの視点なので、それをそのまま持たせる
# ============================================================
tp @s ~ ~ ~ ~ ~
data merge entity @s {Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,NoBasePlate:1b,Small:1b}
tag @s add sw.hook
tag @s add sw.tip
tag @s add sw.ent
scoreboard players operation @s sw.id = #cur sw.id
scoreboard players set @s sw.range 0
scoreboard players set @s sw.t 0
