# 何にも当たらず寿命切れ（実行者＝マーカー）
execute as @e[tag=uw.locktgt] run effect clear @s minecraft:glowing
tag @e[tag=uw.locktgt] remove uw.locktgt
function uw:util/quiet_kill
