# ============================================================
#  フック本体の初期化（実行者＝生成された防具立て）
#  実行時の回転はプレイヤーの視点なので、それをそのまま持たせる
# ============================================================
tp @s ~ ~ ~ ~ ~
data merge entity @s {Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,NoBasePlate:1b,Small:1b}
tag @s add hs.hook
tag @s add hs.tip
tag @s add hs.ent
scoreboard players operation @s hs.id = #cur hs.id
scoreboard players set @s hs.range 0
scoreboard players set @s hs.t 0
