# 左クリックを検知したので、持ち主に除去弾を発射させる（マクロ / 実行者＝Interaction）
$execute as @a[scores={sw.pid=$(pid)},limit=1] at @s run function spiderweb:fire_clear
