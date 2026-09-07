# リード用コウモリの撤去（実行者＝コウモリ）
summon minecraft:marker ~ ~ ~ {Tags:["sw.sweep"]}
tag @s remove sw.rope
tag @s remove sw.ent
function spiderweb:util/quiet_kill
