# 振り子（台車）の毎ティック処理の入口（実行者＝台車）
execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
function sc:hook/swing_tick2 with storage sc:v
