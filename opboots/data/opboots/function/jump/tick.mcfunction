# ============================================================
#  10マスジャンプ中の毎tick処理（実行者＝上昇中のプレイヤー）
#  レビテーションで44tick（約2.2秒）だけ加速させたら効果を切り、
#  あとは通常の重力に任せて落ちてくるのを待つ（tpは使わない）
# ============================================================

scoreboard players add @s ob.leapt 1

execute if score @s ob.leapt matches 44.. run effect clear @s minecraft:levitation
execute if score @s ob.leapt matches 44.. run tag @s remove ob.leaping
