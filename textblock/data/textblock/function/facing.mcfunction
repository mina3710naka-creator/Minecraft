# ============================================================
#  テキストブロック - 向いている方角（東西南北）を判定し、
#  建築の起点座標（プレイヤーの少し前方）を求める
# ============================================================

execute store result score #yaw tb run data get entity @s Rotation[0] 1
execute store result score #px tb run data get entity @s Pos[0] 1
execute store result score #py tb run data get entity @s Pos[1] 1
execute store result score #pz tb run data get entity @s Pos[2] 1

# デフォルトは南向き（yaw -44..44）
scoreboard players set #fx tb 0
scoreboard players set #fz tb 1
scoreboard players set #rx tb -1
scoreboard players set #rz tb 0

# 西向き（yaw 45..134）
execute if score #yaw tb matches 45..134 run scoreboard players set #fx tb -1
execute if score #yaw tb matches 45..134 run scoreboard players set #fz tb 0
execute if score #yaw tb matches 45..134 run scoreboard players set #rx tb 0
execute if score #yaw tb matches 45..134 run scoreboard players set #rz tb -1

# 北向き（yaw 135..180 および -180..-135）
execute if score #yaw tb matches 135..180 run scoreboard players set #fx tb 0
execute if score #yaw tb matches 135..180 run scoreboard players set #fz tb -1
execute if score #yaw tb matches 135..180 run scoreboard players set #rx tb 1
execute if score #yaw tb matches 135..180 run scoreboard players set #rz tb 0
execute if score #yaw tb matches -180..-135 run scoreboard players set #fx tb 0
execute if score #yaw tb matches -180..-135 run scoreboard players set #fz tb -1
execute if score #yaw tb matches -180..-135 run scoreboard players set #rx tb 1
execute if score #yaw tb matches -180..-135 run scoreboard players set #rz tb 0

# 東向き（yaw -134..-45）
execute if score #yaw tb matches -134..-45 run scoreboard players set #fx tb 1
execute if score #yaw tb matches -134..-45 run scoreboard players set #fz tb 0
execute if score #yaw tb matches -134..-45 run scoreboard players set #rx tb 0
execute if score #yaw tb matches -134..-45 run scoreboard players set #rz tb 1

# プレイヤーの2ブロック前方を基準点にする
scoreboard players set #fwd tb 2

scoreboard players operation #baseX tb = #fx tb
scoreboard players operation #baseX tb *= #fwd tb
scoreboard players operation #baseX tb += #px tb

scoreboard players operation #baseZ tb = #fz tb
scoreboard players operation #baseZ tb *= #fwd tb
scoreboard players operation #baseZ tb += #pz tb

scoreboard players operation #baseY tb = #py tb
scoreboard players add #baseY tb 1

execute store result storage textblock:job state.fx int 1 run scoreboard players get #fx tb
execute store result storage textblock:job state.fz int 1 run scoreboard players get #fz tb
execute store result storage textblock:job state.rx int 1 run scoreboard players get #rx tb
execute store result storage textblock:job state.rz int 1 run scoreboard players get #rz tb
execute store result storage textblock:job state.baseX int 1 run scoreboard players get #baseX tb
execute store result storage textblock:job state.baseY int 1 run scoreboard players get #baseY tb
execute store result storage textblock:job state.baseZ int 1 run scoreboard players get #baseZ tb

function textblock:char_loop with storage textblock:job state
