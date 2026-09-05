# ============================================================
#  速度ベクトルを計算して台車の Motion に書き込む
#  （マクロ / 実行者＝計算用の目印 marker / 実行位置＝マーカーの座標）
#  marker は「台車の位置 + アンカー方向 × 速度」の地点に出現しているので、
#  そこから台車自身の位置（#cx/#cy/#cz、pull/move.mcfunction で退避済み）を
#  引けば、純粋な「アンカー方向 × 速度」の速度ベクトルが得られる。
# ============================================================
execute store result score #tx hs.pos run data get entity @s Pos[0] 100
execute store result score #ty hs.pos run data get entity @s Pos[1] 100
execute store result score #tz hs.pos run data get entity @s Pos[2] 100
kill @s

scoreboard players operation #tx hs.pos -= #cx hs.pos
scoreboard players operation #ty hs.pos -= #cy hs.pos
scoreboard players operation #tz hs.pos -= #cz hs.pos

execute store result storage hookshot:v dx double 0.01 run scoreboard players get #tx hs.pos
execute store result storage hookshot:v dy double 0.01 run scoreboard players get #ty hs.pos
execute store result storage hookshot:v dz double 0.01 run scoreboard players get #tz hs.pos

$execute as @e[tag=hs.carrier,scores={hs.id=$(id)},limit=1] run function hookshot:pull/apply_motion with storage hookshot:v
