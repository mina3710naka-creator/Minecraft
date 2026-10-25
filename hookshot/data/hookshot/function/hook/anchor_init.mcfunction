# マーカー（着弾地点に固定される支点）の初期化
tag @s add hs.anchor
tag @s add hs.tip
tag @s add hs.ent
scoreboard players operation @s hs.id = #cur hs.id
scoreboard players set @s hs.t 0
