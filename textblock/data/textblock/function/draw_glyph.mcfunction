# ============================================================
#  テキストブロック - 文字に対応するピクセルパターンを検索
#  未対応の文字（フォント未定義）はそのまま半角の空白として扱う
# ============================================================

data modify storage textblock:job draw.pixels set value []
data modify storage textblock:job draw.w set value 4

$execute if data storage textblock:font glyphs[{c:"$(char)"}] run data modify storage textblock:job draw.pixels set from storage textblock:font glyphs[{c:"$(char)"}].px
$execute if data storage textblock:font glyphs[{c:"$(char)"}] run data modify storage textblock:job draw.w set from storage textblock:font glyphs[{c:"$(char)"}].w

execute if data storage textblock:job draw.pixels[0] run function textblock:draw_pixel with storage textblock:job draw
