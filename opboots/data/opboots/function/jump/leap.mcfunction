# ============================================================
#  10マスジャンプ 開始（実行者＝プレイヤー、地上）
#  tpは使わず、実在するウィンドチャージを自分の当たり判定に重なる
#  位置へ複数個設置し、まとめてMotionを書き換えて自分自身に
#  衝突させることで、本物の爆風の上向きノックバックだけで
#  真上へ打ち上げる。ウィンドチャージの爆発はもともとブロックを
#  壊さない仕様なので地形は一切壊れない。
#  （`/damage <対象> <量> minecraft:wind_charge at <座標>` は、量を
#  大きくしても連続で撃ってもノックバックが変わらなかったため、
#  実際に本物の爆発を起こす方式に戻している）
# ============================================================

function opboots:jump/start

summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}

execute as @e[type=wind_charge,tag=ob.wc,distance=..1] run data modify entity @s Motion set value [0.0,-1.0,0.0]
