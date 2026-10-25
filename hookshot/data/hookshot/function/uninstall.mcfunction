# ============================================================
#  アンインストール
# ============================================================
kill @e[tag=hs.ent]
kill @e[tag=hs.sweep]
tag @a remove hs.active
tag @a remove hs.hooking
tag @a remove hs.pulling
tag @a remove hs.self
tag @a remove hs.caster
scoreboard objectives remove hs.use
scoreboard objectives remove hs.id
scoreboard objectives remove hs.t
scoreboard objectives remove hs.pt
scoreboard objectives remove hs.spd
scoreboard objectives remove hs.range
scoreboard objectives remove hs.sub
tellraw @a {"text":"[フックショット] アンインストールしました","color":"gray"}
