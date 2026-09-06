# ============================================================
#  マーカーに到達（マクロ / 実行者＝台車 / 実行位置＝台車）
#  対応するプレイヤーを見つけ、打ち上げ処理（pull/launch）を呼ぶ。
# ============================================================
$execute as @a[tag=hs.pulling,scores={hs.id=$(id)},limit=1] at @s run function hookshot:pull/launch with storage hookshot:v
