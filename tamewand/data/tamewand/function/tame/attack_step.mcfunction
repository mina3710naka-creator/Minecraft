# ============================================================
#  攻撃モード（実行者＝仲間 / 実行位置＝仲間）
# ============================================================
execute store result storage tamewand:v tid int 1 run scoreboard players get @s tw.aim
function tamewand:tame/attack_do with storage tamewand:v
