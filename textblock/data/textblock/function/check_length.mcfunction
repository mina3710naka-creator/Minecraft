# ============================================================
#  テキストブロック - 文字数チェック（21文字目が存在するか probe）
#  20文字を超える場合は建築を始めずにエラーを出す
#
#  data modify の成功判定は「値が実際に変化したか」で決まるため、
#  前回の建築で probe に残った値と偶然同じ文字だと成功判定が 0 に
#  なってしまう。判定の直前に一度 probe を消してから確実に成功判定させる。
# ============================================================

data remove storage textblock:job probe
execute store success score #toolong tb run data modify storage textblock:job probe set string storage textblock:job state.text 20 21

execute if score #toolong tb matches 1 run tellraw @s [{"text":"[テキストブロック] 文字数が多すぎます（20文字まで）","color":"red"}]
execute if score #toolong tb matches 0 run function textblock:facing
