# 撤去地点に落ちたリードを数ティックだけ掃除する（実行者＝マーカー）
kill @e[type=item,distance=..4,limit=3,nbt={PickupDelay:10s,Item:{id:"minecraft:lead"}}]
scoreboard players add @s hs.t 1
execute if score @s hs.t matches 5.. run kill @s
