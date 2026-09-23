# ============================================================
#  目標への実際のダメージ（マクロ / 実行者＝仲間 / 実行位置＝仲間）
#  15ティック（0.75秒）に1回だけ攻撃するクールダウン制
# ============================================================
execute unless score @s tw.cd matches 0 run scoreboard players remove @s tw.cd 1
execute unless score @s tw.cd matches 0 run return 0

$damage @e[tag=tw.target,scores={tw.tid=$(tid)},limit=1] 4 minecraft:generic
particle minecraft:crit ~ ~1 ~ 0.2 0.2 0.2 0 6 normal @a
playsound minecraft:entity.player.attack.strong player @a ~ ~ ~ 0.6 1.1
scoreboard players set @s tw.cd 15
