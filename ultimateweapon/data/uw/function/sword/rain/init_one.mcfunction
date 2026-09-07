# 落下してくる剣の初期化（実行者＝生成されたトライデント）
tag @s add uw.rain
tag @s add uw.ent
data merge entity @s {NoGravity:1b,PickupStatus:0b}
scoreboard players set @s uw.t 0
