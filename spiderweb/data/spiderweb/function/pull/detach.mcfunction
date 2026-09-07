# ============================================================
#  台車を切り離す（マクロ / 実行者＝台車 / 実行位置＝台車）
#  対応するプレイヤーを見つけて解除処理（release）を呼ぶ。
#  台車自体の後始末（rideの解除・kill）はrelease側のutil/cleanupが
#  ID一致で行う。
# ============================================================
$execute as @a[tag=sw.pulling,scores={sw.id=$(id)},limit=1] at @s run function spiderweb:release
