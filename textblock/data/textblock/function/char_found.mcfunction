# ============================================================
#  テキストブロック - 1文字分を描画してカーソルを進める
# ============================================================

$data modify storage textblock:job draw set value {char:"$(char)",block:"$(block)",scale:$(scale),cursorX:$(cursorX),fx:$(fx),fz:$(fz),rx:$(rx),rz:$(rz),baseX:$(baseX),baseY:$(baseY),baseZ:$(baseZ)}

function textblock:draw_glyph with storage textblock:job draw

# カーソルを1文字分（3マス＋隙間1マス）×scale だけ進める
execute store result score #cx tb run data get storage textblock:job state.cursorX 1
execute store result score #sc tb run data get storage textblock:job state.scale 1
scoreboard players set #step tb 4
scoreboard players operation #step tb *= #sc tb
scoreboard players operation #cx tb += #step tb
execute store result storage textblock:job state.cursorX int 1 run scoreboard players get #cx tb

execute store result score #idx tb run data get storage textblock:job state.index 1
scoreboard players add #idx tb 1
execute store result storage textblock:job state.index int 1 run scoreboard players get #idx tb

execute store result score #nxt tb run data get storage textblock:job state.next 1
scoreboard players add #nxt tb 1
execute store result storage textblock:job state.next int 1 run scoreboard players get #nxt tb

execute store result score #cnt tb run data get storage textblock:job state.count 1
scoreboard players add #cnt tb 1
execute store result storage textblock:job state.count int 1 run scoreboard players get #cnt tb

function textblock:char_loop with storage textblock:job state
