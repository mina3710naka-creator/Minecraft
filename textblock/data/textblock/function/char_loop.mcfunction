# ============================================================
#  テキストブロック - 1文字ずつ取り出すループ
#  index 番目の文字が取り出せなければ文字列の終端＝完了
# ============================================================

$execute store success score #ok tb run data modify storage textblock:job state.char set string storage textblock:job state.text $(index) $(next)

execute if score #ok tb matches 1 run function textblock:char_found with storage textblock:job state
execute if score #ok tb matches 0 run function textblock:finish with storage textblock:job state
