execute unless entity @e[type=marker,tag=lampvideo] run tellraw @s "先に /function lampvideo:setup を実行してください"
execute unless entity @e[type=marker,tag=lampvideo] run return 0
scoreboard players set #f lv 0
scoreboard players set #run lv 1
execute at @s run playsound lampvideo:movie master @s ~ ~ ~ 1 1
