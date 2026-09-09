# ============================================================
#  アンインストール
# ============================================================

# 引き寄せ中のMOBがいればAIを戻す
execute as @e[tag=sc.pulled] run data merge entity @s {NoAI:0b}

kill @e[tag=sc.ent]
kill @e[type=minecraft:arrow,tag=sc.seen]
kill @e[type=minecraft:snowball,tag=sc.seen]
tag @a remove sc.hookflying
tag @a remove sc.webflying
tag @a remove sc.hooked
tag @a remove sc.launching
tag @a remove sc.pulling
tag @e remove sc.pulled

scoreboard objectives remove sc.mode
scoreboard objectives remove sc.sneakp
scoreboard objectives remove sc.mh
scoreboard objectives remove sc.oh
scoreboard objectives remove sc.nmh
scoreboard objectives remove sc.noh
scoreboard objectives remove sc.swap
scoreboard objectives remove sc.jump
scoreboard objectives remove sc.id
scoreboard objectives remove sc.t
scoreboard objectives remove sc.age
scoreboard objectives remove sc.sub
scoreboard objectives remove sc.range
scoreboard objectives remove sc.rope
scoreboard objectives remove sc.theta
scoreboard objectives remove sc.phi
scoreboard objectives remove sc.hookt
scoreboard objectives remove sc.py
scoreboard objectives remove sc.pp
scoreboard objectives remove sc.d
scoreboard objectives remove sc.ballc

tellraw @a {"text":"[スパイダークロスボウ] アンインストールしました","color":"gray"}
