# ============================================================
#  攻撃命令の対象に目印を付ける
#  （マクロ / 実行者＝命令された相手 / 実行位置＝その相手）
# ============================================================
tag @s add tw.target
tag @s add tw.ent
$scoreboard players set @s tw.tid $(tid)
scoreboard players set @s tw.ttl 600

particle minecraft:angry_villager ~ ~1 ~ 0.2 0.3 0.2 0 6 force @a
