# ============================================================
#  テキストブロック - 文字数チェック（21文字目が存在するか probe）
#  20文字を超える場合は建築を始めずにエラーを出す
# ============================================================

execute store success score #toolong tb run data modify storage textblock:job probe set string storage textblock:job state.text 20 21

execute if score #toolong tb matches 1 run tellraw @s [{"text":"[テキストブロック] 文字数が多すぎます（20文字まで）","color":"red"}]
execute if score #toolong tb matches 0 run function textblock:facing
