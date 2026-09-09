# ============================================================
#  最強の武器 / Ultimate Weapon  -  初期化
# ============================================================

scoreboard objectives add uw.snowuse minecraft.used:minecraft.snowball
scoreboard objectives add uw.mode dummy
scoreboard objectives add uw.sneakp dummy
scoreboard objectives add uw.id dummy
scoreboard objectives add uw.t dummy
scoreboard objectives add uw.atk dummy
scoreboard objectives add uw.inter dummy
scoreboard objectives add uw.pitch dummy
scoreboard objectives add uw.using dummy
scoreboard objectives add uw.sub dummy
scoreboard objectives add uw.age dummy
scoreboard objectives add uw.natk dummy
scoreboard objectives add uw.ninter dummy
scoreboard objectives add uw.cool dummy
scoreboard objectives add uw.mgloaded dummy
scoreboard objectives add uw.mgloadedo dummy

scoreboard players add #next uw.id 0

# 前回のワールド終了時の残骸を掃除
kill @e[tag=uw.ent]
kill @e[tag=uw.sword_interact]
tag @a remove uw.has_interact
tag @a remove uw.aiming
tag @a remove uw.zoomactive
tag @a remove uw.hadlock
tag @a remove uw.laser
tag @e remove uw.locktgt
tag @e remove uw.frozen

scoreboard players add @a uw.mode 0
scoreboard players add @a uw.sneakp 0
scoreboard players set @a uw.using 0
scoreboard players set @a uw.snowuse 0
scoreboard players set @a uw.mgloaded 0
scoreboard players set @a uw.mgloadedo 0

tellraw @a [{"text":"[最強の武器] ","color":"gold"},{"text":"読み込み完了 / ","color":"gray"},{"text":"/function uw:give","color":"yellow"},{"text":" でアイテム入手","color":"gray"}]
tellraw @a [{"text":"[最強の武器] ","color":"gold"},{"text":"操作方法は ","color":"gray"},{"text":"/function uw:help","color":"yellow"},{"text":" で確認できます","color":"gray"}]
