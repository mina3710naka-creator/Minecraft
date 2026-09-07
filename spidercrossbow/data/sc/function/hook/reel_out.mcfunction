# 伸ばす（実行者＝プレイヤー）― Shift + 持ち替えキーを押した瞬間
execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
function sc:hook/reel_out_do with storage sc:v
playsound minecraft:block.chain.step player @s ~ ~ ~ 1 0.8
title @s actionbar {"text":"伸ばす","color":"gray"}
