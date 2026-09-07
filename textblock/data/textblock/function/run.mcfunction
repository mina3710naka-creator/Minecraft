# ============================================================
#  テキストブロック - 本体処理の入り口
# ============================================================

$data modify storage textblock:job state set value {text:"$(text)",block:"$(block)",scale:$(scale),index:0,next:1,cursorX:0,count:0}

execute unless data storage textblock:job state{text:""} run function textblock:check_length
execute if data storage textblock:job state{text:""} run tellraw @s [{"text":"[テキストブロック] 文字が指定されていません","color":"red"}]
