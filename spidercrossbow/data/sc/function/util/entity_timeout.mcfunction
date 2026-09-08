# 迷子になった演出用エンティティの掃除（12秒、実行者＝対象）
# sc.anchor / sc.carrier / sc.pulled は専用のライフサイクル管理（release系）を
# 持つため、ここでの一律タイムアウトからは除外する
scoreboard players add @s sc.age 1
execute if score @s sc.age matches 240.. unless entity @s[tag=sc.anchor] unless entity @s[tag=sc.carrier] unless entity @s[tag=sc.pulled] run function sc:util/quiet_kill
