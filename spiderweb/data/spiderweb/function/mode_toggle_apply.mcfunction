# ============================================================
#  モードの実際の切り替え（実行者＝プレイヤー）
# ============================================================
execute if score @s sw.mode matches 0 run scoreboard players set @s sw.mode 1
execute unless score @s sw.mode matches 0 run scoreboard players set @s sw.mode 0

playsound minecraft:block.note_block.hat player @s ~ ~ ~ 1 1.6

execute if score @s sw.mode matches 0 run tellraw @s [{"text":"[ウェブシューター] ","color":"aqua"},{"text":"モード1: フックショット（移動）","color":"green"}]
execute if score @s sw.mode matches 1 run tellraw @s [{"text":"[ウェブシューター] ","color":"aqua"},{"text":"モード2: 巣づくり（3x3x3トラップ）","color":"green"}]
