# MOB引き寄せの終了（実行者＝対象のMOB）
tag @s remove sc.pulled
data merge entity @s {NoAI:0b}
execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
function sc:hook/mobpull_stop2 with storage sc:v
