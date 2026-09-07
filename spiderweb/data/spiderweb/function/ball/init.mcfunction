# ============================================================
#  クモの巣除去弾の初期化（実行者＝生成された防具立て）
# ============================================================
tp @s ~ ~ ~ ~ ~
data merge entity @s {Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,NoBasePlate:1b,Small:1b}
tag @s add sw.ball
tag @s add sw.ent
scoreboard players set @s sw.range 0
scoreboard players set @s sw.t 0
