tag @s add sc.webflying

scoreboard players add #next sc.id 1
scoreboard players operation @s sc.id = #next sc.id
scoreboard players operation #cur sc.id = @s sc.id

playsound minecraft:entity.spider.step player @a ~ ~ ~ 1 0.6

execute anchored eyes positioned ^0.3 ^-0.2 ^0.7 summon minecraft:snowball run function sc:web/init
