# ブロックなどへの着弾：持ち主の引き寄せ（pull）を開始する（マクロ）
$execute as @a[tag=sw.hooking,scores={sw.id=$(id)},limit=1] at @s run function spiderweb:pull/start with storage spiderweb:v
