# 保留していたモード切替を確定する（実行者＝プレイヤー）
execute if entity @s[tag=sc.modepend] run function sc:mode/edge
tag @s remove sc.modepend
