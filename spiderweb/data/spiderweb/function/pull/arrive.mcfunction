# ============================================================
#  支点に到達（マクロ / 実行者＝台車 / 実行位置＝台車）
#  hookshotと違い、ここでは自動で打ち上げ解除せず、そのまま
#  「糸に張り付く」状態(attach)へ移行する。
# ============================================================
$execute as @a[tag=sw.pulling,scores={sw.id=$(id)},limit=1] at @s run function spiderweb:attach/start with storage spiderweb:v
