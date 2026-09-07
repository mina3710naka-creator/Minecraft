$kill @e[tag=sc.anchor,scores={sc.id=$(id)}]
$execute as @e[tag=sc.carrier,scores={sc.id=$(id)}] run function sc:util/quiet_kill
