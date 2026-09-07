# ============================================================
#  クモの巣除去弾の初期化（実行者＝生成された防具立て）
#  発射・引き寄せ中のIDとは無関係な、完全に独立した弾。
# ============================================================
tp @s ~ ~ ~ ~ ~
data merge entity @s {Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,NoBasePlate:1b,Small:1b}
tag @s add sw.ball
tag @s add sw.ent
scoreboard players set @s sw.range 0
scoreboard players set @s sw.t 0
