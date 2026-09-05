# ============================================================
#  OPブーツを装備した瞬間（実行者＝プレイヤー）
# ============================================================
tag @s add ob.worn
effect give @s minecraft:resistance infinite 4 true
effect give @s minecraft:fire_resistance infinite 0 true
tellraw @s [{"text":"[OPブーツ] ","color":"gold"},{"text":"装備しました - 無敵状態","color":"aqua"}]
