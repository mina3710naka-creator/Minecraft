# ============================================================
#  マーカーに到達（実行者＝台車 / 実行位置＝台車）
#  対応するプレイヤーを見つけ、打ち上げ処理（pull/launch）を呼ぶ。
#  id は呼び出し元から受け取るのではなく、pull/detach.mcfunction と
#  同じように自分自身のスコアから直接取得する（値の受け渡し漏れに
#  よる不具合を避けるため、より確実な方式に統一）。
# ============================================================
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
$execute as @a[tag=hs.pulling,scores={hs.id=$(id)},limit=1] at @s run function hookshot:pull/launch with storage hookshot:v
