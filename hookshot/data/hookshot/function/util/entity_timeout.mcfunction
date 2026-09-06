# 迷子になったフック・マーカー・ロープ・台車の掃除（12 秒）
scoreboard players add @s hs.t 1
execute if score @s hs.t matches 240.. if entity @s[tag=hs.rope] at @s run function hookshot:util/drop_rope
execute if score @s hs.t matches 240.. if entity @s[tag=hs.carrier] at @s run function hookshot:util/quiet_kill
execute if score @s hs.t matches 240.. unless entity @s[tag=hs.rope] unless entity @s[tag=hs.carrier] run kill @s
