# ============================================================
#  レーザーの毎ティック処理（実行者＝プレイヤー）
#  射程45ブロック（3ブロック刻み×15ステップ）、命中ごとに6ダメージ
# ============================================================
scoreboard players remove @s uw.t 1
execute if score @s uw.t matches ..0 run tag @s remove uw.laser
execute unless entity @s[tag=uw.laser] run return 0

playsound minecraft:entity.blaze.burn player @a ~ ~ ~ 0.4 1.8

scoreboard players set @s uw.sub 15
execute anchored eyes run function uw:crossbow/laser/step
