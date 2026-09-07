# ============================================================
#  テキストブロック - 建築完了処理
#  取り消し用にバウンディングボックスを記録してから通知する
# ============================================================

$scoreboard players set #cursorX tb $(cursorX)
scoreboard players remove #cursorX tb 1
execute if score #cursorX tb matches ..-1 run scoreboard players set #cursorX tb 0

$scoreboard players set #rx tb $(rx)
$scoreboard players set #rz tb $(rz)
$scoreboard players set #baseX tb $(baseX)
$scoreboard players set #baseY tb $(baseY)
$scoreboard players set #baseZ tb $(baseZ)
$scoreboard players set #scale tb $(scale)

scoreboard players operation #x2 tb = #rx tb
scoreboard players operation #x2 tb *= #cursorX tb
scoreboard players operation #x2 tb += #baseX tb

scoreboard players operation #z2 tb = #rz tb
scoreboard players operation #z2 tb *= #cursorX tb
scoreboard players operation #z2 tb += #baseZ tb

scoreboard players set #hgt tb 7
scoreboard players operation #hgt tb *= #scale tb
scoreboard players operation #y2 tb = #baseY tb
scoreboard players operation #y2 tb += #hgt tb
scoreboard players remove #y2 tb 1

data modify storage textblock:job last set value {}
execute store result storage textblock:job last.x1 int 1 run scoreboard players get #baseX tb
execute store result storage textblock:job last.y1 int 1 run scoreboard players get #baseY tb
execute store result storage textblock:job last.z1 int 1 run scoreboard players get #baseZ tb
execute store result storage textblock:job last.x2 int 1 run scoreboard players get #x2 tb
execute store result storage textblock:job last.y2 int 1 run scoreboard players get #y2 tb
execute store result storage textblock:job last.z2 int 1 run scoreboard players get #z2 tb

$tellraw @s [{"text":"[テキストブロック] 完了（","color":"green"},{"text":"$(count)","color":"yellow"},{"text":"文字）取り消すには /function textblock:undo","color":"green"}]
