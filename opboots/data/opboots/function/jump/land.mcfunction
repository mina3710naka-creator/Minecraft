# ============================================================
#  10マスジャンプ 着地処理（実行者＝プレイヤー）
# ============================================================

tag @s remove ob.leaping
scoreboard players set @s ob.leapt 0

playsound minecraft:entity.generic.small_fall player @s ~ ~ ~ 0.8 1.0
particle minecraft:cloud ~ ~0.1 ~ 0.4 0.1 0.4 0.02 20
particle minecraft:block minecraft:dirt ~ ~0.1 ~ 0.4 0.05 0.4 0.05 15
