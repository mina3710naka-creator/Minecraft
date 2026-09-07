# ============================================================
#  射程外（実行者＝防具立て）
# ============================================================
particle minecraft:smoke ~ ~ ~ 0.1 0.1 0.1 0.01 8 normal @a
playsound minecraft:entity.item.break player @a ~ ~ ~ 0.5 1.4
execute store result storage spiderweb:v id int 1 run scoreboard players get @s sw.id
function spiderweb:hook/miss_release with storage spiderweb:v
execute if entity @s run kill @s
