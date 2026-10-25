# リード用アンカーの追従（実行者＝コウモリ）
execute store result storage hookshot:v id int 1 run scoreboard players get @s hs.id
function hookshot:rope/follow with storage hookshot:v
