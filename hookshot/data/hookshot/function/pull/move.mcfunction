# ============================================================
#  台車に Motion（速度ベクトル）を与える（マクロ / 実行者＝台車 / 実行位置＝台車）
# ============================================================

# 支点が消えていたら切り離す
$execute unless entity @e[tag=hs.anchor,scores={hs.id=$(id)}] run return run function hookshot:pull/detach

# 到達したら切り離す（実体の衝突があるので、めり込みを気にせず近くまで寄せられる）
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=..1.3] run return run function hookshot:pull/detach

# 着弾点に近づくほど減速する（急停止を防ぐイーズアウト）
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=1.3..3] if score @s hs.spd matches 31.. run scoreboard players set @s hs.spd 30
$execute if entity @e[tag=hs.anchor,scores={hs.id=$(id)},distance=3..5] if score @s hs.spd matches 61.. run scoreboard players set @s hs.spd 60

particle minecraft:glow ~ ~ ~ 0.1 0.15 0.1 0 1 normal @a

# 台車の現在位置を centi-block 整数でスコアに退避しておく
# （このあと仮の marker で「アンカー方向へ進んだ座標」を読み取り、
#  こことの差分を取ることで Motion に使う速度ベクトルを求める。
#  ワールド原点(0 0 0)は読み込まれているとは限らないため、必ず
#  台車自身のすぐ近く＝読み込み済みの場所だけで完結させている）
execute store result score #cx hs.pos run data get entity @s Pos[0] 100
execute store result score #cy hs.pos run data get entity @s Pos[1] 100
execute store result score #cz hs.pos run data get entity @s Pos[2] 100

execute store result storage hookshot:v step double 0.01 run scoreboard players get @s hs.spd
$execute at @s facing entity @e[tag=hs.anchor,scores={hs.id=$(id)},limit=1] feet positioned ^ ^ ^$(step) summon minecraft:marker run function hookshot:pull/write_motion with storage hookshot:v
