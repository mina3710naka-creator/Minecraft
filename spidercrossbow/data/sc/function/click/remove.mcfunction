# クリック検知エンティティを片付ける（実行者＝プレイヤー）
tag @s remove sc.has_interact
kill @e[type=minecraft:interaction,tag=sc.interact,distance=..2,limit=1,sort=nearest]
