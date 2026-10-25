# ============================================================
#  テキストブロック - 1文字ずつ取り出すループ
#  index 番目の文字が取り出せなければ文字列の終端＝完了
#
#  data modify の成功判定は「値が実際に変化したか」で決まるため、
#  前回と同じ文字が連続すると成功判定が 0 になってしまう。
#  それを防ぐため、判定の直前に一度 state.char を消してから
#  「存在しない→存在する」への変化として確実に成功判定させる。
# ============================================================

data remove storage textblock:job state.char
$execute store success score #ok tb run data modify storage textblock:job state.char set string storage textblock:job state.text $(index) $(next)

execute if score #ok tb matches 1 run function textblock:char_found with storage textblock:job state
execute if score #ok tb matches 0 run function textblock:finish with storage textblock:job state
