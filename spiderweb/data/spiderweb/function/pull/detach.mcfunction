# ============================================================
#  台車を切り離す（実行者＝台車 / 実行位置＝台車）
#  対応するプレイヤーを見つけて解除処理（release）を呼ぶ。
# ============================================================
execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
$execute as @a[tag=sw.pulling,scores={sw.id=$(id)},limit=1] at @s run function spiderweb:release
