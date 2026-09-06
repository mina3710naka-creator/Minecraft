# ============================================================
#  10マスジャンプ中の毎tick処理（実行者＝上昇中のプレイヤー）
#  レビテーションで9tickだけ加速させたら効果を切り、
#  あとは通常の重力に任せて落ちてくるのを待つ（tpは使わない）。
#  amplifierを上げた分、以前（20tick）より短い時間で
#  同じくらいの高さに到達するようにしてある
# ============================================================

scoreboard players add @s ob.leapt 1

execute if score @s ob.leapt matches 9.. run effect clear @s minecraft:levitation
execute if score @s ob.leapt matches 9.. run tag @s remove ob.leaping
