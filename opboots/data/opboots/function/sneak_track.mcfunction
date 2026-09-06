# ============================================================
#  シフト（スニーク）保持時間のカウント（実行者＝装備しているプレイヤー）
#  溜まり具合が目に見えないと「本当に3秒経っているか」が分から
#  ないため、アクションバーに進捗を表示するデバッグ表示を兼ねている
# ============================================================

execute if predicate opboots:sneaking run scoreboard players add @s ob.sneak 1
execute unless predicate opboots:sneaking run scoreboard players set @s ob.sneak 0

execute if score @s ob.sneak matches 1..59 run title @s actionbar [{"text":"溜め中... ","color":"yellow"},{"score":{"name":"@s","objective":"ob.sneak"}},{"text":" / 60"}]
execute if score @s ob.sneak matches 60.. run title @s actionbar {"text":"爆発ジャンプ 準備完了！（ジャンプ）","color":"gold","bold":true}
