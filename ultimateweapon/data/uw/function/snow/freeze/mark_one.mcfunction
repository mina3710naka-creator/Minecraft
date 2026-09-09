# 本物の雪玉にタグを付け、追跡用マーカーを従わせる（実行者＝その雪玉）
tag @s add uw.sbtagged
tag @s add uw.sb_freeze
execute summon minecraft:marker run function uw:snow/freeze/tracker_init
