# ============================================================
#  テキストブロック - 初期化
# ============================================================

scoreboard objectives add tb dummy

function textblock:font/init

tellraw @a [{"text":"[テキストブロック] ","color":"aqua"},{"text":"読み込み完了 / ","color":"gray"},{"text":"/function textblock:build {text:\"HI\",block:\"minecraft:white_concrete\"}","color":"yellow"},{"text":" で建築","color":"gray"}]
