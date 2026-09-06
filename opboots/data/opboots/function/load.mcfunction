# ============================================================
#  最強のブーツ (OP Boots) - 初期化
# ============================================================

scoreboard objectives add ob.hp health
scoreboard objectives add ob.sneak dummy
scoreboard objectives add ob.jump minecraft.custom:minecraft.jump
scoreboard objectives add ob.leapt dummy

scoreboard players set @a ob.jump 0
scoreboard players set @a ob.sneak 0

# 前回のワールド終了時に残っていたものを掃除
tag @a remove ob.wearing
tag @a remove ob.leaping
tag @a remove ob.grounded

tellraw @a [{"text":"[最強のブーツ] ","color":"gold"},{"text":"読み込み完了 / ","color":"gray"},{"text":"/function opboots:give","color":"yellow"},{"text":" でアイテム入手","color":"gray"}]
