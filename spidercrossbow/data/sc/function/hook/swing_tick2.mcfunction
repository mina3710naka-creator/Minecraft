# ============================================================
#  振り子の角度更新（マクロ / 実行者＝台車）
#  プレイヤーの視点（ヨー＝水平・ピッチ＝垂直）に、旋回速度を
#  制限しながら滑らかに追従させることで「見た方向へ振れる／登る」
#  操作感を作る。実際の重力シミュレーションは行わない簡易モデル。
# ============================================================
$execute unless entity @e[tag=sc.anchor,scores={sc.id=$(id)}] run return run function sc:hook/detach_lost

$execute as @a[tag=sc.hooked,scores={sc.id=$(id)},limit=1] store result score @s sc.py run data get entity @s Rotation[0] 1
$execute as @a[tag=sc.hooked,scores={sc.id=$(id)},limit=1] store result score @s sc.pp run data get entity @s Rotation[1] 1
$scoreboard players operation @s sc.py = @a[tag=sc.hooked,scores={sc.id=$(id)},limit=1] sc.py
$scoreboard players operation @s sc.pp = @a[tag=sc.hooked,scores={sc.id=$(id)},limit=1] sc.pp

# --- 水平方向(theta)をヨーへ追従（1tickあたり最大10度） ---
scoreboard players operation @s sc.d = @s sc.py
scoreboard players operation @s sc.d -= @s sc.theta
execute if score @s sc.d matches 181.. run scoreboard players remove @s sc.d 360
execute if score @s sc.d matches ..-181 run scoreboard players add @s sc.d 360
execute if score @s sc.d matches 11.. run scoreboard players set @s sc.d 10
execute if score @s sc.d matches ..-11 run scoreboard players set @s sc.d -10
scoreboard players operation @s sc.theta += @s sc.d
execute if score @s sc.theta matches 181.. run scoreboard players remove @s sc.theta 360
execute if score @s sc.theta matches ..-181 run scoreboard players add @s sc.theta 360

# --- 垂直方向(phi)をピッチへ追従（下を見る=綱の下へ、上を見る=綱の高さへ、1tickあたり最大5度） ---
scoreboard players operation @s sc.d = @s sc.pp
scoreboard players add @s sc.d 55
execute if score @s sc.d matches ..14 run scoreboard players set @s sc.d 15
execute if score @s sc.d matches 86.. run scoreboard players set @s sc.d 85
scoreboard players operation @s sc.d -= @s sc.phi
execute if score @s sc.d matches 6.. run scoreboard players set @s sc.d 5
execute if score @s sc.d matches ..-6 run scoreboard players set @s sc.d -5
scoreboard players operation @s sc.phi += @s sc.d

# --- アンカーから綱の長さ・角度ぶん離れた位置へ再配置 ---
execute store result storage sc:v yaw double 1 run scoreboard players get @s sc.theta
execute store result storage sc:v pitch double 1 run scoreboard players get @s sc.phi
execute store result storage sc:v rope double 0.25 run scoreboard players get @s sc.rope
function sc:hook/swing_move with storage sc:v
