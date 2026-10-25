# ============================================================
#  台車を切り離す（実行者＝台車 / 実行位置＝台車）
#  対応するプレイヤーを見つけて解除処理（release）を呼ぶ。
#  台車自体の後始末（ride の解除・kill）は release 側の
#  util/cleanup が id 一致で行う。
# ============================================================
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
$execute as @a[tag=hs.pulling,scores={hs.id=$(id)},limit=1] at @s run function hookshot:release
