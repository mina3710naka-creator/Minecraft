# 花火の初期化（実行者＝生成されたアイテム実体、花火アイコンで見えるようにする）
tp @s ~ ~ ~ ~ ~
data merge entity @s {Item:{id:"minecraft:firework_rocket",count:1},NoGravity:1b,PickupDelay:32767s,Glowing:1b}
tag @s add uw.uwfirework
tag @s add uw.ent
scoreboard players set @s uw.t 0
