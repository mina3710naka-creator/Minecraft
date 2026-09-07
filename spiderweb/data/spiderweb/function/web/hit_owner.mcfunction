# 着弾したので持ち主の使用状態を後始末する（マクロ）
$execute as @a[tag=sw.webbing,scores={sw.id=$(id)},limit=1] run function spiderweb:release
