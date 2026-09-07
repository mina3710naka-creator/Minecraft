# ============================================================
#  引き寄せ中MOBの毎ティック処理（実行者＝MOB / 実行位置＝MOB）
# ============================================================
scoreboard players add @s sw.pt 1
execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
function spiderweb:yank/step with storage spiderweb:v
