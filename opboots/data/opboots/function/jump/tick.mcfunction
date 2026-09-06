# ============================================================
#  10マスジャンプ直後の後始末（実行者＝ジャンプ直後のプレイヤー）
#  通常はウィンドチャージの着弾ですぐ消えるが、万一残っていた場合に
#  備えて念のため片付ける。多重発動を防ぐクールダウンも兼ねている
# ============================================================

scoreboard players add @s ob.leapt 1

kill @e[type=wind_charge,tag=ob.wc,distance=..4]

execute if score @s ob.leapt matches 6.. run tag @s remove ob.leaping
