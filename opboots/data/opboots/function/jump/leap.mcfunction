# ============================================================
#  10マスジャンプ 開始（実行者＝プレイヤー）
# ============================================================

# 次回の溜めのためにシフト保持カウンターをリセット
scoreboard players set @s ob.sneak 0

tag @s add ob.leaping
scoreboard players set @s ob.leapt 0

playsound minecraft:entity.ender_dragon.flap player @s ~ ~ ~ 0.6 1.6
particle minecraft:cloud ~ ~0.1 ~ 0.3 0.1 0.3 0.01 15
