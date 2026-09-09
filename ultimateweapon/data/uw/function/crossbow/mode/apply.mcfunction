# ============================================================
#  モードを次へ進める（実行者＝プレイヤー）0→1→2→0
# ============================================================
scoreboard players add @s uw.mode 1
execute if score @s uw.mode matches 3.. run scoreboard players set @s uw.mode 0

playsound minecraft:block.note_block.hat player @s ~ ~ ~ 1 1.5

execute if score @s uw.mode matches 0 run title @s actionbar {"text":"モード1: マシンガン","color":"aqua","bold":true}
execute if score @s uw.mode matches 1 run title @s actionbar {"text":"モード2: 固定レーザー","color":"red","bold":true}
execute if score @s uw.mode matches 2 run title @s actionbar {"text":"モード3: ロックオン花火","color":"light_purple","bold":true}
