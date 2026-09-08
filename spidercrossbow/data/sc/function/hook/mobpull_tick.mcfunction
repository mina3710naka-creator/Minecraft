# MOB引き寄せの毎ティック処理の入口（実行者＝引き寄せ中のMOB）
scoreboard players add @s sc.t 1
execute store result storage sc:v id int 1 run scoreboard players get @s sc.id
function sc:hook/mobpull_tick2 with storage sc:v
