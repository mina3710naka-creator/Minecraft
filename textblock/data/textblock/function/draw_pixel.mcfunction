# ============================================================
#  テキストブロック - ピクセル1つ分をブロックで塗りつぶす
#  （scale が2以上のときは scale×scale の正方形になる）
#  pixels リストの先頭を処理してから取り除き、残りがあれば再帰
# ============================================================

execute store result score #ppx tb run data get storage textblock:job draw.pixels[0].x 1
execute store result score #ppy tb run data get storage textblock:job draw.pixels[0].y 1

$scoreboard players set #scale tb $(scale)
$scoreboard players set #cursorX tb $(cursorX)
$scoreboard players set #rx tb $(rx)
$scoreboard players set #rz tb $(rz)
$scoreboard players set #baseX tb $(baseX)
$scoreboard players set #baseY tb $(baseY)
$scoreboard players set #baseZ tb $(baseZ)

# right = cursorX + px.x * scale
scoreboard players operation #right tb = #ppx tb
scoreboard players operation #right tb *= #scale tb
scoreboard players operation #right tb += #cursorX tb

# x1,z1 = base + right方向ベクトル * right
scoreboard players operation #x1 tb = #rx tb
scoreboard players operation #x1 tb *= #right tb
scoreboard players operation #x1 tb += #baseX tb

scoreboard players operation #z1 tb = #rz tb
scoreboard players operation #z1 tb *= #right tb
scoreboard players operation #z1 tb += #baseZ tb

# y1 = base + (4 - px.y) * scale （y:0行＝文字の上端が高い位置になる）
scoreboard players set #rowoff tb 4
scoreboard players operation #rowoff tb -= #ppy tb
scoreboard players operation #rowoff tb *= #scale tb
scoreboard players operation #y1 tb = #baseY tb
scoreboard players operation #y1 tb += #rowoff tb

# 反対側の角（scale分だけ right方向・上方向に伸ばす）
scoreboard players operation #ext tb = #scale tb
scoreboard players remove #ext tb 1

scoreboard players operation #x2 tb = #rx tb
scoreboard players operation #x2 tb *= #ext tb
scoreboard players operation #x2 tb += #x1 tb

scoreboard players operation #z2 tb = #rz tb
scoreboard players operation #z2 tb *= #ext tb
scoreboard players operation #z2 tb += #z1 tb

scoreboard players operation #y2 tb = #ext tb
scoreboard players operation #y2 tb += #y1 tb

$data modify storage textblock:job place set value {block:"$(block)"}
execute store result storage textblock:job place.x1 int 1 run scoreboard players get #x1 tb
execute store result storage textblock:job place.y1 int 1 run scoreboard players get #y1 tb
execute store result storage textblock:job place.z1 int 1 run scoreboard players get #z1 tb
execute store result storage textblock:job place.x2 int 1 run scoreboard players get #x2 tb
execute store result storage textblock:job place.y2 int 1 run scoreboard players get #y2 tb
execute store result storage textblock:job place.z2 int 1 run scoreboard players get #z2 tb

function textblock:place with storage textblock:job place

data remove storage textblock:job draw.pixels[0]
execute if data storage textblock:job draw.pixels[0] run function textblock:draw_pixel with storage textblock:job draw
