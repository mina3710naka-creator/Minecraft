# ビームの命中処理（実行者＝命中したMOB、1tickにつき1体1回だけ選ばれる）
tag @s add uw.laserhit
particle minecraft:flash ~ ~1 ~ 0 0 0 0 1 normal @a
scoreboard players add @s uw.cool 0
execute if score @s uw.cool matches ..0 run function uw:crossbow/laser/damage_one
