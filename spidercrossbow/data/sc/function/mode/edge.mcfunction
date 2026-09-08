# ============================================================
#  モードを次へ進める（実行者＝プレイヤー）0→1→0
#  Shiftを押して保留期間が確定した時に呼ばれる（mode/pend_commit.mcfunction）
# ============================================================
scoreboard players add @s sc.mode 1
execute if score @s sc.mode matches 2.. run scoreboard players set @s sc.mode 0

playsound minecraft:block.note_block.hat player @s ~ ~ ~ 1 1.5

execute if score @s sc.mode matches 0 run title @s actionbar {"text":"モード1: フックショット","color":"aqua","bold":true}
execute if score @s sc.mode matches 1 run title @s actionbar {"text":"モード2: 巣づくり(糸を消費)","color":"white","bold":true}
