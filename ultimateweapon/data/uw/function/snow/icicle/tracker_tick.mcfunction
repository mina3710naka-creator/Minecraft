# 雪玉に追従し、消えた瞬間（＝着弾した瞬間）にその最後の位置で発動する
scoreboard players add @s uw.t 1
execute if score @s uw.t matches 200.. run function uw:util/quiet_kill
execute unless entity @s run return 0

execute if entity @e[tag=uw.sb_icicle,distance=..4] at @e[tag=uw.sb_icicle,limit=1,sort=nearest] run tp @s ~ ~ ~
execute unless entity @e[tag=uw.sb_icicle,distance=..4] run function uw:snow/icicle/tracker_land
