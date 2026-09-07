# マーカー（着弾地点に固定される支点）の初期化
tag @s add sw.anchor
tag @s add sw.tip
tag @s add sw.ent
scoreboard players operation @s sw.id = #cur sw.id
scoreboard players set @s sw.t 0
