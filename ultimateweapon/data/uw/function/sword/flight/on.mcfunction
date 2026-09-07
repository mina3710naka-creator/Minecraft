# 飛行モードON（実行者＝プレイヤー）
tag @s add uw.flying
effect give @s minecraft:slow_falling 30 0 true
playsound minecraft:item.elytra.flying player @s ~ ~ ~ 1 1
title @s actionbar {"text":"飛行モード ON","color":"aqua"}
