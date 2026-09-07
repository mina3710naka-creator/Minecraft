# 迷子になった演出用エンティティの掃除（20秒、実行者＝対象）
# ※ uw.t は各アビリティ自身の寿命カウンタとして使われているため、
#   ここでは専用の uw.age を使い、二重カウントを避ける
scoreboard players add @s uw.age 1
execute if score @s uw.age matches 400.. run kill @s
