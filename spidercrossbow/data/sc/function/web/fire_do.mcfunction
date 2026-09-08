tag @s add sc.webflying

scoreboard players add #next sc.id 1
scoreboard players operation @s sc.id = #next sc.id
scoreboard players operation #cur sc.id = @s sc.id

playsound minecraft:entity.spider.step player @a ~ ~ ~ 1 0.6

execute as @e[type=minecraft:arrow,tag=sc.seen,tag=!sc.hook,tag=!sc.webproj,distance=..2,limit=1,sort=nearest] run function sc:web/init
