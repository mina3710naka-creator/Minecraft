# ============================================================
#  10マスジャンプ 開始（実行者＝プレイヤー）
#  tpは使わず、足元に静止したウィンドチャージを設置し、そこへ
#  矢を撃ち込んで起爆させる。爆風の上向きノックバックだけで
#  真上に打ち上げる
# ============================================================

# 次回の溜めのためにシフト保持カウンターをリセット
scoreboard players set @s ob.sneak 0

tag @s add ob.leaping
scoreboard players set @s ob.leapt 0

summon minecraft:wind_charge ~ ~-0.3 ~ {Motion:[0.0,0.0,0.0],NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:arrow ~ ~0.3 ~ {Motion:[0.0,-2.0,0.0],NoGravity:1b,pickup:0b,Tags:["ob.arrow"]}
