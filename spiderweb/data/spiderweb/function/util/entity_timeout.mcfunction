# 迷子になったフック・巣づくり弾・マーカー・ロープ・台車の掃除（12秒）
scoreboard players add @s sw.t 1
execute if score @s sw.t matches 240.. if entity @s[tag=sw.rope] at @s run function spiderweb:util/drop_rope
execute if score @s sw.t matches 240.. if entity @s[tag=sw.carrier] at @s run function spiderweb:util/quiet_kill
execute if score @s sw.t matches 240.. unless entity @s[tag=sw.rope] unless entity @s[tag=sw.carrier] run kill @s
