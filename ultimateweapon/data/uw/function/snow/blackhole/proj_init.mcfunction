# 弾の初期化（実行者＝生成されたアイテム実体、雪玉アイコンで見えるようにする）
data merge entity @s {Item:{id:"minecraft:snowball",count:1},NoGravity:1b,PickupDelay:32767s,Glowing:1b}
tag @s add uw.bhproj
tag @s add uw.ent
scoreboard players set @s uw.t 0
