# ============================================================
#  OPブーツ / OP Boots  -  初期化
# ============================================================

# ジャンプ入力の検知（回数統計を流用）
scoreboard objectives add ob.jump minecraft.custom:minecraft.jump

# 内部用スコア
scoreboard objectives add ob.charge dummy
scoreboard objectives add ob.jt dummy
scoreboard objectives add ob.vv dummy
scoreboard objectives add ob.id dummy

# ID 採番カウンタ
scoreboard players set #ob_next ob.id 0

# 前回のワールド終了時に残っていたものを掃除
kill @e[type=marker,tag=ob.jumpdir]
effect clear @a[tag=ob.worn] minecraft:resistance
effect clear @a[tag=ob.worn] minecraft:fire_resistance
tag @a remove ob.worn
tag @a remove ob.jumping
tag @a remove ob.dj_used
scoreboard players set @a ob.charge 0
scoreboard players set @a ob.jt 0
scoreboard players set @a ob.vv 0
scoreboard players set @a ob.jump 0

tellraw @a [{"text":"[OPブーツ] ","color":"gold"},{"text":"読み込み完了 / ","color":"gray"},{"text":"/function opboots:give","color":"yellow"},{"text":" でアイテム入手","color":"gray"}]
