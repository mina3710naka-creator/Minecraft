# ============================================================
#  フックショット / Hookshot  -  初期化
# ============================================================

# 釣り竿を使った回数（使用検知に利用）
scoreboard objectives add hs.use minecraft.used:minecraft.fishing_rod

# 内部用スコア
scoreboard objectives add hs.id dummy
scoreboard objectives add hs.t dummy
scoreboard objectives add hs.pt dummy
scoreboard objectives add hs.spd dummy
scoreboard objectives add hs.range dummy
scoreboard objectives add hs.sub dummy
# 到達後のジャンプ演出で使う、上昇量の作業スコア
scoreboard objectives add hs.jh dummy

# ID 採番カウンタ
scoreboard players add #next hs.id 0

# 前回のワールド終了時に残っていたものを掃除
kill @e[tag=hs.ent]
kill @e[tag=hs.sweep]
tag @a remove hs.active
tag @a remove hs.hooking
tag @a remove hs.pulling
tag @a remove hs.self
tag @a remove hs.caster
scoreboard players set @a hs.use 0

tellraw @a [{"text":"[フックショット] ","color":"aqua"},{"text":"読み込み完了 / ","color":"gray"},{"text":"/function hookshot:give","color":"yellow"},{"text":" でアイテム入手","color":"gray"}]
