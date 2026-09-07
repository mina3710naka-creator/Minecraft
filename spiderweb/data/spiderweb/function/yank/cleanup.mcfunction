# 対応するプレイヤーがいなくなった場合などのタグ後始末（実行者＝MOB）
# 本物のMOBなのでkillはせず、このデータパックが付けたものだけを外す
tag @s remove sw.yankmob
tag @s remove sw.tip
scoreboard players reset @s sw.id
scoreboard players reset @s sw.pt
scoreboard players reset @s sw.spd
