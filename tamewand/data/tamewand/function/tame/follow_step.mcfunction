# ============================================================
#  追従モード（実行者＝仲間 / 実行位置＝仲間）
# ============================================================
execute store result storage tamewand:v pid int 1 run scoreboard players get @s tw.owner
function tamewand:tame/follow_do with storage tamewand:v
