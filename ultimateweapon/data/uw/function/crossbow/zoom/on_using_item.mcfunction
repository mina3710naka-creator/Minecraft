# ============================================================
#  minecraft:using_item アドバンスメントの報酬（実行者＝プレイヤー）
#  ズーム中フラグの維持にのみ使う。二度と発火しないよう即revoke
#  ※毎tick必ず発火するとは限らない可能性を考慮し、0/1の一発判定では
#    なく数tick分の猶予（バッファ）を持たせて「離した」誤判定を防ぐ
# ============================================================
advancement revoke @s only uw:using_item

execute if entity @s[tag=uw.aiming] run scoreboard players set @s uw.using 6
