# 射程外に終わったので持ち主を解除する（マクロ）
$execute as @a[tag=sw.active,scores={sw.id=$(id)},limit=1] at @s run function spiderweb:release
