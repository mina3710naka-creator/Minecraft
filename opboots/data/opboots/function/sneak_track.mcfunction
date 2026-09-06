# ============================================================
#  シフト（スニーク）保持時間のカウント（実行者＝装備しているプレイヤー）
# ============================================================

execute if predicate opboots:sneaking run scoreboard players add @s ob.sneak 1
execute unless predicate opboots:sneaking run scoreboard players set @s ob.sneak 0
