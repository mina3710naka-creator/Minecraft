execute if score #f lv matches 4584.. run function lampvideo:stop
execute if score #run lv matches 1 store result storage lampvideo:s n int 1 run scoreboard players get #f lv
execute if score #run lv matches 1 run function lampvideo:step with storage lampvideo:s
execute if score #run lv matches 1 run scoreboard players add #f lv 1
