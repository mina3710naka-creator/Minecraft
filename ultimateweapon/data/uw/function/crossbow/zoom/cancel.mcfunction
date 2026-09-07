# キャンセル（実行者＝プレイヤー）
execute as @e[tag=uw.locktgt] run effect clear @s minecraft:glowing
tag @e[tag=uw.locktgt] remove uw.locktgt
title @s actionbar {"text":"キャンセルしました","color":"gray"}
