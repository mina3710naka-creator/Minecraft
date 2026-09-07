# 対応する当たり判定（Interaction）を片付ける（実行者＝プレイヤー）
execute unless score @s sw.pid matches 1.. run return 0
execute store result storage spiderweb:v pid int 1 run scoreboard players get @s sw.pid
function spiderweb:punch/remove_do with storage spiderweb:v
