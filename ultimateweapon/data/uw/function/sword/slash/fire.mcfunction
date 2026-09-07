# ============================================================
#  左クリック: 前方60ブロックの斬撃（実行者＝プレイヤー、視点基準）
#  4ブロック×15ステップ＝60ブロック、命中したMOBに200ダメージ
# ============================================================
tag @e[tag=uw.slashhit] remove uw.slashhit

playsound minecraft:item.trident.throw player @a ~ ~ ~ 1 0.7
particle minecraft:sweep_attack ~ ~1 ~ 0 0 0 0 1 normal @a

scoreboard players set @s uw.sub 15
execute anchored eyes run function uw:sword/slash/step
