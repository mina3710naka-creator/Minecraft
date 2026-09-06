# ============================================================
#  10マスジャンプ 開始（実行者＝プレイヤー）
#  tpは使わず、自分の体に重なる位置にまず静止したウィンドチャージを
#  設置してから、そのMotionを書き換えて自分自身に衝突させ起爆する
#  （地面が近くにない空中ジャンプでも同じように動作させるため、
#  地面ではなく自分の当たり判定にぶつけて起爆させている）
# ============================================================

# 次回の溜めのためにシフト保持カウンターをリセット
scoreboard players set @s ob.sneak 0

tag @s add ob.leaping
scoreboard players set @s ob.leapt 0

summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
data modify entity @e[type=wind_charge,tag=ob.wc,distance=..0.5,limit=1,sort=nearest] Motion set value [0.0,-1.0,0.0]
