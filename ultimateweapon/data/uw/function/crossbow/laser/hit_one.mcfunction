# ビームの命中処理（実行者＝命中したMOB、1tickにつき1体1回だけ）
tag @s add uw.laserhit
particle minecraft:flash ~ ~1 ~ 0 0 0 0 1 normal @a
damage @s 6 minecraft:generic
