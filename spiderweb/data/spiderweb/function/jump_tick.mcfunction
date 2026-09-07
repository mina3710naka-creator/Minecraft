# ============================================================
#  解除ジャンプ（レビテーション）中の毎tick処理（実行者＝上昇中のプレイヤー）
#  数tickだけ加速させたら効果を切り、あとは重力に任せて落ちてくるのを
#  待つ（tpは使わない）。
# ============================================================
scoreboard players add @s sw.lt 1

execute if score @s sw.lt matches 7.. run effect clear @s minecraft:levitation
execute if score @s sw.lt matches 7.. run tag @s remove sw.launching
