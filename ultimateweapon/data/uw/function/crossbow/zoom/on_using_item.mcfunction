# ============================================================
#  minecraft:using_item アドバンスメントの報酬（実行者＝プレイヤー）
#  ズーム中フラグの維持にのみ使う。二度と発火しないよう即revoke
# ============================================================
advancement revoke @s only uw:using_item

execute if entity @s[tag=uw.aiming] run scoreboard players set @s uw.using 1
