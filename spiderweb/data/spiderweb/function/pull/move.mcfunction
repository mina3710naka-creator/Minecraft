# ============================================================
#  台車を1tick分tpで動かす（マクロ / 実行者＝台車 / 実行位置＝台車）
# ============================================================

# 支点が消えていたら切り離す
$execute unless entity @e[tag=sw.anchor,scores={sw.id=$(id)}] run return run function spiderweb:pull/detach with storage spiderweb:v

# 十分近づいたら張り付き処理へ
$execute if entity @e[tag=sw.anchor,scores={sw.id=$(id)},distance=..0.2] run return run function spiderweb:pull/arrive with storage spiderweb:v

# 着弾点に近づくほど減速する（急停止を防ぐイーズアウト）
$execute if entity @e[tag=sw.anchor,scores={sw.id=$(id)},distance=0.2..4] if score @s sw.spd matches 61.. run scoreboard players set @s sw.spd 60
$execute if entity @e[tag=sw.anchor,scores={sw.id=$(id)},distance=4..8] if score @s sw.spd matches 121.. run scoreboard players set @s sw.spd 120

particle minecraft:glow ~ ~ ~ 0.1 0.15 0.1 0 1 normal @a

$execute facing entity @e[tag=sw.anchor,scores={sw.id=$(id)},limit=1] feet run tp @s ~ ~ ~ ~ ~

# 1tick分の移動を8分割し、細かいステップごとに着弾判定をしながら進む
execute store result storage spiderweb:v step double 0.00125 run scoreboard players get @s sw.spd
scoreboard players set @s sw.sub 8
function spiderweb:pull/step with storage spiderweb:v
