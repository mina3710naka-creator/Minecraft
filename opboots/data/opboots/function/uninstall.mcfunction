# ============================================================
#  アンインストール
# ============================================================
kill @e[type=marker,tag=ob.jumpdir]
effect clear @a[tag=ob.worn] minecraft:resistance
effect clear @a[tag=ob.worn] minecraft:fire_resistance
tag @a remove ob.worn
tag @a remove ob.jumping
tag @a remove ob.dj_used
scoreboard objectives remove ob.charge
scoreboard objectives remove ob.jt
scoreboard objectives remove ob.vv
scoreboard objectives remove ob.id
scoreboard objectives remove ob.jump
tellraw @a {"text":"[OPブーツ] アンインストールしました","color":"gray"}
