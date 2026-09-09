# 中心に到達した存在への押しつぶしダメージ（実行者＝対象）
# ※ 被ダメージ無敵時間より長い間隔でのみダメージが通るようにする
scoreboard players set @s uw.cool 12
particle minecraft:crit ~ ~ ~ 0.3 0.3 0.3 0.1 8 normal @a
particle minecraft:portal ~ ~ ~ 0.2 0.2 0.2 0.3 10 normal @a
damage @s 4 minecraft:generic
