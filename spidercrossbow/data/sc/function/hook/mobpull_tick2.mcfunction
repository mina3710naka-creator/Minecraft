# ============================================================
#  MOB引き寄せ（マクロ / 実行者＝引き寄せ中のMOB）
#  持ち主の方を向いて毎ティック0.4ブロック前進する。
#  十分近づいたか、持ち主がいなくなったか、5秒(100tick)経ったら終了
# ============================================================
$execute unless entity @a[tag=sc.pulling,scores={sc.id=$(id)}] run return run function sc:hook/mobpull_stop

$execute if entity @a[tag=sc.pulling,scores={sc.id=$(id)},distance=..1.8] run return run function sc:hook/mobpull_stop
execute if score @s sc.t matches 100.. run return run function sc:hook/mobpull_stop

$execute facing entity @a[tag=sc.pulling,scores={sc.id=$(id)},limit=1] feet run tp @s ~ ~ ~ ~ ~
tp @s ^ ^ ^0.4
particle minecraft:crit ~ ~0.3 ~ 0.05 0.05 0.05 0.01 2 normal @a
