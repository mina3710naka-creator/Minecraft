# 当たり判定（Interaction）の追従・後始末・攻撃検知（実行者＝Interaction）
execute store result storage spiderweb:v pid int 1 run scoreboard players get @s sw.pid
function spiderweb:punch/follow_do with storage spiderweb:v
