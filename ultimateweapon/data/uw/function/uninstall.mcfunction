# ============================================================
#  アンインストール
# ============================================================
kill @e[tag=uw.ent]
kill @e[tag=uw.sword_interact]
tag @a remove uw.has_interact
tag @a remove uw.aiming
tag @a remove uw.laser
tag @e remove uw.locktgt

# 凍結中のMOBを解放してから削除する
execute as @e[tag=uw.frozen] run data merge entity @s {NoAI:0b}
tag @e remove uw.frozen

scoreboard objectives remove uw.snowuse
scoreboard objectives remove uw.mode
scoreboard objectives remove uw.sneakp
scoreboard objectives remove uw.id
scoreboard objectives remove uw.t
scoreboard objectives remove uw.atk
scoreboard objectives remove uw.inter
scoreboard objectives remove uw.pitch
scoreboard objectives remove uw.using
scoreboard objectives remove uw.sub
scoreboard objectives remove uw.age
scoreboard objectives remove uw.natk
scoreboard objectives remove uw.ninter
scoreboard objectives remove uw.cool
scoreboard objectives remove uw.mgloaded
scoreboard objectives remove uw.mgloadedo

tellraw @a {"text":"[最強の武器] アンインストールしました","color":"gray"}
