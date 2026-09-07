# クリック検知エンティティを片付ける（実行者＝プレイヤー）
tag @s remove uw.has_interact
kill @e[type=minecraft:interaction,tag=uw.sword_interact,distance=..2,limit=1,sort=nearest]
