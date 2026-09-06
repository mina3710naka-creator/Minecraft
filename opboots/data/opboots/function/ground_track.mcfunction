# ============================================================
#  接地判定（実行者＝装備しているプレイヤー）
#  空中にいるかどうかを ob.grounded タグで管理する
#  （空中にいる間だけ自由に動けるようにするため）
# ============================================================

execute unless block ~ ~-1 ~ #minecraft:replaceable run tag @s add ob.grounded
execute if block ~ ~-1 ~ #minecraft:replaceable run tag @s remove ob.grounded
