# 迷子になったフック・マーカー・ロープの掃除（12 秒）
scoreboard players add @s hs.t 1
execute if score @s hs.t matches 240.. if entity @s[tag=hs.rope] at @s run function hookshot:util/drop_rope
execute if score @s hs.t matches 240.. unless entity @s[tag=hs.rope] run kill @s
