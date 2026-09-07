# 弾の飛行（実行者＝マーカー / 実行位置＝マーカー）1tickに3ブロック
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 40.. run function uw:util/quiet_kill
execute unless entity @s run return 0

scoreboard players set @s uw.sub 3
function uw:snow/freeze/proj_step
