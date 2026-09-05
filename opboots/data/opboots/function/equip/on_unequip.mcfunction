# ============================================================
#  OPブーツを脱いだ瞬間（実行者＝プレイヤー）
# ============================================================
tag @s remove ob.worn
effect clear @s minecraft:resistance
effect clear @s minecraft:fire_resistance

# ジャンプ中に脱いだ場合も安全に後始末
execute if entity @s[tag=ob.jumping] run function opboots:jump/stop

scoreboard players set @s ob.charge 0
tag @s remove ob.dj_used
tellraw @s [{"text":"[OPブーツ] ","color":"gold"},{"text":"装備を解除しました","color":"gray"}]
