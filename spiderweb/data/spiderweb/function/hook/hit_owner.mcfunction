# ブロックに着弾：持ち主を引き寄せ開始（マクロ）
$execute as @a[tag=sw.hooking,scores={sw.id=$(id)},limit=1] at @s run function spiderweb:pull/start with storage spiderweb:v
