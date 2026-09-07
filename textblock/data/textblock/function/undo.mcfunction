# ============================================================
#  テキストブロック - 直前に建築した範囲を取り消す
# ============================================================

execute unless data storage textblock:job last run tellraw @s [{"text":"[テキストブロック] 取り消せる建築がありません","color":"red"}]
execute if data storage textblock:job last run function textblock:undo_do with storage textblock:job last
