# ============================================================
#  しゃがみ長押し検知（実行者＝プレイヤー / 装備中のみ呼び出される）
#  60 tick（3秒）しゃがみ続けると自動で爆発ジャンプを発射する。
#  3秒たまる前にジャンプ入力があった場合は、爆発なしの通常10マスジャンプになる。
# ============================================================
execute unless entity @s[tag=ob.jumping] if entity @s[nbt={Pose:"SNEAKING"}] run scoreboard players add @s ob.charge 1
execute unless entity @s[nbt={Pose:"SNEAKING"}] if score @s ob.charge matches 1.. run scoreboard players set @s ob.charge 0

# 溜め演出（パーティクル＋段階的な音の上昇）
execute if score @s ob.charge matches 1..59 run particle minecraft:crit ~ ~0.1 ~ 0.25 0.05 0.25 0 2 force @s
execute if score @s ob.charge matches 1 run playsound minecraft:block.note_block.hat master @s ~ ~ ~ 1 1
execute if score @s ob.charge matches 20 run playsound minecraft:block.note_block.hat master @s ~ ~ ~ 1 1.5
execute if score @s ob.charge matches 40 run playsound minecraft:block.note_block.hat master @s ~ ~ ~ 1 2

# 3秒たまる前にジャンプ入力があれば、爆発なしの通常ジャンプ
execute unless entity @s[tag=ob.jumping] if score @s ob.charge matches 1..59 if score @s ob.jump matches 1.. run function opboots:jump/start_plain

# 3秒（60 tick）溜まったら自動で爆発ジャンプ
execute unless entity @s[tag=ob.jumping] if score @s ob.charge matches 60.. run function opboots:jump/start
