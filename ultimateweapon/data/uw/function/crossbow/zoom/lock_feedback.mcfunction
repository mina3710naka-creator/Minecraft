# ロックオン確定演出（実行者＝プレイヤー）
playsound minecraft:block.note_block.bell player @s ~ ~ ~ 1 1.6
execute at @e[tag=uw.locktgt,limit=1] run particle minecraft:end_rod ~ ~1 ~ 0.3 0.5 0.3 0.02 15 normal @a
execute at @e[tag=uw.locktgt,limit=1] run particle minecraft:flash ~ ~1 ~ 0 0 0 0 1 normal @a
