# ============================================================
#  10マスジャンプ直後の後始末（実行者＝ジャンプ直後のプレイヤー）
#  起爆用に撃った矢を確実にkillし、残っているウィンドチャージの
#  タグも掃除する。多重発動を防ぐクールダウンも兼ねている
# ============================================================

scoreboard players add @s ob.leapt 1

kill @e[type=arrow,tag=ob.arrow,distance=..4]
tag @e[type=wind_charge,tag=ob.wc,distance=..4] remove ob.wc

execute if score @s ob.leapt matches 6.. run tag @s remove ob.leaping
