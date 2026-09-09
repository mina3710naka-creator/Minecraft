# ビームの命中処理（実行者＝命中したMOB、1tickにつき1体1回だけ選ばれる）
tag @s add uw.laserhit
particle minecraft:flash ~ ~1 ~ 0 0 0 0 1 normal @a
execute unless score @s uw.cool matches 1.. run function uw:crossbow/laser/damage_one
