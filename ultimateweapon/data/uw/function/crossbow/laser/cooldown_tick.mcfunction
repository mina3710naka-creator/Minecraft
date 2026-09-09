# レーザー再命中クールダウンの減少（実行者＝命中済みの対象）
scoreboard players remove @s uw.cool 1
execute if score @s uw.cool matches ..0 run tag @s remove uw.laserhit
