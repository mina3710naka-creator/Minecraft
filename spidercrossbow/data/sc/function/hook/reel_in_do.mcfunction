$scoreboard players remove @e[tag=sc.carrier,scores={sc.id=$(id)},limit=1] sc.rope 8
$execute if score @e[tag=sc.carrier,scores={sc.id=$(id)},limit=1] sc.rope matches ..11 run scoreboard players set @e[tag=sc.carrier,scores={sc.id=$(id)},limit=1] sc.rope 12
