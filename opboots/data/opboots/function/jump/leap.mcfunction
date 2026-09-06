# ============================================================
#  10マスジャンプ 開始（実行者＝プレイヤー）
#  tpは使わず、足元にまず静止したウィンドチャージを設置してから、
#  そのMotionを書き換えて真下へ叩き込み、地面へ着弾させて起爆する
# ============================================================

# 次回の溜めのためにシフト保持カウンターをリセット
scoreboard players set @s ob.sneak 0

tag @s add ob.leaping
scoreboard players set @s ob.leapt 0

summon minecraft:wind_charge ~ ~-0.3 ~ {NoGravity:1b,Tags:["ob.wc"]}
data modify entity @e[type=wind_charge,tag=ob.wc,distance=..0.5,limit=1,sort=nearest] Motion set value [0.0,-1.0,0.0]
