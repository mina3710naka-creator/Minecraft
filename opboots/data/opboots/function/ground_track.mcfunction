# ============================================================
#  接地判定（実行者＝装備しているプレイヤー）
#  空中ジャンプは「地面に足をつけてから1回まで」なので、
#  接地したタイミングで空中ジャンプの使用済みフラグをリセットする
# ============================================================

execute unless block ~ ~-1 ~ #minecraft:replaceable run tag @s add ob.grounded
execute if block ~ ~-1 ~ #minecraft:replaceable run tag @s remove ob.grounded

execute if entity @s[tag=ob.grounded] run tag @s remove ob.airjumped
