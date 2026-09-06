# ============================================================
#  10マスジャンプ 開始（実行者＝プレイヤー）
#  tpは一切使わず、レビテーション（浮遊）の上昇力だけで
#  真上に約10マス打ち上げる
# ============================================================

# 次回の溜めのためにシフト保持カウンターをリセット
scoreboard players set @s ob.sneak 0

tag @s add ob.leaping
scoreboard players set @s ob.leapt 0

effect give @s minecraft:levitation 4 4 true

playsound minecraft:entity.ender_dragon.flap player @s ~ ~ ~ 0.6 1.6
particle minecraft:cloud ~ ~0.1 ~ 0.3 0.1 0.3 0.01 15
