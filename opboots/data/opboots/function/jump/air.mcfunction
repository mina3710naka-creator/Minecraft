# ============================================================
#  空中ジャンプ 開始（実行者＝プレイヤー、空中でシフトを押した瞬間）
#  ジャンプキーは空中にいる間は判定できない仕様のため、代わりに
#  「空中でシフトを押した瞬間」を空中ジャンプの合図にしている。
#  地面に着地するまでに1回だけ使用できる。
#
#  空中ジャンプはすでに落下中で下向きの速度がついていることが多く、
#  爆風の勢いがその速度に上乗せされる形になって打ち消されやすいため、
#  地上ジャンプ（jump/leap.mcfunction）よりも多くのウィンドチャージを
#  同時に爆発させている（複数個同時に爆発させると爆風が重なり、
#  1個だけのときよりはっきり強くなる）
# ============================================================

tag @s add ob.airjumped
function opboots:jump/start

summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}
summon minecraft:wind_charge ~ ~0.1 ~ {NoGravity:1b,Tags:["ob.wc"]}

execute as @e[type=wind_charge,tag=ob.wc,distance=..1] run data modify entity @s Motion set value [0.0,-1.0,0.0]
