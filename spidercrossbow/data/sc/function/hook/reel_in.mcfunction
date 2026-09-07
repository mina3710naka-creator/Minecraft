# 巻き取り（実行者＝プレイヤー）― 持ち替えキーを押した瞬間
execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
function sc:hook/reel_in_do with storage sc:v
playsound minecraft:block.chain.step player @s ~ ~ ~ 1 1.6
title @s actionbar {"text":"巻き取り","color":"aqua"}
