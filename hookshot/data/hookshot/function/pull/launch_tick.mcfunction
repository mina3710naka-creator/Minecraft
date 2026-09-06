# ============================================================
#  打ち上げ（レビテーション）中の毎tick処理（実行者＝上昇中のプレイヤー）
#  opboots の 10マスジャンプ（jump/tick.mcfunction）と同じ考え方：
#  数tickだけ加速させたら効果を切り、あとは通常の重力に任せて
#  落ちてくるのを待つ（tpは使わない）。
# ============================================================

scoreboard players add @s hs.lt 1

execute if score @s hs.lt matches 4.. run effect clear @s minecraft:levitation
execute if score @s hs.lt matches 4.. run tag @s remove hs.launching
