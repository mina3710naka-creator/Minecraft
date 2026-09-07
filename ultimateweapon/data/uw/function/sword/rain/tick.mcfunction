# ============================================================
#  落下中の剣の毎ティック処理（実行者＝トライデント / 実行位置＝トライデント）
#  1tickに2ブロック降下、9tick（18ブロック）で着弾
# ============================================================
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 9.. run function uw:sword/rain/impact
execute unless entity @s run return 0

particle minecraft:crit ~ ~ ~ 0.1 0.3 0.1 0 2 normal @a
tp @s ~ ~-2 ~
