# ============================================================
#  MOB に着弾（マクロ / 実行者＝刺さった MOB / 実行位置＝その MOB）
#  持ち主が使う「巻き取り」とは別に、MOB自身をNoAIにして
#  持ち主の方へ毎ティック引き寄せる
# ============================================================
$scoreboard players set @s sc.id $(id)
tag @s add sc.pulled
scoreboard players set @s sc.t 0

particle minecraft:crit ~ ~1 ~ 0.2 0.2 0.2 0.05 10 normal @a
playsound minecraft:entity.generic.hurt player @a ~ ~ ~ 0.5 0.8

data merge entity @s {NoAI:1b}

$execute as @a[tag=sc.hookflying,scores={sc.id=$(id)},limit=1] run tag @s remove sc.hookflying
$execute as @a[scores={sc.id=$(id)},limit=1] run tag @s add sc.pulling
