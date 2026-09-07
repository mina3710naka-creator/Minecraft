# ============================================================
#  打ち上げ（レビテーション）中の毎tick処理（実行者＝上昇中のプレイヤー）
#  数tickだけ上昇させたら効果を切り、あとは通常の重力に任せて
#  落ちてくるのを待つ（約5マス上昇、hookshotと同じ調整値）
# ============================================================
scoreboard players add @s sw.lt 1

execute if score @s sw.lt matches 7.. run effect clear @s minecraft:levitation
execute if score @s sw.lt matches 7.. run tag @s remove sw.launching
