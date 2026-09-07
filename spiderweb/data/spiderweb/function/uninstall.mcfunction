# ============================================================
#  アンインストール
# ============================================================
kill @e[tag=sw.ent]
kill @e[tag=sw.sweep]
kill @e[tag=sw.puncher]
tag @e[tag=sw.yankmob] remove sw.tip
tag @e[tag=sw.yankmob] remove sw.yankmob

tag @a remove sw.active
tag @a remove sw.hooking
tag @a remove sw.webbing
tag @a remove sw.pulling
tag @a remove sw.attached
tag @a remove sw.yanking
tag @a remove sw.launching
tag @a remove sw.self
tag @a remove sw.caster

scoreboard objectives remove sw.use
scoreboard objectives remove sw.rkey
scoreboard objectives remove sw.jump
scoreboard objectives remove sw.id
scoreboard objectives remove sw.t
scoreboard objectives remove sw.pt
scoreboard objectives remove sw.spd
scoreboard objectives remove sw.range
scoreboard objectives remove sw.sub
scoreboard objectives remove sw.lt
scoreboard objectives remove sw.len
scoreboard objectives remove sw.mode
scoreboard objectives remove sw.sneakprev
scoreboard objectives remove sw.pid
scoreboard objectives remove sw.wc

tellraw @a {"text":"[ウェブシューター] アンインストールしました","color":"gray"}
